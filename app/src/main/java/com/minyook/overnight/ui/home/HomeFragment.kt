package com.minyook.overnight.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.minyook.overnight.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // fragment_home.xml 레이아웃을 이 Fragment에 연결합니다.
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ⚠️ 중요: RecyclerView, FAB 클릭 등
        // 홈 화면의 모든 로직은 이제 MainActivity.kt가 아니라
        // 이곳 HomeFragment.kt의 onViewCreated 안에 작성해야 합니다.

        // 예: val fab: FloatingActionButton = view.findViewById(R.id.fab_add)
        // fab.setOnClickListener { ... }
    }
}