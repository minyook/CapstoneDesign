package com.minyook.overnight.ui.mypage

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.minyook.overnight.databinding.FragmentMypageBinding
import com.minyook.overnight.ui.file.SubjectFolderActivity

class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMypageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. [전체 노트 보기] -> SubjectFolderActivity 이동
        binding.cardAllNotes.setOnClickListener {
            val intent = Intent(requireContext(), SubjectFolderActivity::class.java)
            startActivity(intent)
        }

        // 2. [로그아웃] 버튼 클릭
        binding.btnLogout.setOnClickListener {
            Toast.makeText(requireContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
            // 로그아웃 로직 추가 (예: Firebase.auth.signOut())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}