package com.minyook.overnight.ui.file

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minyook.overnight.databinding.ItemSubjectFolderBinding
import com.minyook.overnight.data.model.SubjectFolder

class SubjectFolderAdapter(
    private val folderList: List<SubjectFolder>,
    private val onItemClick: (SubjectFolder) -> Unit
) : RecyclerView.Adapter<SubjectFolderAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(val binding: ItemSubjectFolderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(folder: SubjectFolder) {
            binding.tvFolderTitle.text = folder.title
            binding.tvFolderDate.text = folder.date
            binding.root.setOnClickListener { onItemClick(folder) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemSubjectFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(folderList[position])
    }

    override fun getItemCount(): Int = folderList.size
}