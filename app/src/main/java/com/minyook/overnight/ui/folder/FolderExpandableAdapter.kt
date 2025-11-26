// ui.folder/FolderExpandableAdapter.kt

package com.minyook.overnight.ui.folder

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minyook.overnight.R

class FolderExpandableAdapter(
    // [데이터 저장소] Fragment에서 전달된 원본 데이터
    private val data: MutableList<FolderItem.Group>,
    // [콜백] 동적 추가 버튼
    private val onAddClicked: (groupName: String) -> Unit,
    // [콜백] 하위 항목 클릭
    private val onChildClicked: (child: FolderItem.Child) -> Unit,
    // [콜백] 휴지통 클릭
    private val onTrashClicked: () -> Unit,
    // [콜백] 하위 폴더 옵션 버튼 클릭
    private val onChildOptionsClicked: (view: View, childName: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_GROUP = 1
    private val VIEW_TYPE_CHILD = 2

    // 표시될 실제 목록 (초기 호출 시 data를 인자로 전달)
    private val displayList: MutableList<FolderItem> = calculateDisplayList(data) // ⭐ 수정 완료

    // -----------------------------------
    // I. 데이터 관리 및 갱신
    // -----------------------------------

    /**
     * Group Data를 기반으로 펼쳐진 Child 항목을 포함한 최종 표시 목록을 계산합니다.
     */
    private fun calculateDisplayList(groups: List<FolderItem.Group>): MutableList<FolderItem> {
        val list = mutableListOf<FolderItem>()
        groups.forEach { group ->
            list.add(group) // 1. 그룹 헤더를 추가

            // 2. 펼쳐진 그룹의 하위 항목을 추가합니다.
            if (group.isExpanded && group.name != "휴지통") {
                // ⭐ [핵심 수정] Children 목록을 추가하기 전에 isDeleted가 false인 항목만 필터링합니다.
                val activeChildren = group.children.filter { !it.isDeleted }
                list.addAll(activeChildren) // 필터링된 항목들만 추가
            }
        }
        return list
    }

    /**
     * Fragment에서 데이터 변경 후 호출됩니다. (삭제/이름 변경 후)
     */
    @SuppressLint("NotifyDataSetChanged")
    fun notifyDataChanged() {
        // 원본 데이터(data)가 Fragment에 의해 변경되었으므로, displayList를 다시 계산합니다.
        displayList.clear()
        displayList.addAll(calculateDisplayList(data))
        notifyDataSetChanged()
    }

    /**
     * 새 하위 항목을 그룹에 추가하고 목록을 업데이트합니다.
     */
    fun addChildToGroup(groupName: String, childName: String) {
        val targetGroup = data.find { it.name == groupName }

        if (targetGroup != null) {
            val newChild = FolderItem.Child(parentId = targetGroup.id, name = childName)
            targetGroup.children.add(0, newChild)

            // 데이터 변경 후 전체 갱신 요청
            if (targetGroup.isExpanded) {
                notifyDataChanged()
            }
        }
    }

    // -----------------------------------
    // II. RecyclerView 필수 구현 (생략)
    // -----------------------------------

    override fun getItemViewType(position: Int): Int {
        return when (displayList[position]) {
            is FolderItem.Group -> VIEW_TYPE_GROUP
            is FolderItem.Child -> VIEW_TYPE_CHILD
        }
    }

    override fun getItemCount(): Int = displayList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GROUP) {
            val view = inflater.inflate(R.layout.item_folder_group, parent, false)
            GroupViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_folder_child, parent, false)
            ChildViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {
            is FolderItem.Group -> (holder as GroupViewHolder).bind(item)
            is FolderItem.Child -> (holder as ChildViewHolder).bind(item)
        }
    }

    // -----------------------------------
    // III. ViewHolders
    // -----------------------------------

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_group_title)
        private val toggleIcon: ImageView = itemView.findViewById(R.id.iv_expand_toggle)
        private val groupIcon: ImageView = itemView.findViewById(R.id.iv_group_icon)

        // ⭐ XML에 추가한 버튼 ID 연결
        private val addButton: ImageButton = itemView.findViewById(R.id.btn_add_child)

        fun bind(group: FolderItem.Group) {
            // 1. 그룹 이름 설정
            title.text = group.name

            // 2. 휴지통 vs 전체 노트 분기 처리
            if (group.name == "휴지통") {
                // [휴지통]
                groupIcon.setImageResource(R.drawable.ic_delete)
                toggleIcon.visibility = View.INVISIBLE // 접기 화살표 숨김
                addButton.visibility = View.GONE       // + 버튼 숨김

                itemView.setOnClickListener {
                    onTrashClicked() // 휴지통 클릭 시 이동
                }
            } else {
                // [전체 노트] (일반 그룹)
                groupIcon.setImageResource(R.drawable.ic_folder)
                toggleIcon.visibility = View.VISIBLE

                // ⭐ [핵심] "전체 노트"일 때만 + 버튼 보이기 & 클릭 리스너 연결
                if (group.name == "전체 노트") {
                    addButton.visibility = View.VISIBLE
                    addButton.setOnClickListener {
                        // + 버튼 클릭 시 -> FolderFragment의 다이얼로그 호출
                        onAddClicked(group.name)
                    }
                } else {
                    addButton.visibility = View.GONE
                }

                // 접기/펴기 아이콘 회전
                val rotation = if (group.isExpanded) 0f else 270f
                toggleIcon.rotation = rotation

                // 그룹(카드) 전체 클릭 시 -> 접기/펴기 수행
                itemView.setOnClickListener {
                    toggleGroupExpansion(group)
                }
            }
        }
    }

    inner class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_child_title)
        private val optionsButton: ImageButton = itemView.findViewById(R.id.btn_child_options)

        fun bind(child: FolderItem.Child) {
            title.text = child.name

            // 1. 하위 항목 클릭 시 이동
            itemView.setOnClickListener {
                onChildClicked(child)
            }

            // 2. 옵션 버튼 클릭 시 BottomSheet 띄우기 요청
            optionsButton.setOnClickListener {
                onChildOptionsClicked(it, child.name)
            }
        }
    }

    // -----------------------------------
    // IV. 핵심 로직: 접기/펴기
    // -----------------------------------

    private fun toggleGroupExpansion(group: FolderItem.Group) {
        group.isExpanded = !group.isExpanded
        notifyDataChanged() // 데이터 변경 후 갱신
    }
}