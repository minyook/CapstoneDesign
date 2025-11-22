// 📁 AnalysisProgressActivity.kt
package com.minyook.overnight.ui.file

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.minyook.overnight.R

class AnalysisProgressActivity : AppCompatActivity() {

    // 🔴 분석 시뮬레이션 시간 (3초) 🔴
    private val analysisDurationMillis = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_progress) // 로딩 UI 연결

        // 1. 분석 완료 시뮬레이션을 위한 딜레이 설정
        Handler(Looper.getMainLooper()).postDelayed({
            // 2. 딜레이 후 결과 화면으로 이동
            navigateToResults()
        }, analysisDurationMillis)
    }

    private fun navigateToResults() {
        val intent = Intent(this, AnalysisResultActivity::class.java)

        // UploadActivity에서 받은 데이터들을 결과 화면으로 그대로 넘겨줍니다.
        intent.putExtra("presentationId", getIntent().getStringExtra("presentationId"))
        intent.putExtra("contentId", getIntent().getStringExtra("contentId"))
        intent.putExtra("topicId", getIntent().getStringExtra("topicId"))

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}