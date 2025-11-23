package com.minyook.overnight.ui.file

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.data.model.AnalysisResultData
import com.minyook.overnight.data.model.StatusResponse
import com.minyook.overnight.data.network.RetrofitClient
import com.minyook.overnight.databinding.ActivityAnalysisProgressBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AnalysisProgressActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisProgressBinding
    private var jobId: String? = null
    private var contentId: String? = null
    private var topicId: String? = null

    private lateinit var db: FirebaseFirestore

    // 1초마다 서버 상태를 체크하기 위한 핸들러
    private val handler = Handler(Looper.getMainLooper())
    private val pollingRunnable = object : Runnable {
        override fun run() {
            checkAnalysisStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()

        // 이전 화면(UploadActivity)에서 전달받은 ID들
        jobId = intent.getStringExtra("jobId")
        contentId = intent.getStringExtra("contentId")
        topicId = intent.getStringExtra("topicId")

        if (jobId != null) {
            // 1초 뒤부터 폴링 시작
            handler.postDelayed(pollingRunnable, 1000)
        } else {
            Toast.makeText(this, "작업 ID가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // 서버에 현재 분석 상태를 물어보는 함수
    private fun checkAnalysisStatus() {
        if (jobId == null) return

        RetrofitClient.instance.checkStatus(jobId!!)
            .enqueue(object : Callback<StatusResponse> {
                override fun onResponse(
                    call: Call<StatusResponse>,
                    response: Response<StatusResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val statusData = response.body()!!

                        // 화면에 현재 진행 메시지 표시 (예: "오디오 추출 중...")
                        updateProgressUI(statusData)

                        when (statusData.status) {
                            "Complete" -> {
                                // 분석 완료 -> 결과 데이터 저장 시작
                                saveAnalysisResultToFirestore(statusData.result)
                            }
                            "Error" -> {
                                // 서버 에러 발생
                                showError(statusData.message ?: "알 수 없는 오류")
                            }
                            else -> {
                                // 아직 진행 중 -> 1초 뒤에 다시 물어봄
                                handler.postDelayed(pollingRunnable, 1000)
                            }
                        }
                    } else {
                        // 네트워크는 연결됐으나 서버가 응답을 거부한 경우 (잠시 후 재시도)
                        handler.postDelayed(pollingRunnable, 2000)
                    }
                }

                override fun onFailure(call: Call<StatusResponse>, t: Throwable) {
                    Log.e("Polling", "통신 실패: ${t.message}")
                    // 네트워크 오류 시 2초 뒤 재시도
                    handler.postDelayed(pollingRunnable, 2000)
                }
            })
    }

    private fun updateProgressUI(data: StatusResponse) {
        binding.tvLoadingMessage.text = data.message ?: "분석 중..."
    }

    // ⭐️ [핵심] 서버에서 받은 AI 결과를 파싱해서 Firestore에 저장
    private fun saveAnalysisResultToFirestore(resultData: AnalysisResultData?) {
        if (resultData == null || contentId == null || topicId == null) {
            showError("결과 데이터가 비어있습니다.")
            return
        }

        binding.tvLoadingMessage.text = "AI 결과 저장 중..."

        // 1. Topic 정보(팀명, 기준 배점 등)를 먼저 가져옵니다.
        db.collection("contents").document(contentId!!)
            .collection("topics").document(topicId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val teamName = document.getString("teamInfo") ?: "Unknown Team"
                    val topicName = document.getString("topicName") ?: "Unknown Topic"

                    // DB에 저장된 원본 기준 (배점 확인용)
                    val standards = document.get("standards") as? List<HashMap<String, Any>> ?: emptyList()

                    // 2. 서버 결과(JSON) 파싱
                    val aiData = resultData.aiAssessment
                    val reviews = aiData?.reviews // 항목별 평가 리스트

                    // 전체 피드백 조합 (총평 + 영상 요약)
                    var overallFeedback = aiData?.overallSummary ?: "피드백 없음"
                    if (!aiData?.videoSummary.isNullOrEmpty()) {
                        overallFeedback += "\n\n[영상 요약]\n${aiData?.videoSummary}"
                    }

                    // 만약 JSON 파싱 실패로 리스트가 없고 통문장 피드백만 있다면 그걸 사용
                    if (reviews.isNullOrEmpty() && !aiData?.aiFeedback.isNullOrEmpty()) {
                        overallFeedback = aiData?.aiFeedback!!
                    }

                    // 3. 점수 리스트 구성 (Scores)
                    val scoresList = ArrayList<HashMap<String, Any>>()
                    var totalScore = 0

                    // 기준별로 순회하며 AI가 준 점수를 매칭
                    for (std in standards) {
                        val name = std["standardName"] as? String ?: ""
                        val maxScore = (std["standardScore"] as? Number)?.toInt() ?: 0

                        // AI 결과(reviews)에서 이름이 같은 항목 찾기
                        val match = reviews?.find { it.name == name }

                        // 찾았으면 AI 점수, 못 찾았으면 0점 처리
                        val actualScore = match?.score ?: 0
                        val feedback = match?.feedback ?: "분석 결과 없음"

                        totalScore += actualScore

                        // DB에 저장할 개별 항목 데이터
                        scoresList.add(hashMapOf(
                            "standardName" to name,
                            "standardScore" to maxScore,
                            "scoreValue" to actualScore,
                            "feedback" to feedback
                        ))
                    }

                    // 4. 최종 저장할 데이터 맵 생성
                    val presentationData = hashMapOf(
                        "contentId" to contentId,
                        "topicId" to topicId,
                        "teamInfo" to teamName,
                        "topicName" to topicName,
                        "overallFeedback" to overallFeedback,
                        "scores" to scoresList,
                        "totalScore" to totalScore,
                        "status" to "completed",
                        "gradeAt" to com.google.firebase.Timestamp.now(),
                        "videoUrl" to "uploaded_to_server" // 실제 URL은 서버나 로컬 경로 사용
                    )

                    // 5. Firestore 'presentations' 컬렉션에 추가
                    db.collection("presentations")
                        .add(presentationData)
                        .addOnSuccessListener { ref ->
                            // 저장 성공! 결과 화면으로 이동 (문서 ID 전달)
                            navigateToResultActivity(ref.id)
                        }
                        .addOnFailureListener { e ->
                            showError("DB 저장 실패: ${e.message}")
                        }

                } else {
                    showError("주제 정보를 찾을 수 없습니다.")
                }
            }
            .addOnFailureListener { e ->
                showError("Topic 로드 실패: ${e.message}")
            }
    }

    private fun navigateToResultActivity(presentationId: String) {
        val intent = Intent(this, AnalysisResultActivity::class.java)
        intent.putExtra("presentationId", presentationId)
        intent.putExtra("contentId", contentId)
        intent.putExtra("topicId", topicId)

        // 뒤로 가기 했을 때 다시 로딩 화면으로 오지 않도록 플래그 설정
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.tvLoadingMessage.text = "오류 발생"
        binding.tvLoadingSub.text = message
        // 에러 발생 시 폴링 중단
        handler.removeCallbacks(pollingRunnable)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 액티비티가 종료되면 폴링도 반드시 중단해야 함 (메모리 누수 방지)
        handler.removeCallbacks(pollingRunnable)
    }
}