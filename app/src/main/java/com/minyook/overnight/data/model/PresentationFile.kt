package com.minyook.overnight.data.model

import java.io.Serializable

data class PresentationFile(
    val id: String = "",       // Firestore 문서 ID
    val title: String = "",    // 파일 이름 (fileName)
    val date: String = "",     // 생성 날짜 (createdAt)
    val score: Int = 0,        // 점수 (score)
    val summary: String = ""   // 요약 (summary)
) : Serializable