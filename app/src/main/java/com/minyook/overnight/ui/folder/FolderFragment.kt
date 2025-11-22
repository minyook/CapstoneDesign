package com.minyook.overnight.ui.folder

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.minyook.overnight.R
import com.minyook.overnight.ui.mainscrean.PresentationInfoActivity
import java.util.ArrayList

class FolderFragment : Fragment(), AddChildDialogFragment.ChildCreationListener,
    FolderOptionsBottomSheet.FolderOptionListener, RenameFolderDialogFragment.RenameListener {

    private lateinit var folderAdapter: FolderExpandableAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddFolder: FloatingActionButton

    // Firebase 관련 변수
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var firestoreListener: ListenerRegistration? = null

    // 데이터 리스트
    private var folderGroupsData: MutableList<FolderItem.Group> = mutableListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_folder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Firebase 초기화
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 2. 뷰 바인딩
        recyclerView = view.findViewById(R.id.recycler_folder_list)
        fabAddFolder = view.findViewById(R.id.fab_add_folder)

        // 3. 어댑터 초기화 (빈 리스트로 시작)
        folderAdapter = FolderExpandableAdapter(
            data = folderGroupsData,
            onAddClicked = ::showAddChildDialog,
            onChildClicked = ::navigateToChildNotes,
            onTrashClicked = ::navigateToTrashList,
            onChildOptionsClicked = ::showChildOptionsBottomSheet
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = folderAdapter

        // 4. 하단 FAB 리스너 (팝업)
        fabAddFolder.setOnClickListener { anchorView ->
            showAddOptionsPopup(anchorView)
        }

        // 5. Firestore 데이터 리스너 연결
        setupFirestoreListener()
    }

    /**
     * Firestore의 'contents' 컬렉션을 실시간으로 구독합니다.
     */
    private fun setupFirestoreListener() {
        val user = auth.currentUser
        if (user == null) return

        // 쿼리: 내(userId)가 만든 contents만 가져오기
        val query = db.collection("contents")
            .whereEqualTo("userId", user.uid)
        // .orderBy("createdAt") // 필요 시 주석 해제 (인덱스 설정 필요할 수 있음)

        firestoreListener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("FolderFragment", "Listen failed.", e)
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val activeChildren = mutableListOf<FolderItem.Child>()
                val trashChildren = mutableListOf<FolderItem.Child>()

                // 문서를 하나씩 꺼내서 객체로 변환
                for (doc in snapshots) {
                    val contentName = doc.getString("contentName") ?: "이름 없음"
                    val isDeleted = doc.getBoolean("isDeleted") ?: false
                    val docId = doc.id // Firestore 문서 ID

                    // FolderItem.Child 생성 (id에 Firestore 문서 ID 저장)
                    val childItem = FolderItem.Child(
                        parentId = "G1", // 부모 그룹 ID (임의 고정)
                        id = docId,      // ⭐ 중요: 나중에 이 ID로 수정/삭제함
                        name = contentName,
                        isDeleted = isDeleted
                    )

                    if (isDeleted) {
                        trashChildren.add(childItem)
                    } else {
                        activeChildren.add(childItem)
                    }
                }

                updateLocalData(activeChildren, trashChildren)
            }
        }
    }

    /**
     * Firestore에서 받은 데이터를 로컬 리스트 구조에 맞춰 재배치하고 UI 갱신
     */
    private fun updateLocalData(active: MutableList<FolderItem.Child>, trash: MutableList<FolderItem.Child>) {
        folderGroupsData.clear()

        // 1. 전체 노트 그룹
        val allNotesGroup = FolderItem.Group(
            id = "G1",
            name = "전체 노트",
            isExpanded = true, // 기본 펼침
            children = active
        )

        // 2. 휴지통 그룹
        val trashGroup = FolderItem.Group(
            id = "G4",
            name = "휴지통",
            children = trash
        )

        folderGroupsData.add(allNotesGroup)
        folderGroupsData.add(trashGroup)

        // 어댑터에 변경 알림
        folderAdapter.notifyDataChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 리스너 해제 (메모리 누수 방지)
        firestoreListener?.remove()
    }

    // -----------------------------------
    // 기능 구현 (Firestore 연동)
    // -----------------------------------

    // 1. 폴더 추가 (Firestore에 add)
    override fun onChildCreated(groupName: String, childName: String) {
        val user = auth.currentUser ?: return

        // Firestore 'contents' 컬렉션 구조에 맞게 데이터 생성
        val newContent = hashMapOf(
            "userId" to user.uid,
            "contentName" to childName,
            "totalTopics" to 0,
            "totalPresentations" to 0,
            "isDeleted" to false
            // "createdAt" to com.google.firebase.Timestamp.now() // 필요하면 추가
        )

        db.collection("contents")
            .add(newContent)
            .addOnSuccessListener {
                Toast.makeText(context, "'$childName' 폴더 생성 완료", Toast.LENGTH_SHORT).show()
                // 리스너가 자동으로 UI 업데이트하므로 여기서 adapter.notify 할 필요 없음
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "폴더 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 2. 폴더 삭제 (휴지통으로 이동 -> isDeleted = true)
    override fun onFolderDeleted(folderTitle: String) {
        // 이름으로 ID를 찾아야 함 (Firestore ID가 필요하므로)
        val targetChild = findChildByName(folderTitle)

        if (targetChild != null) {
            db.collection("contents").document(targetChild.id)
                .update("isDeleted", true)
                .addOnSuccessListener {
                    Toast.makeText(context, "휴지통으로 이동했습니다.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // 3. 폴더 이름 변경
    override fun onFolderRenamed(oldTitle: String, newTitle: String) {
        val targetChild = findChildByName(oldTitle)

        if (targetChild != null) {
            db.collection("contents").document(targetChild.id)
                .update("contentName", newTitle)
                .addOnSuccessListener {
                    Toast.makeText(context, "이름이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // 헬퍼 함수: 이름으로 Child 객체 찾기 (ID를 알아내기 위함)
    private fun findChildByName(name: String): FolderItem.Child? {
        folderGroupsData.forEach { group ->
            val child = group.children.find { it.name == name }
            if (child != null) return child
        }
        return null
    }

    // -----------------------------------
    // 기존 UI 로직 유지
    // -----------------------------------

    // '전체 노트' 옆의 + 버튼 클릭 시
    private fun showAddChildDialog(groupName: String) {
        val dialog = AddChildDialogFragment.newInstance(groupName)
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, "AddChildDialog")
    }

    // 자식 항목 클릭 시 이동
    private fun navigateToChildNotes(folderTitle: String) {
        val fragment = ChildNotesFragment.newInstance(folderTitle)

        val containerId = (view?.parent as? ViewGroup)?.id ?: R.id.fragment_container
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    // 휴지통 클릭 시 이동
    private fun navigateToTrashList() {
        val dataToSend = ArrayList(folderGroupsData)
        val fragment = TrashNotesFragment.newInstance(dataToSend)
        val containerId = (view?.parent as? ViewGroup)?.id ?: R.id.fragment_container

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack(null)
            .commit()
    }

    // 자식 항목 옵션(점 세개) 클릭 시
    private fun showChildOptionsBottomSheet(anchorView: View, folderTitle: String) {
        val bottomSheet = FolderOptionsBottomSheet.newInstance(folderTitle)
        bottomSheet.setTargetFragment(this, 0)
        bottomSheet.show(parentFragmentManager, "ChildOptions")
    }

    // FolderOptionsBottomSheet에서 이름 변경 선택 시
    override fun onFolderRenamed(folderTitle: String) {
        val dialog = RenameFolderDialogFragment.newInstance(folderTitle)
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, "RenameDialog")
    }

    // 우측 하단 FAB 팝업
    private fun showAddOptionsPopup(anchorView: View) {
        val inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_add_options, null)
        val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)

        popupWindow.setBackgroundDrawable(BitmapDrawable())
        popupWindow.isOutsideTouchable = true

        popupView.findViewById<LinearLayout>(R.id.option_record).setOnClickListener {
            Toast.makeText(requireContext(), "녹화 기능 실행 (구현 필요)", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        popupView.findViewById<LinearLayout>(R.id.option_file_upload).setOnClickListener {
            // PresentationInfoActivity로 이동 (패키지명 주의)
            val intent = Intent(requireContext(), PresentationInfoActivity::class.java)
            startActivity(intent)
            popupWindow.dismiss()
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val yOffset = - (anchorView.height + popupView.measuredHeight + 16)
        popupWindow.showAsDropDown(anchorView, 0, yOffset)
    }
}