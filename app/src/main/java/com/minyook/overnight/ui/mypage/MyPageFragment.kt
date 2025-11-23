package com.minyook.overnight.ui.mypage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.minyook.overnight.R

class MyPageFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // fragment_mypage.xml 레이아웃을 화면에 표시합니다.
        return inflater.inflate(R.layout.fragment_mypage, container, false)
    }
}