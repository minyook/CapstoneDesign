package com.minyook.overnight.ui.mainscrean

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.minyook.overnight.R

class GuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        // 1. XML에서 뷰(버튼, 레이아웃) 찾아오기
        val btnBack = findViewById<ImageButton>(R.id.btn_back)

        // 탭 버튼들
        val btnCriteria = findViewById<TextView>(R.id.btn_tab_criteria) // 평가 기준 탭
        val btnUsage = findViewById<TextView>(R.id.btn_tab_usage)       // 이용 방법 탭

        // 보여줄 내용 레이아웃들
        val layoutCriteria = findViewById<LinearLayout>(R.id.layout_criteria_content)
        val layoutUsage = findViewById<LinearLayout>(R.id.layout_usage_content)

        // 2. 뒤로가기 버튼 기능
        btnBack.setOnClickListener {
            finish()
        }

        // 3. [평가 기준] 탭을 눌렀을 때
        btnCriteria.setOnClickListener {
            // (1) 내용 화면 전환 (기준은 보이고, 방법은 숨김)
            layoutCriteria.visibility = View.VISIBLE
            layoutUsage.visibility = View.GONE

            // (2) 탭 디자인 변경 (선택된 탭은 파란색 배경)
            btnCriteria.setBackgroundResource(R.drawable.bg_tab_selected_blue)
            btnCriteria.setTextColor(Color.WHITE)

            // (3) 선택 안 된 탭은 배경 제거 및 회색 글씨
            btnUsage.setBackgroundResource(0)
            btnUsage.setTextColor(Color.parseColor("#888888"))
        }

        // 4. [이용 방법] 탭을 눌렀을 때
        btnUsage.setOnClickListener {
            // (1) 내용 화면 전환 (방법은 보이고, 기준은 숨김)
            layoutCriteria.visibility = View.GONE
            layoutUsage.visibility = View.VISIBLE

            // (2) 탭 디자인 변경
            btnUsage.setBackgroundResource(R.drawable.bg_tab_selected_blue)
            btnUsage.setTextColor(Color.WHITE)

            // (3) 선택 안 된 탭 디자인 초기화
            btnCriteria.setBackgroundResource(0)
            btnCriteria.setTextColor(Color.parseColor("#888888"))
        }
    }
}