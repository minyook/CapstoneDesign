package com.minyook.overnight.data.model

data class SubjectFolder(
    val id: String = "",       // Firestore 문서 ID
    val title: String = "",    // 폴더 이름 (contentName)
    val date: String = ""      // 생성 날짜 (createdAt)
)