package com.minyook.overnight.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.databinding.FragmentHomeBinding
import com.minyook.overnight.ui.mainscrean.GuideActivity
import com.minyook.overnight.ui.mainscrean.PresentationInfoActivity
import com.minyook.overnight.ui.mainscrean.ScriptChatActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 파이어베이스 인증 & 데이터베이스 객체 초기화
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 시스템 UI 숨기기 (전체화면 모드)
        hideSystemUI()

        // 2. 버튼 클릭 리스너 설정
        setupListeners()

        // 3. 사용자 이름 불러오기 (Firestore)
        fetchUserName()
    }

    // 화면이 다시 보일 때마다 전체화면 모드 재적용
    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    // --- [기능 1] 사용자 이름 가져오기 ---
    private fun fetchUserName() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val uid = currentUser.uid // 현재 로그인한 사용자의 UID

            // Firestore 경로: 컬렉션("user") -> 문서(UID) -> 필드("name")
            db.collection("user").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name")

                        // 이름이 있으면 UI 업데이트, 없으면 기본값 유지
                        if (!name.isNullOrEmpty()) {
                            // XML에 tv_hello_user ID가 있어야 합니다.
                            binding.tvHelloUser.text = "Hello, $name!"
                        }
                    } else {
                        Log.d("HomeFragment", "해당 유저의 문서가 존재하지 않습니다.")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeFragment", "데이터 가져오기 실패", exception)
                    // 실패 시 기본 텍스트 유지
                    binding.tvHelloUser.text = "Hello, Presenter!"
                }
        } else {
            // 로그인이 안 된 경우
            binding.tvHelloUser.text = "Hello, Guest!"
        }
    }

    // --- [기능 2] 내비게이션 바 숨기기 ---
    private fun hideSystemUI() {
        val window = requireActivity().window
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // 제스처로 스와이프하면 잠깐 나왔다가 다시 사라지게 설정
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 상태바와 내비게이션바 모두 숨기기
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    // --- [기능 3] 버튼 클릭 이벤트 ---
    private fun setupListeners() {
        // 메인 카드(보라색) 클릭 -> 발표 정보 입력 화면 이동
        binding.cardMainAction.setOnClickListener {
            val intent = Intent(requireContext(), PresentationInfoActivity::class.java)
            startActivity(intent)
        }

        // Script, Guide 카드 (필요 시 구현)
        binding.cardScript.setOnClickListener {
            //
            val intent = Intent(requireContext(), ScriptChatActivity::class.java)
            startActivity(intent)
        }
        binding.cardGuide.setOnClickListener {
            // TODO: 가이드 화면 이동
            val intent = Intent(requireContext(), GuideActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}