package com.minyook.overnight.ui.folder

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.R
import com.minyook.overnight.databinding.FragmentTrashNotesBinding
// item_folder_child.xml을 사용하는 바인딩 (없으면 R.layout.item_folder_child 사용)
import com.minyook.overnight.databinding.ItemFolderChildBinding

class TrashNotesFragment : Fragment() {

    private var _binding: FragmentTrashNotesBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var trashAdapter: TrashAdapter
    private val trashList = ArrayList<FolderItem.Child>()

    companion object {
        fun newInstance() = TrashNotesFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrashNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        fetchTrashItems()
    }

    private fun setupToolbar() {
        binding.toolbarTrash.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    // --- [1] 데이터 불러오기 (휴지통만) ---
    private fun fetchTrashItems() {
        val uid = auth.currentUser?.uid ?: return

        // Firestore에서 삭제된(isDeleted == true) 폴더만 가져오기
        db.collection("user").document(uid).collection("folders")
            .whereEqualTo("isDeleted", true)
            .get()
            .addOnSuccessListener { documents ->
                trashList.clear()
                for (document in documents) {
                    val name = document.getString("name") ?: ""
                    val id = document.id

                    // 휴지통 아이템 객체 생성
                    val item = FolderItem.Child(
                        parentId = "trash",
                        id = id,
                        name = name,
                        isDeleted = true
                    )
                    trashList.add(item)
                }

                // 목록 비었는지 체크
                if (trashList.isEmpty()) {
                    binding.tvTrashEmpty.visibility = View.VISIBLE
                    binding.recyclerTrashList.visibility = View.GONE
                } else {
                    binding.tvTrashEmpty.visibility = View.GONE
                    binding.recyclerTrashList.visibility = View.VISIBLE
                    trashAdapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "데이터 로드 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerView() {
        // 어댑터 생성 (클릭 시 바텀 시트 호출)
        trashAdapter = TrashAdapter(trashList) { item ->
            showTrashOptionsBottomSheet(item)
        }

        binding.recyclerTrashList.layoutManager = LinearLayoutManager(context)
        binding.recyclerTrashList.adapter = trashAdapter
    }

    // --- [2] 바텀 시트 옵션 메뉴 ---
    private fun showTrashOptionsBottomSheet(item: FolderItem.Child) {
        // 1. 바텀 시트 레이아웃 inflate
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_trash_options, null)

        // 2. 다이얼로그 생성
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(sheetView)

        // 3. 배경 투명 처리 (XML의 둥근 모서리 적용을 위해 필수)
        try {
            val parentLayout = sheetView.parent as View
            parentLayout.setBackgroundColor(Color.TRANSPARENT)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // --- 클릭 리스너 설정 ---

        // [복구하기] 버튼
        sheetView.findViewById<View>(R.id.option_restore).setOnClickListener {
            bottomSheetDialog.dismiss()
            restoreFolder(item)
        }

        // [영구 삭제] 버튼
        sheetView.findViewById<View>(R.id.option_delete_permanently).setOnClickListener {
            bottomSheetDialog.dismiss()
            showDeleteConfirmDialog(item) // 확인 팝업 호출
        }

        bottomSheetDialog.show()
    }

    // --- [3] 기능 로직 ---

    // 영구 삭제 전 확인 팝업
    private fun showDeleteConfirmDialog(item: FolderItem.Child) {
        AlertDialog.Builder(requireContext())
            .setTitle("영구 삭제")
            .setMessage("'${item.name}'을(를) 정말로 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                deletePermanently(item)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 복구 로직 (isDeleted -> false)
    private fun restoreFolder(item: FolderItem.Child) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("user").document(uid).collection("folders").document(item.id)
            .update("isDeleted", false)
            .addOnSuccessListener {
                Toast.makeText(context, "복구되었습니다.", Toast.LENGTH_SHORT).show()
                fetchTrashItems() // 목록 갱신
            }
    }

    // 영구 삭제 로직 (DB에서 제거)
    private fun deletePermanently(item: FolderItem.Child) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("user").document(uid).collection("folders").document(item.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "영구 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                fetchTrashItems() // 목록 갱신
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- [4] 내부 어댑터 (휴지통 전용) ---
    inner class TrashAdapter(
        private val items: List<FolderItem.Child>,
        private val onClick: (FolderItem.Child) -> Unit
    ) : RecyclerView.Adapter<TrashAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemFolderChildBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFolderChildBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            // 텍스트 설정
            holder.binding.tvChildTitle.text = item.name

            // 아이콘을 휴지통 모양으로 변경 (시각적 구분)
            holder.binding.ivChildIcon.setImageResource(R.drawable.ic_delete)
            holder.binding.ivChildIcon.setColorFilter(Color.parseColor("#EF4444")) // 빨간색 틴트

            // 클릭 시 바텀 시트 호출
            holder.binding.root.setOnClickListener { onClick(item) }

            // 옵션 버튼(점3개)은 숨김 (항목 자체를 누르게 유도)
            // 만약 점3개 버튼으로만 메뉴를 열고 싶다면 onClick을 여기에 연결하고 root 리스너를 제거하세요.
            holder.binding.btnChildOptions.visibility = View.GONE
        }

        override fun getItemCount() = items.size
    }
}