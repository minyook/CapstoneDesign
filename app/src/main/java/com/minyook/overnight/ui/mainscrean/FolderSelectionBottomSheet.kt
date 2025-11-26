package com.minyook.overnight.ui.mainscrean

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.R

class FolderSelectionBottomSheet : BottomSheetDialogFragment() {

    private var listener: OnFolderSelectedListener? = null
    private var userUid: String? = null
    private lateinit var db: FirebaseFirestore

    companion object {
        const val TAG = "FolderSelectionBottomSheet"
        private const val ARG_UID = "user_uid"

        fun newInstance(uid: String): FolderSelectionBottomSheet {
            val fragment = FolderSelectionBottomSheet()
            val args = Bundle()
            args.putString(ARG_UID, uid)
            fragment.arguments = args
            return fragment
        }
    }

    interface OnFolderSelectedListener {
        fun onFolderSelected(folderId: String, folderName: String)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFolderSelectedListener) {
            listener = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userUid = arguments?.getString(ARG_UID)
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_folder_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_folder_selection)
        recyclerView.layoutManager = LinearLayoutManager(context)

        if (userUid != null) {
            fetchFolders(recyclerView)
        } else {
            Toast.makeText(context, "유저 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchFolders(recyclerView: RecyclerView) {
        Log.d(TAG, "데이터 로드 시작 - UID: $userUid")

        // 1. 'folders' 컬렉션까지 접근
        // 경로: user -> {userUID} -> folders
        db.collection("user").document(userUid!!).collection("folders")
            .orderBy("created_at")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(context, "생성된 폴더가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    val folders = mutableListOf<Pair<String, String>>()

                    // 2. folders 컬렉션 안의 모든 문서(foldersUID)를 하나씩 꺼냄
                    for (doc in documents) {
                        val name = doc.getString("name") ?: "이름 없음"

                        // ★ 여기가 바로 foldersUID 입니다!
                        // doc.id는 문서의 이름(ID)을 의미합니다.
                        val foldersUID = doc.id

                        // 삭제되지 않은 폴더만 리스트에 추가
                        val isDeleted = doc.getBoolean("isDeleted") ?: false
                        if (!isDeleted) {
                            folders.add(Pair(foldersUID, name))
                        }
                    }

                    // 어댑터 연결
                    recyclerView.adapter = FolderSelectionAdapter(folders) { id, name ->
                        // 클릭 시 부모 액티비티(PresentationInfoActivity)로 foldersUID와 이름을 전달
                        listener?.onFolderSelected(id, name)
                        dismiss()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "데이터 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Firestore Error", e)
            }
    }

    // 리스트 아이템 어댑터
    class FolderSelectionAdapter(
        private val folders: List<Pair<String, String>>,
        private val onClick: (String, String) -> Unit
    ) : RecyclerView.Adapter<FolderSelectionAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            // 아까 만든 item_folder_selection.xml의 TextView ID
            val textView: TextView = view.findViewById(R.id.tv_folder_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // 커스텀 아이템 레이아웃 연결
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_folder_selection, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (id, name) = folders[position]
            holder.textView.text = name
            holder.itemView.setOnClickListener { onClick(id, name) }
        }

        override fun getItemCount() = folders.size
    }
}