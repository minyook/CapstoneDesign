package com.minyook.overnight.ui.mypage

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore // ⭐ 1. Firestore import 추가
import com.minyook.overnight.databinding.FragmentMypageBinding
import com.minyook.overnight.ui.file.SubjectFolderActivity
import com.minyook.overnight.ui.FirstScrean.AuthActivity

class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance() // ⭐ 2. Firestore 인스턴스 초기화

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMypageBinding.inflate(inflater, container, false)
        // auth는 이미 클래스 레벨에서 초기화됨
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ⭐ 3. Firestore에서 이름 가져오는 함수 호출
        loadUserProfileFromFirestore()

        // 1. [전체 노트 보기] -> SubjectFolderActivity 이동
        binding.cardAllNotes.setOnClickListener {
            val intent = Intent(requireContext(), SubjectFolderActivity::class.java)
            startActivity(intent)
        }

        // 2. [로그아웃] 버튼 클릭
        binding.btnLogout.setOnClickListener {
            performLogout()
        }
    }

    // ⭐ 4. HomeFragment와 동일하게 Firestore에서 사용자 이름 로드
    private fun loadUserProfileFromFirestore() {
        val user = auth.currentUser

        // 이메일은 Auth 객체에서 직접 가져와 먼저 설정
        binding.tvProfileEmail.text = user?.email ?: "로그인 필요"

        if (user != null) {
            val uid = user.uid

            // Firestore 경로: user/{UID}
            db.collection("user").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val name = document.getString("name")

                        if (!name.isNullOrEmpty()) {
                            binding.tvProfileName.text = name
                        } else {
                            binding.tvProfileName.text = "사용자" // 이름 필드가 비어있을 경우 기본값
                        }
                    } else {
                        Log.d("MyPageFragment", "Firestore 문서 없음: 이름 기본값 사용")
                        binding.tvProfileName.text = "사용자"
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("MyPageFragment", "Firestore 이름 가져오기 실패", exception)
                    binding.tvProfileName.text = "사용자 (에러)"
                }
        } else {
            // 로그인이 안 된 경우 (이메일은 이미 위에서 설정됨)
            binding.tvProfileName.text = "Guest"
        }
    }

    // 로그아웃 로직 (앱 꺼짐/재로그인 방지 최종 확인)
    private fun performLogout() {
        auth.signOut() // Firebase 사용자 로그아웃 처리

        Toast.makeText(requireContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()

        // AuthActivity(로그인 화면을 담고 있는 Activity)로 전환
        val intent = Intent(requireContext(), AuthActivity::class.java)

        // 기존의 모든 액티비티 스택을 지우고 AuthActivity를 새롭게 시작
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)

        // 현재 MyPageFragment를 포함하는 Activity를 종료합니다.
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}