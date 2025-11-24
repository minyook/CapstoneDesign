package com.minyook.overnight.ui.folder

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog // ★ 바텀 시트 임포트
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.minyook.overnight.R
import com.minyook.overnight.databinding.DialogAddChildBinding
import com.minyook.overnight.databinding.FragmentFolderBinding
import com.minyook.overnight.ui.file.SubjectFolderActivity
import com.minyook.overnight.ui.mainscrean.PresentationInfoActivity
import com.minyook.overnight.ui.folder.ChildNotesFragment

class FolderFragment : Fragment() {

    private var _binding: FragmentFolderBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // 어댑터 및 데이터
    private lateinit var folderAdapter: FolderExpandableAdapter
    private var groups = mutableListOf<FolderItem.Group>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideSystemUI()
        initGroupData()
        setupRecyclerView()
        fetchFolders()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    // --- [1] 초기 데이터 구조 ---
    private fun initGroupData() {
        groups.clear()
        groups.add(FolderItem.Group(name = "전체 노트", isExpanded = true, children = mutableListOf()))
        groups.add(FolderItem.Group(name = "휴지통", children = mutableListOf()))
    }

    // --- [2] 리사이클러뷰 설정 ---
    private fun setupRecyclerView() {
        folderAdapter = FolderExpandableAdapter(
            data = groups,
            onAddClicked = { groupName ->
                showAddFolderDialog()
            },
            onChildClicked = { child ->
                // ⭐️ 여기가 핵심! 클릭된 폴더의 ID와 이름을 가지고 이동합니다.
                val intent = Intent(requireContext(), SubjectFolderActivity::class.java)
                intent.putExtra("folderId", child.id)     // 폴더 ID
                intent.putExtra("folderName", child.name) // 폴더 이름
                startActivity(intent)
            },
            onTrashClicked = {
                // 휴지통 클릭 -> TrashNotesFragment 이동
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, TrashNotesFragment.newInstance())
                    .addToBackStack(null)
                    .commit()
            },
            onChildOptionsClicked = { view, childName ->
                // ★ [수정됨] 옵션 클릭 -> 바텀 시트 호출
                showChildOptionsBottomSheet(childName)
            }
        )

        binding.recyclerFolderList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = folderAdapter
            itemAnimator = null
        }
    }

    // --- [3] 하위 항목 옵션 메뉴 (Bottom Sheet 적용) ---
    private fun showChildOptionsBottomSheet(childName: String) {
        // 1. 대상 데이터 찾기
        val allNotesGroup = groups.find { it.name == "전체 노트" } ?: return
        val targetChild = allNotesGroup.children.find { it.name == childName } ?: return

        // 2. BottomSheetDialog 생성
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        // dialog_folder_options.xml 레이아웃 인플레이트
        val sheetView = layoutInflater.inflate(R.layout.dialog_folder_options, null)
        bottomSheetDialog.setContentView(sheetView)

        // 3. 배경 투명 처리 (XML의 둥근 모서리가 보이도록 설정)
        try {
            val parentLayout = sheetView.parent as View
            parentLayout.setBackgroundColor(Color.TRANSPARENT)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. 버튼 클릭 리스너 설정
        // [이름 변경] 클릭
        sheetView.findViewById<View>(R.id.option_rename).setOnClickListener {
            bottomSheetDialog.dismiss() // 시트 닫기
            showEditDialog(targetChild) // 이름 변경 다이얼로그 호출
        }

        // [삭제] 클릭
        sheetView.findViewById<View>(R.id.option_delete).setOnClickListener {
            bottomSheetDialog.dismiss() // 시트 닫기
            deleteChildFolder(targetChild) // 삭제 로직 실행
        }

        // 5. 표시
        bottomSheetDialog.show()
    }

    // --- [4] 폴더 이름 수정 다이얼로그 ---
    private fun showEditDialog(child: FolderItem.Child) {
        val dialogBinding = DialogAddChildBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.etNewChildName.setText(child.name)
        dialogBinding.btnCreate.text = "수정"

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCreate.setOnClickListener {
            val newName = dialogBinding.etNewChildName.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateChildFolder(child, newName)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    // --- [5] Firestore 로직 ---
    private fun updateChildFolder(child: FolderItem.Child, newName: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("user").document(uid).collection("folders").document(child.id)
            .update("name", newName)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "수정되었습니다.", Toast.LENGTH_SHORT).show()
                fetchFolders()
            }
    }

    private fun deleteChildFolder(child: FolderItem.Child) {
        val uid = auth.currentUser?.uid ?: return
        // 휴지통 이동 (isDeleted = true)
        db.collection("user").document(uid).collection("folders").document(child.id)
            .update("isDeleted", true)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "휴지통으로 이동했습니다.", Toast.LENGTH_SHORT).show()
                fetchFolders()
            }
    }

    private fun fetchFolders() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("user").document(uid).collection("folders")
            .orderBy("created_at")
            .get()
            .addOnSuccessListener { documents ->
                val allNotesGroup = groups.find { it.name == "전체 노트" }
                if (allNotesGroup != null) {
                    allNotesGroup.children.clear()
                    for (document in documents) {
                        val name = document.getString("name") ?: "이름 없음"
                        val id = document.id
                        val isDeleted = document.getBoolean("isDeleted") ?: false

                        if (!isDeleted) {
                            val child = FolderItem.Child(allNotesGroup.id, id, name, false)
                            allNotesGroup.children.add(child)
                        }
                    }
                    folderAdapter.notifyDataChanged()
                }
            }
    }

    private fun createNewFolder(folderName: String) {
        val uid = auth.currentUser?.uid ?: return
        val folderData = hashMapOf(
            "name" to folderName,
            "created_at" to System.currentTimeMillis(),
            "isDeleted" to false
        )
        db.collection("user").document(uid).collection("folders")
            .add(folderData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "폴더 생성 완료", Toast.LENGTH_SHORT).show()
                fetchFolders()
            }
    }

    // --- [6] FAB & 팝업 ---
    private fun setupListeners() {
        binding.fabAddFolder.setOnClickListener { view ->
            showAddOptionsMenu(view)
        }
    }

    private fun showAddOptionsMenu(anchorView: View) {
        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.popup_add_options, null)

        val popupWindow = PopupWindow(
            popupView,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 20f

        popupView.findViewById<View>(R.id.btn_upload_file).setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(requireContext(), PresentationInfoActivity::class.java)
            startActivity(intent)
        }
        popupView.findViewById<View>(R.id.btn_add_folder_popup).setOnClickListener {
            popupWindow.dismiss()
            showAddFolderDialog()
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val xOff = -(popupView.measuredWidth - anchorView.width)
        val yOff = -(popupView.measuredHeight + anchorView.height + 30)
        popupWindow.showAsDropDown(anchorView, xOff, yOff)
    }

    private fun showAddFolderDialog() {
        val dialogBinding = DialogAddChildBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCreate.setOnClickListener {
            val folderName = dialogBinding.etNewChildName.text.toString().trim()
            if (folderName.isNotEmpty()) {
                createNewFolder(folderName)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "폴더 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun hideSystemUI() {
        val window = requireActivity().window
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}