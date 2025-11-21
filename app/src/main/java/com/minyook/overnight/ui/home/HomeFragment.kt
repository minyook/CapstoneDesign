package com.minyook.overnight.ui.home


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.minyook.overnight.R


/**
 * HomeFragment:
 * 1. '+' 버튼(FAB) 클릭 이벤트를 처리합니다.
 * 2. 클릭 시 BottomSheet 대신 PopupWindow를 띄웁니다.
 * 3. 팝업창의 "파일 업로드"를 누르면 PresentationInfoActivity로 이동합니다.
 */
class HomeFragment : Fragment() { // 👈 OnOptionClickListener 인터페이스 구현부 삭제

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // fragment_home.xml 레이아웃을 이 Fragment에 연결합니다.
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
/*
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }*/
}