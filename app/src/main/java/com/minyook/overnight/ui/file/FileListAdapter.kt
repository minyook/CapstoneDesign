package com.minyook.overnight.ui.file

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minyook.overnight.R
import com.minyook.overnight.databinding.ItemFileListBinding
import com.minyook.overnight.data.model.PresentationFile

class FileListAdapter(
    private val fileList: List<PresentationFile>,
    private val onItemClick: (PresentationFile) -> Unit
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    inner class FileViewHolder(val binding: ItemFileListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: PresentationFile) {
            binding.tvFileName.text = file.title
            binding.tvFileInfo.text = "${file.date} | ${file.score}점"
            binding.ivFileIcon.setImageResource(R.drawable.ic_file) // ic_file 없으면 ic_folder 사용
            binding.root.setOnClickListener { onItemClick(file) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(fileList[position])
    }

    override fun getItemCount(): Int = fileList.size
}