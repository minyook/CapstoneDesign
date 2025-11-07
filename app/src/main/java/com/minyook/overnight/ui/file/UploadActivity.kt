package com.minyook.overnight.ui.file

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.minyook.overnight.R

class UploadActivity : AppCompatActivity(), UploadOptionsBottomSheet.UploadOptionListener {

    private lateinit var btnAnalyze: Button
    private lateinit var tvFileName: TextView

    // 1. 파일 선택 결과를 처리하는 런처 (이 부분이 수정되었습니다)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val fileUri: Uri? = data?.data

            if (fileUri != null) {
                // --- ⚠️ 파일 이름 가져오는 로직 추가 ---
                val fileName = getFileNameFromUri(fileUri)
                tvFileName.text = fileName ?: "파일 이름 읽기 실패" // 파일 이름 표시
                // --- ---

                tvFileName.visibility = View.VISIBLE
                btnAnalyze.isEnabled = true // 파일이 선택되면 분석 버튼 활성화
            }
        } else {
            Toast.makeText(this, "파일 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        val uploadZone: CardView = findViewById(R.id.card_upload_zone)
        btnAnalyze = findViewById(R.id.btn_analyze)
        tvFileName = findViewById(R.id.tv_file_name)

        uploadZone.setOnClickListener {
            UploadOptionsBottomSheet().show(supportFragmentManager, "UploadOptions")
        }

        btnAnalyze.setOnClickListener {
            Toast.makeText(this, "AI 분석을 시작합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionSelected(option: UploadOptionsBottomSheet.UploadOption) {
        when (option) {
            UploadOptionsBottomSheet.UploadOption.GALLERY -> {
                val intent = Intent(Intent.ACTION_PICK)
                intent.type = "video/*, image/*"
                filePickerLauncher.launch(intent)
            }
            UploadOptionsBottomSheet.UploadOption.FILES -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                filePickerLauncher.launch(intent)
            }
            UploadOptionsBottomSheet.UploadOption.DRIVE -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                filePickerLauncher.launch(intent)
            }
        }
    }

    // 2. ⚠️ Uri에서 파일 이름을 가져오는 헬퍼 함수 (새로 추가)
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fileName
    }
}