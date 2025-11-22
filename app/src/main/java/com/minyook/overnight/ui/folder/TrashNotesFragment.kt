// ui.folder/TrashNotesFragment.kt

package com.minyook.overnight.ui.folder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore // 1. Firestore 임포트
import com.minyook.overnight.R
import java.util.ArrayList

class TrashNotesFragment : Fragment(), TrashOptionsBottomSheet.TrashOptionListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTextView: TextView
    private lateinit var trashAdapter: TrashAdapter
    private var allFolderData: List<FolderItem.Group>? = null

    // 2. Firestore 변수 선언
    private lateinit var db: FirebaseFirestore

    companion object {
        private const val ARG_DATA = "folder_data"

        fun newInstance(data: List<FolderItem.Group>): TrashNotesFragment {
            return TrashNotesFragment().apply {
                arguments = Bundle().apply {
                    // FolderFragment에서 넘겨준 전체 데이터를 받습니다.
                    putSerializable(ARG_DATA, ArrayList(data))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getSerializable(ARG_DATA)?.let {
            @Suppress("UNCHECKED_CAST")
            allFolderData = it as List<FolderItem.Group>
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_trash_notes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 3. Firestore 초기화
        db = FirebaseFirestore.getInstance()

        // 뷰 초기화
        recyclerView = view.findViewById(R.id.recycler_trash_list)
        emptyTextView = view.findViewById(R.id.tv_trash_empty)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar_trash)
        toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        recyclerView.layoutManager = LinearLayoutManager(context)

        displayTrashItems()
    }

    private fun displayTrashItems() {
        // 전체 데이터 중 isDeleted가 true인 항목만 필터링
        val allChildren = allFolderData?.flatMap { it.children } ?: emptyList()
        val trashedItems = allChildren.filter { it.isDeleted }.toMutableList()

        if (trashedItems.isEmpty()) {
            emptyTextView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            trashAdapter = TrashAdapter(trashedItems) { folderTitle ->
                val bottomSheet = TrashOptionsBottomSheet.newInstance(folderTitle)
                bottomSheet.setTargetFragment(this, 0)
                bottomSheet.show(parentFragmentManager, "TrashOptions")
            }
            recyclerView.adapter = trashAdapter
        }
    }

    // 4. 복구 로직 수정 (DB 업데이트 추가)
    override fun onRestore(folderTitle: String) {
        // 1. 해당 폴더의 ID(Firestore Document ID)를 찾습니다.
        val targetItem = allFolderData?.flatMap { it.children }?.find { it.name == folderTitle }

        if (targetItem != null) {
            // 2. Firestore에서 isDeleted를 false로 업데이트
            db.collection("contents").document(targetItem.id)
                .update("isDeleted", false)
                .addOnSuccessListener {
                    // 3. 성공 시 화면에서 제거 및 토스트 메시지
                    trashAdapter.removeItem(folderTitle)

                    // 목록이 비었는지 확인하여 "비었음" 텍스트 표시 처리
                    if (trashAdapter.itemCount == 0) {
                        emptyTextView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }

                    Toast.makeText(context, "'$folderTitle' 복구 완료", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "복구 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "항목을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}