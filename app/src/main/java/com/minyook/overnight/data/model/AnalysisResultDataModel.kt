package com.minyook.overnight.data.model

import java.io.Serializable

// 전체 분석 결과를 담는 데이터 클래스 (Gemini API 응답 구조)
data class AnalysisResult(
    val totalMaxScore: Int,
    val totalActualScore: Int,
    val results: List<CriterionResult>
) : Serializable