package com.minyook.overnight.ui.file // 패키지명은 본인 프로젝트에 맞게 수정

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.minyook.overnight.databinding.ActivityUploadBinding
import com.minyook.overnight.ui.file.AnalysisProgressActivity

class UploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUploadBinding
    private var selectedFileUri: Uri? = null

    // 1. 파일 선택 결과를 받는 런처 설정
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedFileUri = uri
                updateUiAfterSelection(uri) // 파일을 선택하면 UI를 업데이트하는 함수 호출
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        // 2. 업로드 영역 클릭 시 파일 선택창 열기
        binding.cardUploadZone.setOnClickListener {
            // 동영상 파일만 필터링 (필요하면 "*/*"로 변경 가능)
            filePickerLauncher.launch("video/*")
        }

        // 분석 버튼 클릭 리스너
        binding.btnAnalyze.setOnClickListener {
            if (selectedFileUri != null) {
                // 1. 로딩 화면(AnalysisLoadingActivity)으로 이동할 Intent 생성
                // (주의: AnalysisLoadingActivity 클래스가 만들어져 있어야 합니다)
                val intent = Intent(this, AnalysisProgressActivity::class.java)

                // 필요하다면 선택한 파일 정보를 넘겨줄 수 있습니다
                intent.putExtra("videoUri", selectedFileUri.toString())

                // 2. 화면 이동 시작
                startActivity(intent)
            }
        }
    }

    // 3. 파일 선택 후 UI 업데이트 (여기가 핵심!)
    private fun updateUiAfterSelection(uri: Uri) {
        // (1) 파일 이름 가져와서 텍스트뷰에 넣기
        val fileName = getFileNameFromUri(uri)
        binding.tvFileName.text = fileName

        // (2) 파일 정보 레이아웃 보여주기
        binding.layoutFileInfo.visibility = View.VISIBLE

        // (3) 업로드 안내 영역은 숨기거나 유지 (선택사항. 여기선 유지하되 텍스트만 변경 예시)
        binding.tvUploadTitle.text = "파일 변경하기"

        // (4) 분석 버튼 활성화 하기
        binding.btnAnalyze.isEnabled = true
        binding.btnAnalyze.alpha = 1.0f // 흐릿했던 버튼을 선명하게 변경
    }

    // URI에서 파일 이름 추출하는 헬퍼 함수
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