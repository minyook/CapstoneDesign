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

            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            finish()
        }, 2500)
    }
}