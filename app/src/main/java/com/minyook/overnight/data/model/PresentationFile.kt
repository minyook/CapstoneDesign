package com.minyook.overnight.data.model

import java.io.Serializable

data class PresentationFile(
    val id: String = "",       // Firestore 문서 ID (Presentation ID)
    val topicId: String = "",  // 상위 Topic 문서 ID (경로 찾기 필수)
    val title: String = "",    // 화면에 표시할 제목 (주제 / 팀명)
    val date: String = "",     // 생성 날짜
    val score: Int = 0,        // 총점
    val summary: String = ""   // 요약
) : Serializable