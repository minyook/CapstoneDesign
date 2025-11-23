package com.minyook.overnight.data.model

import java.io.Serializable

data class CriterionResult(
    val criterionName: String,  // 기준명 (예: 발음 정확도)
    val maxScore: Int,          // 만점
    val actualScore: Int,       // 획득 점수
    val feedback: String        // 피드백
) : Serializable