package com.minyook.overnight.ui.mainscrean

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.minyook.overnight.R
import com.minyook.overnight.ui.FirstScrean.LoginFragment
import com.minyook.overnight.ui.home.HomeFragment // HomeFragment import
import com.minyook.overnight.ui.mypage.MyPageFragment // MyPageFragment import
import com.minyook.overnight.ui.folder.FolderFragment // ⭐️ FolderFragment import (패키지 경로 가정)



// ⚠️ 클래스 이름을 여기에서 변경했습니다.
class OvernightActivity : AppCompatActivity() {

    // 1. 홈, 마이페이지 Fragment를 미리 준비합니다.
    private val homeFragment = HomeFragment()
    private val myPageFragment = MyPageFragment()
    private val folderFragment = FolderFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overnight)

        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_navigation)

        // Firebase Auth 인스턴스 가져오기
        val auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            // ⭐ 로그아웃 상태라면 로그인 화면(LoginActivity/LoginFragment)으로 이동
            val intent = Intent(this, LoginFragment::class.java)
            startActivity(intent)
            finish() // 현재 액티비티 종료
            return
        }

        // 2. 앱이 처음 켜졌을 때 '홈' 화면을 기본으로 보여줍니다.
        if (savedInstanceState == null) {
            loadFragment(homeFragment)
        }

        // 3. ⚡️ 여기가 '프래그먼트 전환'을 불러오는 핵심 코드입니다. ⚡️
        // 네비게이션 바의 아이템이 선택되었을 때를 감지합니다.
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // '홈' 아이콘을 클릭하면
                R.id.nav_home -> {
                    loadFragment(homeFragment)
                    true
                }

                // '폴더' 아이콘을 클릭하면
                R.id.nav_folder -> {
                    // ⭐️ TODO: 폴더 프래그먼트 로드 -> 로직 구현 완료
                    loadFragment(folderFragment)
                    true
                }

                // '마이페이지' 아이콘을 클릭하면
                R.id.nav_mypage -> {
                    // 1번에서 준비해둔 myPageFragment를 불러옵니다.
                    loadFragment(myPageFragment)
                    true
                }
                else -> false
            }
        }
    }

    // 4. 이 함수가 Fragment를 화면에 실제로 띄워줍니다.
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}