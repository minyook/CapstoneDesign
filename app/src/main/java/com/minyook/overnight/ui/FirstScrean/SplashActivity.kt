package com.minyook.overnight.ui.FirstScrean

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth // ⭐ 추가: Firebase 인증 Import
import com.minyook.overnight.R
import com.minyook.overnight.ui.mainscrean.OvernightActivity // ⭐ 추가: 로그인 시 이동할 메인 Activity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    // ⭐ 추가: Firebase Auth 인스턴스 선언
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // ⭐ 추가: Firebase Auth 인스턴스 초기화
        auth = FirebaseAuth.getInstance()

        val logo: ImageView = findViewById(R.id.iv_splash_logo)
        val spinner: ProgressBar = findViewById(R.id.progress_bar)
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)

        logo.visibility = View.VISIBLE
        spinner.visibility = View.VISIBLE
        logo.startAnimation(fadeIn)
        spinner.startAnimation(fadeIn)

        Handler(Looper.getMainLooper()).postDelayed({
            // ⭐ [수정] Firebase 인증 상태에 따라 경로 분기
            checkLoginStatus()
        }, 2500)
    }

    // ⭐ 추가: 로그인 상태 체크 및 화면 전환 함수
    private fun checkLoginStatus() {
        val nextActivity: Class<*>

        if (auth.currentUser != null) {
            // 로그인 상태: OvernightActivity로 이동 (로그인된 사용자 화면)
            nextActivity = OvernightActivity::class.java
        } else {
            // 로그아웃 상태: AuthActivity로 이동 (LoginFragment를 호스팅하는 액티비티)
            nextActivity = AuthActivity::class.java
        }

        val intent = Intent(this, nextActivity)

        // FLAG_ACTIVITY_CLEAR_TASK와 FLAG_ACTIVITY_NEW_TASK를 사용하여
        // 기존의 모든 스택을 지우고 앱을 새롭게 시작
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish() // SplashActivity는 종료합니다.
    }
}