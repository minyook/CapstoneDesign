// 경로: app/src/main/java/com/minyook/overnight.ui.mainscrean/FolderSelectionBottomSheet.kt
package com.minyook.overnight.ui.mainscrean

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.minyook.overnight.R

class FolderSelectionBottomSheet : BottomSheetDialogFragment() {

    // 1. 선택된 경로를 Activity로 전달하기 위한 인터페이스
    interface OnFolderSelectedListener {
        fun onFolderSelected(path: String)
    }

    private var listener: OnFolderSelectedListener? = null

    // 2. [임시 데이터] 실제 폴더 목록 (FolderFragment의 구조를 단순화)
    private val folderPaths = listOf(
        "전체 노트/글로벌",
        "전체 노트/기본 폴더",
        "전체 노트/생활속의통계이해",
        "최근 사용 폴더",
        "새로운 프로젝트 폴더"
    )

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 리스너를 호스트(PresentationInfoActivity)로 설정
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

        // 어댑터 설정
        val adapter = FolderPathAdapter(folderPaths) { path ->
            listener?.onFolderSelected(path)
            dismiss() // 선택 후 팝업 닫기
        }
        recyclerView.adapter = adapter
    }

    // --- 내부 RecyclerView Adapter 클래스 (간소화) ---
    private inner class FolderPathAdapter(
        private val paths: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<FolderPathAdapter.PathViewHolder>() {

        inner class PathViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            // simple_list_item_1 레이아웃의 기본 TextView ID 사용
            val pathTextView: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathViewHolder {
            // 안드로이드 기본 레이아웃을 사용해 단순 목록 형태로 표시
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return PathViewHolder(view)
        }

        override fun onBindViewHolder(holder: PathViewHolder, position: Int) {
            val path = paths[position]
            holder.pathTextView.text = path
            holder.itemView.setOnClickListener { onClick(path) }
        }

        override fun getItemCount() = paths.size
    }

    companion object {
        const val TAG = "FolderSelectionBottomSheet"
    }
}