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
        // 결과 화면으로 이동 (AnalysisResultActivity는 4.에서 정의)
        val intent = Intent(this, AnalysisResultActivity::class.java)

        // 뒤로가기 버튼으로 로딩 화면이 다시 나타나지 않도록 모든 이전 화면을 지웁니다.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // 현재 로딩 화면 종료
    }
}