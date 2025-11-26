package com.minyook.overnight.ui.file

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.minyook.overnight.data.model.AnalysisResponse
import com.minyook.overnight.data.model.ScoringCriteria
import com.minyook.overnight.data.network.RetrofitClient
import com.minyook.overnight.databinding.ActivityUploadBinding
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class UploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUploadBinding
    private var selectedFileUri: Uri? = null
    // [수정 1] userId 변수 추가 및 contentId 변수 유지
    private var userId: String? = null    // 추가됨
    private var contentId: String? = null // (폴더 ID 역할)
    private var topicId: String? = null
    private lateinit var db: FirebaseFirestore

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedFileUri = uri
                updateUiAfterSelection(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUI()

        db = FirebaseFirestore.getInstance()

        // [수정 2] ID들 받기 (키 이름 불일치 해결)
        // PresentationInfoActivity에서 "folderId"로 보냈으므로 "folderId"로 받아야 합니다.
        userId = intent.getStringExtra("userId")         //  User ID 수신
        contentId = intent.getStringExtra("folderId")    // "contentId" -> "folderId"로 변경해서 받기
        topicId = intent.getStringExtra("topicId")

        // (디버깅용 로그)
        Log.d("UploadActivity", "Received IDs - User: $userId, Folder: $contentId, Topic: $topicId")

        setupListeners()
    }

    // ★ [추가] 화면이 다시 보일 때(다른 앱 갔다 왔을 때)도 숨김 유지
    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    // ★ [핵심 기능] 시스템 바(상태바, 내비게이션 바) 숨기기 함수
    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // 스와이프 시 잠깐 나타났다가 다시 사라지게 설정
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // 상태바와 내비게이션바 모두 숨김
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupListeners() {
        binding.cardUploadZone.setOnClickListener {
            filePickerLauncher.launch("video/*")
        }

        binding.btnAnalyze.setOnClickListener {
            if (selectedFileUri != null && contentId != null && topicId != null) {
                fetchCriteriaAndUpload(selectedFileUri!!)
            } else {
                Toast.makeText(this, "오류: 필요한 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ... (이하 fetchCriteriaAndUpload, uploadVideoToServer 등 기존 로직 유지) ...

    // 1단계: Firestore에서 채점 기준 가져오기
    private fun fetchCriteriaAndUpload(uri: Uri) {
        binding.btnAnalyze.isEnabled = false
        binding.btnAnalyze.text = "채점 기준 불러오는 중..."

        // ⚠️ 방어 코드: userId가 없으면 진행 불가
        if (userId == null) {
            Toast.makeText(this, "유저 정보가 없어 기준을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            resetButton()
            return
        }

        // [수정] 경로 변경: contents -> user/{uid}/folders/{folderId}/topics/{topicId}
        // (참고: 여기서 contentId 변수는 folderId 값을 담고 있습니다)
        db.collection("user").document(userId!!)
            .collection("folders").document(contentId!!)
            .collection("topics").document(topicId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val standardsMap = document.get("standards") as? List<HashMap<String, Any>>
                    val criteriaList = ArrayList<ScoringCriteria>()

                    if (standardsMap != null) {
                        for (map in standardsMap) {
                            val name = map["standardName"] as? String ?: ""
                            val score = (map["standardScore"] as? Number)?.toInt() ?: 0
                            val desc = map["standardDetail"] as? String ?: ""
                            criteriaList.add(ScoringCriteria(name, score, desc))
                        }
                    }
                    // 기준을 찾았으면 서버로 전송 시작
                    uploadVideoToServer(uri, criteriaList)
                } else {
                    Toast.makeText(this, "채점 기준을 찾을 수 없습니다. (경로 확인 필요)", Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "DB 오류: ${it.message}", Toast.LENGTH_SHORT).show()
                resetButton()
            }
    }

    private fun uploadVideoToServer(uri: Uri, criteriaList: List<ScoringCriteria>) {
        binding.btnAnalyze.text = "서버로 전송 중..."

        val file = getFileFromUri(uri) ?: return // (앞부분 생략됨)
        if (file == null) {
            Toast.makeText(this, "파일 변환 실패", Toast.LENGTH_SHORT).show()
            resetButton()
            return
        }


        val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val gson = Gson()
        val criteriaBody = gson.toJson(criteriaList).toRequestBody("text/plain".toMediaTypeOrNull())

        RetrofitClient.instance.analyzeVideo(body, criteriaBody)
            .enqueue(object : Callback<AnalysisResponse> {
                override fun onResponse(
                    call: Call<AnalysisResponse>,
                    response: Response<AnalysisResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val jobId = response.body()!!.jobId
                        Log.d("Upload", "Job ID: $jobId")

                        // ⭐️ [핵심 수정] 다음 화면으로 UserID와 FolderID(contentId)를 전달
                        val intent = Intent(this@UploadActivity, AnalysisProgressActivity::class.java)
                        intent.putExtra("jobId", jobId)
                        intent.putExtra("userId", userId)       // ⭐️ User ID 전달 필수
                        intent.putExtra("contentId", contentId) // Folder ID 전달
                        intent.putExtra("topicId", topicId)     // Topic ID 전달
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@UploadActivity, "서버 오류: ${response.code()}", Toast.LENGTH_SHORT).show()
                        resetButton()
                    }
                }

                override fun onFailure(call: Call<AnalysisResponse>, t: Throwable) {
                    Toast.makeText(this@UploadActivity, "통신 실패: ${t.message}", Toast.LENGTH_LONG).show()
                    Log.e("Upload", "Error", t)
                    resetButton()
                }
            })
    }

    private fun getFileFromUri(uri: Uri): File? {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileNameFromUri(uri)
            val tempFile = File(cacheDir, fileName)
            val outputStream = FileOutputStream(tempFile)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor.use {
                if (it != null && it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "temp_video.mp4"
    }

    private fun updateUiAfterSelection(uri: Uri) {
        val fileName = getFileNameFromUri(uri)
        binding.tvFileName.text = fileName
        binding.layoutFileInfo.visibility = View.VISIBLE
        binding.tvUploadTitle.text = "파일 변경하기"
        resetButton()
    }

    private fun resetButton() {
        binding.btnAnalyze.isEnabled = true
        binding.btnAnalyze.text = "AI 분석 시작하기"
        binding.btnAnalyze.alpha = 1.0f
    }
}