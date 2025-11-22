package com.minyook.overnight.ui.file

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.minyook.overnight.databinding.ActivityUploadBinding
import java.util.UUID

class UploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUploadBinding
    private var selectedFileUri: Uri? = null

    // Step 2에서 넘겨받은 ID들
    private var contentId: String? = null
    private var topicId: String? = null

    // Firebase
    private lateinit var storage: FirebaseStorage
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

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

        // 1. Firebase 초기화
        storage = FirebaseStorage.getInstance()
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 2. 이전 화면에서 넘겨준 ID 받기
        contentId = intent.getStringExtra("contentId")
        topicId = intent.getStringExtra("topicId")

        setupListeners()
    }

    private fun setupListeners() {
        binding.cardUploadZone.setOnClickListener {
            filePickerLauncher.launch("video/*")
        }

        // "분석 시작하기" 버튼 클릭 시 -> 업로드 시작
        binding.btnAnalyze.setOnClickListener {
            if (selectedFileUri != null && contentId != null && topicId != null) {
                uploadVideoToFirebase(selectedFileUri!!)
            } else {
                Toast.makeText(this, "오류: 필요한 정보(ID)가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /*
    // --- 핵심: 영상 업로드 로직 ---
    private fun uploadVideoToFirebase(uri: Uri) {
        val user = auth.currentUser ?: return

        // 1. UI를 '업로드 중' 상태로 변경 (버튼 비활성화 등)
        binding.btnAnalyze.isEnabled = false
        binding.btnAnalyze.text = "업로드 중..."

        // 2. 파일명 생성 (중복 방지용 UUID)
        val fileName = "video_${UUID.randomUUID()}.mp4"
        // 저장 경로: videos/{userId}/{fileName}
        val storageRef = storage.reference.child("videos/${user.uid}/$fileName")

        // 3. 업로드 시작
        storageRef.putFile(uri)
            .addOnSuccessListener {
                // 4. 업로드 성공 -> 다운로드 URL 가져오기
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    savePresentationToFirestore(downloadUrl.toString(), fileName)
                }
            }
            .addOnFailureListener { e ->
                binding.btnAnalyze.isEnabled = true
                binding.btnAnalyze.text = "AI 분석 시작하기"
                Toast.makeText(this, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }*/

    // --- [수정됨] 로컬 테스트용 업로드 함수 (Storage 건너뛰기) ---
    private fun uploadVideoToFirebase(uri: Uri) {
        // val user = auth.currentUser ?: return // (주석: 로컬 테스트라 user 없어도 되지만, DB저장 때 필요하니 둠)

        // 1. UI 업데이트 (업로드 흉내)
        binding.btnAnalyze.isEnabled = false
        binding.btnAnalyze.text = "업로드 중..."

        // 2. [중요] 실제 업로드 코드를 건너뜁니다.
        // Storage에 올리는 대신, 내 폰에 있는 파일 경로(uri)를 그대로 DB에 저장합니다.
        // 이렇게 하면 Storage 오류가 나지 않습니다.
        val fakeDownloadUrl = uri.toString() // 로컬 주소 (content://...)
        val fakeFileName = "local_test_video.mp4"

        // 3. 1.5초 정도 딜레이를 줘서 업로드하는 척 연출 (선택사항)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // 바로 Firestore 저장 단계로 점프!
            savePresentationToFirestore(fakeDownloadUrl, fakeFileName)
        }, 1500)
    }

    // --- 핵심: Firestore에 데이터 저장 ---
    private fun savePresentationToFirestore(videoUrl: String, fileName: String) {
        val user = auth.currentUser ?: return

        // presentations 컬렉션에 저장할 데이터
        val presentationData = hashMapOf(
            "userId" to user.uid,
            "contentId" to contentId,
            "topicId" to topicId,
            "videoUrl" to videoUrl,
            "fileName" to fileName,
            "status" to "processing", // 처리 중 상태로 시작
            "totalScore" to 0,       // 아직 점수 없음
            "uploadedAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("presentations")
            .add(presentationData)
            .addOnSuccessListener { documentReference ->
                // 5. 저장 성공 -> 로딩(분석 대기) 화면으로 이동
                val intent = Intent(this, AnalysisProgressActivity::class.java)
                intent.putExtra("presentationId", documentReference.id) // 생성된 ID 전달
                intent.putExtra("contentId", contentId)
                intent.putExtra("topicId", topicId)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "데이터 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.btnAnalyze.isEnabled = true
                binding.btnAnalyze.text = "AI 분석 시작하기"
            }
    }

    private fun updateUiAfterSelection(uri: Uri) {
        val fileName = getFileNameFromUri(uri)
        binding.tvFileName.text = fileName
        binding.layoutFileInfo.visibility = View.VISIBLE
        binding.tvUploadTitle.text = "파일 변경하기"
        binding.btnAnalyze.isEnabled = true
        binding.btnAnalyze.alpha = 1.0f
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
        return result ?: "unknown_file.mp4"
    }
}