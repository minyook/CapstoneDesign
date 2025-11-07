package com.minyook.overnight.ui.mainscrean

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.minyook.overnight.R
import com.minyook.overnight.databinding.ActivityOvernightBinding

class OvernightActivity : AppCompatActivity() {
    private var mBinding: ActivityOvernightBinding? = null
    private val binding get() = mBinding!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overnight)
        mBinding = ActivityOvernightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // hiihihihih
        // activity_main.xml 레이아웃을 화면에 연결합니다.
        setContentView(R.layout.activity_overnight)
    }
}