// ui/file/AnalysisProgressActivity.kt

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

    // 1 4단계 경로를 위한 ID 변수들 선언
    private var userId: String? = null     // 유저 ID
    private var folderId: String? = null   // 폴더 ID (기존 contentId 역할)
    private var topicId: String? = null    // 주제 ID

    private lateinit var db: FirebaseFirestore
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

        // 2 Intent로 넘어온 ID들 받기
        jobId = intent.getStringExtra("jobId")
        userId = intent.getStringExtra("userId")       // UploadActivity에서 넘겨준 userId
        folderId = intent.getStringExtra("contentId")  // Folder ID
        topicId = intent.getStringExtra("topicId")     // Topic ID

        // 필수 정보가 다 있는지 확인
        if (jobId != null && userId != null && folderId != null && topicId != null) {
            // 1초 뒤부터 폴링 시작
            handler.postDelayed(pollingRunnable, 1000)
        } else {
            Toast.makeText(this, "경로 정보가 누락되었습니다.", Toast.LENGTH_SHORT).show()
            Log.e("Progress", "Missing ID - User:$userId, Folder:$folderId, Topic:$topicId")
            finish()
        }
    }

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
                        updateProgressUI(statusData)

                        when (statusData.status) {
                            "Complete" -> {
                                // 분석 완료 -> 저장 시작
                                saveAnalysisResultToFirestore(statusData.result)
                            }
                            "Error" -> {
                                showError(statusData.message ?: "서버 오류 발생")
                            }
                            else -> {
                                // 진행 중 -> 계속 폴링
                                handler.postDelayed(pollingRunnable, 1000)
                            }
                        }
                    } else {
                        handler.postDelayed(pollingRunnable, 2000)
                    }
                }

                override fun onFailure(call: Call<StatusResponse>, t: Throwable) {
                    Log.e("Polling", "통신 실패: ${t.message}")
                    handler.postDelayed(pollingRunnable, 2000)
                }
            })
    }

    private fun updateProgressUI(data: StatusResponse) {
        binding.tvLoadingMessage.text = data.message ?: "분석 중..."
    }

    // 3 파이어베이스 저장 경로 변경 (4단계 구조)
    private fun saveAnalysisResultToFirestore(resultData: AnalysisResultData?) {
        if (resultData == null) {
            showError("결과 데이터가 비어있습니다.")
            return
        }

        binding.tvLoadingMessage.text = "AI 결과 저장 중..."

        // 1. Topic 문서 위치 참조: User -> Folders -> Topics
        val topicRef = db.collection("user").document(userId!!)
            .collection("folders").document(folderId!!)
            .collection("topics").document(topicId!!)

        topicRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val teamName = document.getString("teamInfo") ?: "Unknown Team"
                val topicName = document.getString("topicName") ?: "Unknown Topic"

                // DB에 저장된 기준표 가져오기 (배점 정보)
                val standards = document.get("standards") as? List<HashMap<String, Any>> ?: emptyList()

                // 2. 서버 결과(JSON) 파싱
                val aiData = resultData.aiAssessment
                val reviews = aiData?.reviews

                // 전체 피드백 조합
                var overallFeedback = aiData?.overallSummary ?: "피드백 없음"
                if (!aiData?.videoSummary.isNullOrEmpty()) {
                    overallFeedback += "\n\n[영상 요약]\n${aiData?.videoSummary}"
                }
                // JSON 파싱 실패 시 텍스트 피드백 사용 (안전장치)
                if (reviews.isNullOrEmpty() && !aiData?.aiFeedback.isNullOrEmpty()) {
                    overallFeedback = aiData?.aiFeedback!!
                }

                // 3. 점수 리스트 구성
                val scoresList = ArrayList<HashMap<String, Any>>()
                var totalScore = 0

                for (std in standards) {
                    val name = std["standardName"] as? String ?: ""
                    val maxScore = (std["standardScore"] as? Number)?.toInt() ?: 0

                    // AI 결과에서 이름이 같은(혹은 포함된) 항목 찾기
                    val match = reviews?.find { it.name.contains(name) || name.contains(it.name) }

                    val actualScore = match?.score ?: 0
                    val feedback = match?.feedback ?: "분석 내용 없음"

                    totalScore += actualScore

                    scoresList.add(hashMapOf(
                        "standardName" to name,
                        "standardScore" to maxScore,
                        "scoreValue" to actualScore,
                        "feedback" to feedback
                    ))
                }

                // 4. 저장할 데이터 생성
                val presentationData = hashMapOf(
                    "teamInfo" to teamName,
                    "topicName" to topicName,
                    "overallFeedback" to overallFeedback,
                    "scores" to scoresList,
                    "totalScore" to totalScore,
                    "status" to "completed",
                    "gradeAt" to com.google.firebase.Timestamp.now()
                )

                // 5. Topics 하위에 Presentations 컬렉션을 만들어 저장
                topicRef.collection("presentations")
                    .add(presentationData)
                    .addOnSuccessListener { ref ->
                        // 저장 성공! 결과 화면으로 이동 (생성된 ID 전달)
                        navigateToResultActivity(ref.id)
                    }
                    .addOnFailureListener { e ->
                        showError("DB 저장 실패: ${e.message}")
                    }

            } else {
                showError("주제 정보를 찾을 수 없습니다.")
            }
        }.addOnFailureListener { e ->
            showError("Topic 로드 실패: ${e.message}")
        }
    }

    private fun navigateToResultActivity(presentationId: String) {
        val intent = Intent(this, AnalysisResultActivity::class.java)

        // 4 결과 화면에서도 경로를 알 수 있게 ID들 전달
        intent.putExtra("userId", userId)
        intent.putExtra("contentId", folderId)
        intent.putExtra("topicId", topicId)
        intent.putExtra("presentationId", presentationId)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        binding.tvLoadingMessage.text = "오류 발생"
        binding.tvLoadingSub.text = message
        handler.removeCallbacks(pollingRunnable)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollingRunnable)
    }
}