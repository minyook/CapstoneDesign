package com.minyook.overnight.data.model

import java.io.Serializable

// 개별 발표 기준 항목의 결과를 담는 데이터 클래스
data class CriterionResult(
    val criterionName: String,  // 발표 기준명 (예: "논리성")
    val maxScore: Int,          // 기준의 만점 점수 (예: 20)
    val actualScore: Int,       // AI가 평가한 실제 점수 (예: 18)
    val feedback: String        // 해당 기준에 대한 상세 피드백
) : Serializable

// 전체 분석 결과를 담는 데이터 클래스 (Gemini API 응답 구조)
data class AnalysisResult(
    val totalMaxScore: Int,
    val totalActualScore: Int,
    val results: List<CriterionResult>
) : Serializable