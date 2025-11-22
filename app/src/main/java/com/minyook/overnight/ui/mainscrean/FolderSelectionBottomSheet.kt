package com.minyook.overnight.ui.mainscrean

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.R

class FolderSelectionBottomSheet : BottomSheetDialogFragment() {

    // 1. 인터페이스 수정 (ID와 이름을 둘 다 전달하도록 변경)
    interface OnFolderSelectedListener {
        fun onFolderSelected(folderId: String, folderName: String)
    }

    private var listener: OnFolderSelectedListener? = null
    private val folderList = mutableListOf<FolderData>() // 실제 데이터를 담을 리스트

    // 간단한 데이터 클래스 (내부 사용용)
    data class FolderData(val id: String, val name: String)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? OnFolderSelectedListener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_folder_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_folder_list)

        // 2. Firestore에서 실제 폴더 목록 가져오기
        fetchFoldersFromFirestore(recyclerView)
    }

    private fun fetchFoldersFromFirestore(recyclerView: RecyclerView) {
        val db = FirebaseFirestore.getInstance()
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 'contents' 컬렉션에서 내(userId)가 만든 폴더 중 삭제되지 않은(isDeleted == false) 것만 조회
        db.collection("contents")
            .whereEqualTo("userId", user.uid)
            .whereEqualTo("isDeleted", false)
            .get()
            .addOnSuccessListener { documents ->
                folderList.clear()
                for (doc in documents) {
                    val name = doc.getString("contentName") ?: "이름 없음"
                    val id = doc.id // 문서 ID
                    folderList.add(FolderData(id, name))
                }

                // 어댑터 연결
                recyclerView.adapter = FolderPathAdapter(folderList) { selectedFolder ->
                    // 선택 시 ID와 이름을 리스너로 전달
                    listener?.onFolderSelected(selectedFolder.id, selectedFolder.name)
                    dismiss()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "폴더 목록을 불러오지 못했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- 내부 Adapter ---
    private inner class FolderPathAdapter(
        private val folders: List<FolderData>,
        private val onClick: (FolderData) -> Unit
    ) : RecyclerView.Adapter<FolderPathAdapter.PathViewHolder>() {

        inner class PathViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val pathTextView: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return PathViewHolder(view)
        }

        override fun onBindViewHolder(holder: PathViewHolder, position: Int) {
            val folder = folders[position]
            holder.pathTextView.text = folder.name // 화면엔 이름만 표시
            holder.itemView.setOnClickListener { onClick(folder) }
        }

        override fun getItemCount() = folders.size
    }

    companion object {
        const val TAG = "FolderSelectionBottomSheet"
    }
}