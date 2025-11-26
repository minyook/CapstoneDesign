package com.minyook.overnight.ui.folder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.minyook.overnight.databinding.ItemNoteChildBinding // ★ XML 이름(item_note_child.xml)에 맞춰 바인딩 생성

class ChildNotesFragment : Fragment() {

    private var _binding: ItemNoteChildBinding? = null
    private val binding get() = _binding!!
    private var folderTitle: String? = null

    companion object {
        private const val ARG_TITLE = "folder_title"
        fun newInstance(title: String) = ChildNotesFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_TITLE, title)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            folderTitle = it.getString(ARG_TITLE)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ItemNoteChildBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 제목 설정
        binding.tvChildTitle.text = folderTitle

        // 2. 뒤로가기
        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // 3. FAB (새 노트)
        binding.fabAddNote.setOnClickListener {
            // TODO: 새 노트 로직
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}