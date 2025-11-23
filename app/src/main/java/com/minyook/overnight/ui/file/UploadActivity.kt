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

    // 이전 화면에서 받은 ID
    private var contentId: String? = null
    private var topicId: String? = null

    // DB (채점 기준 불러오기용)
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

        db = FirebaseFirestore.getInstance()

        contentId = intent.getStringExtra("contentId")
        topicId = intent.getStringExtra("topicId")

        setupListeners()
    }

    private fun setupListeners() {
        binding.cardUploadZone.setOnClickListener {
            filePickerLauncher.launch("video/*")
        }

        binding.btnAnalyze.setOnClickListener {
            if (selectedFileUri != null && contentId != null && topicId != null) {
                // 1. 먼저 DB에서 채점 기준을 가져오고 -> 업로드를 시작합니다.
                fetchCriteriaAndUpload(selectedFileUri!!)
            } else {
                Toast.makeText(this, "오류: 필요한 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------
    // 1단계: Firestore에서 채점 기준(Standard) 가져오기
    // ---------------------------------------------------------
    private fun fetchCriteriaAndUpload(uri: Uri) {
        binding.btnAnalyze.isEnabled = false
        binding.btnAnalyze.text = "채점 기준 불러오는 중..."

        db.collection("contents").document(contentId!!)
            .collection("topics").document(topicId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // DB에 저장된 Map 리스트를 가져옴
                    val standardsMap = document.get("standards") as? List<HashMap<String, Any>>

                    // 서버 포맷(ScoringCriteria)으로 변환
                    val criteriaList = ArrayList<ScoringCriteria>()
                    if (standardsMap != null) {
                        for (map in standardsMap) {
                            val name = map["standardName"] as? String ?: ""
                            val score = (map["standardScore"] as? Number)?.toInt() ?: 0
                            val desc = map["standardDetail"] as? String ?: ""
                            criteriaList.add(ScoringCriteria(name, score, desc))
                        }
                    }

                    // 기준을 다 가져왔으니 이제 진짜 업로드 시작!
                    uploadVideoToServer(uri, criteriaList)

                } else {
                    Toast.makeText(this, "채점 기준을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "DB 오류: ${it.message}", Toast.LENGTH_SHORT).show()
                resetButton()
            }
    }

    // ---------------------------------------------------------
    // 2단계: FastAPI 서버로 영상과 기준 전송하기 (Retrofit)
    // ---------------------------------------------------------
    private fun uploadVideoToServer(uri: Uri, criteriaList: List<ScoringCriteria>) {
        binding.btnAnalyze.text = "서버로 전송 중..."

        // 1. Uri -> 실제 파일(File)로 변환 (캐시 폴더에 복사)
        val file = getFileFromUri(uri)
        if (file == null) {
            Toast.makeText(this, "파일 변환 실패", Toast.LENGTH_SHORT).show()
            resetButton()
            return
        }

        // 2. RequestBody 생성 (영상 파일)
        // "video/*" 또는 "multipart/form-data"
        val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

        // 3. RequestBody 생성 (채점 기준 JSON 문자열)
        val gson = Gson()
        val criteriaJson = gson.toJson(criteriaList) // 리스트를 JSON 문자열로 변환
        val criteriaBody = criteriaJson.toRequestBody("text/plain".toMediaTypeOrNull())

        // 4. Retrofit 전송 시작
        RetrofitClient.instance.analyzeVideo(body, criteriaBody)
            .enqueue(object : Callback<AnalysisResponse> {
                override fun onResponse(
                    call: Call<AnalysisResponse>,
                    response: Response<AnalysisResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        // 성공! Job ID를 받았습니다.
                        val jobId = response.body()!!.jobId
                        Log.d("Upload", "Job ID: $jobId")

                        // 로딩 화면으로 이동 (Job ID 전달)
                        val intent = Intent(this@UploadActivity, AnalysisProgressActivity::class.java)
                        intent.putExtra("jobId", jobId) // ⭐ 핵심: 서버 작업 ID
                        intent.putExtra("contentId", contentId)
                        intent.putExtra("topicId", topicId)
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

    // ---------------------------------------------------------
    // 유틸리티: Uri -> 임시 파일 변환
    // ---------------------------------------------------------
    private fun getFileFromUri(uri: Uri): File? {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileNameFromUri(uri)
            // 앱 캐시 폴더에 임시 파일 생성
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