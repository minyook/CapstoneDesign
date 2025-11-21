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
import com.google.gson.Gson // 👈 Gson 임포트
import com.minyook.overnight.R
// PresentationInfoActivity에서 정의했던 데이터 클래스와 일치해야 합니다.
// 여기서는 UploadActivity 내부에 다시 정의합니다.
data class UploadInfo(
    val fileName: String,
    val fileUriString: String // Uri 객체는 SharedPreferences에 직접 저장할 수 없으므로 String으로 변환
)


class UploadActivity : AppCompatActivity(), UploadOptionsBottomSheet.UploadOptionListener {

    private lateinit var btnAnalyze: Button
    private lateinit var tvFileName: TextView
    private var selectedFileUri: Uri? = null // 👈 선택된 파일의 URI를 임시 저장

    // 로컬 저장소 상수
    private val PREFS_NAME = "AnalysisPrefs"
    private val KEY_UPLOAD_INFO = "upload_info_json"
    private val gson = Gson()

    // 1. 파일 선택 결과를 처리하는 런처
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val fileUri: Uri? = data?.data

            if (fileUri != null) {
                val fileName = getFileNameFromUri(fileUri)

                // 🔴 파일 정보 임시 저장 및 UI 업데이트
                selectedFileUri = fileUri
                tvFileName.text = fileName ?: "파일 이름 읽기 실패"

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

        // 🚨 초기 상태 복구 (이전에 저장된 파일이 있으면 로드)
        val loadedUploadInfo = loadUploadInfo()
        if (loadedUploadInfo != null) {
            selectedFileUri = Uri.parse(loadedUploadInfo.fileUriString)
            tvFileName.text = loadedUploadInfo.fileName
            tvFileName.visibility = View.VISIBLE
            btnAnalyze.isEnabled = true
        }

        uploadZone.setOnClickListener {
            UploadOptionsBottomSheet().show(supportFragmentManager, "UploadOptions")
        }

        // 🔴 [수정] AI 분석 버튼 클릭 리스너: 데이터 저장 후 이동 🔴
        btnAnalyze.setOnClickListener {
            if (selectedFileUri != null) {
                // 1. 로컬 저장 함수 호출
                saveUploadInfoData(selectedFileUri!!, tvFileName.text.toString())

                // 2. 로딩 화면으로 이동
                val intent = Intent(this, AnalysisProgressActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "분석할 파일을 먼저 선택해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionSelected(option: UploadOptionsBottomSheet.UploadOption) {
        // ... (파일 선택 로직은 동일) ...
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

    // -----------------------------------------------------------------
    // 💾 신규: 입력된 데이터를 수집하여 로컬에 저장하고 로드하는 함수
    // -----------------------------------------------------------------

    /**
     * 파일 이름과 URI를 로컬에 저장합니다.
     */
    private fun saveUploadInfoData(uri: Uri, name: String) {
        val uploadInfo = UploadInfo(
            fileName = name,
            fileUriString = uri.toString() // URI를 문자열로 변환하여 저장
        )

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val jsonString = gson.toJson(uploadInfo)
        prefs.edit().putString(KEY_UPLOAD_INFO, jsonString).apply()
        Toast.makeText(this, "업로드 정보 저장 완료.", Toast.LENGTH_SHORT).show()
    }

    /**
     * 로컬에서 저장된 업로드 정보를 로드합니다.
     */
    private fun loadUploadInfo(): UploadInfo? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_UPLOAD_INFO, null)

        return if (jsonString != null) {
            gson.fromJson(jsonString, UploadInfo::class.java)
        } else {
            null
        }
    }

    // 2. Uri에서 파일 이름을 가져오는 헬퍼 함수 (동일)
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