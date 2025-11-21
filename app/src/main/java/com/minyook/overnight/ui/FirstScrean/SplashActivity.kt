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
import com.minyook.overnight.R
import com.minyook.overnight.ui.FirstScrean.AuthActivity // 👈 AuthActivity 임포트 (가정)
// 또는 com.minyook.overnight.ui.mainscrean.OvernightActivity (프로젝트 구조에 따라)

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo: ImageView = findViewById(R.id.iv_splash_logo)
        val spinner: ProgressBar = findViewById(R.id.progress_bar)
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)

        logo.visibility = View.VISIBLE
        spinner.visibility = View.VISIBLE
        logo.startAnimation(fadeIn)
        spinner.startAnimation(fadeIn)

        Handler(Looper.getMainLooper()).postDelayed({

            // 🔴 [수정] OnboardingActivity 대신 AuthActivity로 바로 이동
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
            finish()
        }, 2500)
    }
}