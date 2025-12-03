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
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.databinding.FragmentMypageBinding
import com.minyook.overnight.ui.file.SubjectFolderActivity
import com.minyook.overnight.ui.FirstScrean.AuthActivity

class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    // ViewBinding 사용
    private val binding get() = _binding!!

    // Firebase 인스턴스
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMypageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 사용자 프로필(이름, 이메일) 불러오기
        loadUserProfileFromFirestore()

        // 2. 통계 데이터(과목 수, 발표 횟수) 계산해서 불러오기
        loadUserStatistics()

        // 3. [전체 노트 보기] 클릭 시 이동
        binding.cardAllNotes.setOnClickListener {
            val intent = Intent(requireContext(), SubjectFolderActivity::class.java)
            startActivity(intent)
        }

        // 4. [로그아웃] 버튼 클릭
        binding.btnLogout.setOnClickListener {
            performLogout()
        }
    }

    // Firestore에서 사용자 이름 가져오기
    private fun loadUserProfileFromFirestore() {
        val user = auth.currentUser

        // 이메일 설정
        binding.tvProfileEmail.text = user?.email ?: "로그인 필요"

        if (user != null) {
            val uid = user.uid

            db.collection("user").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val name = document.getString("name")
                        binding.tvProfileName.text = name ?: "사용자"
                    } else {
                        binding.tvProfileName.text = "사용자"
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("MyPageFragment", "Firestore 이름 가져오기 실패", exception)
                }
        } else {
            binding.tvProfileName.text = "Guest"
        }
    }

    // Firestore에서 폴더 개수와 발표 주제(Topic) 개수를 세는 함수
    private fun loadUserStatistics() {
        val user = auth.currentUser ?: return
        val uid = user.uid

        // 1. 등록된 과목(폴더) 개수 세기
        db.collection("user").document(uid).collection("folders")
            .get()
            .addOnSuccessListener { folderSnapshot ->

                // 폴더 개수 표시 (예: "3개")
                val folderCount = folderSnapshot.size()
                binding.tvSubjectCount.text = "${folderCount}개"

                // 폴더가 하나도 없으면 0회 처리 후 종료
                if (folderCount == 0) {
                    binding.tvPresentationCount.text = "0회"
                    binding.tvExcellentCount.text = "0회"
                    return@addOnSuccessListener
                }

                // 2. 총 발표 횟수 계산 (모든 폴더의 하위 topic을 더함)
                var totalTopics = 0
                var processedFolders = 0 // 비동기 처리를 위한 카운터

                for (folderDoc in folderSnapshot.documents) {
                    // 각 폴더 안의 topics 컬렉션 조회
                    folderDoc.reference.collection("topics")
                        .get()
                        .addOnSuccessListener { topicSnapshot ->
                            // 해당 폴더의 발표 개수를 누적
                            totalTopics += topicSnapshot.size()

                            // 모든 폴더를 다 확인했는지 체크
                            processedFolders++
                            if (processedFolders == folderCount) {
                                // 최종적으로 UI 업데이트
                                binding.tvPresentationCount.text = "${totalTopics}회"

                                // (참고) 우수 발표 로직은 추후 점수 데이터가 생기면 추가 가능
                                // 현재는 0회로 고정
                                binding.tvExcellentCount.text = "0회"
                            }
                        }
                        .addOnFailureListener {
                            // 실패하더라도 카운트는 올려야 다른 폴더 집계가 멈추지 않음
                            processedFolders++
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MyPageFragment", "통계 데이터 로드 실패", e)
            }
    }

    // 로그아웃 처리
    private fun performLogout() {
        auth.signOut()
        Toast.makeText(requireContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}