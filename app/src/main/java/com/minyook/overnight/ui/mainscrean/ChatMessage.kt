package com.minyook.overnight.ui.mainscrean

data class ChatMessage(
    val message: String,
    val isUser: Boolean // true면 사용자(나), false면 AI
)