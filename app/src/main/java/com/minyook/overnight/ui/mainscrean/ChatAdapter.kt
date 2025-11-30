package com.minyook.overnight.ui.mainscrean

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.minyook.overnight.databinding.ItemChatMessageBinding // 패키지명 확인 필요

class ChatAdapter(private val messageList: ArrayList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chatMessage: ChatMessage) {
            if (chatMessage.isUser) {
                // [사용자 메시지] : AI 숨김, 유저 보임
                binding.layoutAiMessage.visibility = View.GONE
                binding.tvUserText.visibility = View.VISIBLE
                binding.tvUserText.text = chatMessage.message
            } else {
                // [AI 메시지] : 유저 숨김, AI 보임
                binding.tvUserText.visibility = View.GONE
                binding.layoutAiMessage.visibility = View.VISIBLE
                binding.tvAiText.text = chatMessage.message
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messageList[position])
    }

    override fun getItemCount(): Int = messageList.size

    // 메시지 추가용 함수
    fun addMessage(message: ChatMessage) {
        messageList.add(message)
        notifyItemInserted(messageList.size - 1)
    }
}