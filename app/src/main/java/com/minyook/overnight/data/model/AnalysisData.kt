package com.minyook.overnight.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

// 1. [요청] 서버로 보낼 채점 기준
data class ScoringCriteria(
    val name: String,
    val score: Int,
    val description: String
)

// 2. [응답] 분석 시작
data class AnalysisResponse(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("message") val message: String
)

// 3. [응답] 상태 확인
data class StatusResponse(
    val status: String,
    val message: String?,
    val progress: Int?,
    val total: Int?,
    val result: AnalysisResultData?
)

// 4. [응답] 최종 분석 결과 데이터 (수정됨)
data class AnalysisResultData(
    @SerializedName("ai_assessment") val aiAssessment: AiAssessment?
)

data class AiAssessment(
    // 성공 시 (JSON 모드)
    @SerializedName("reviews") val reviews: List<ReviewItem>?,
    @SerializedName("overall_summary") val overallSummary: String?,
    @SerializedName("video_summary") val videoSummary: String?,

    // 실패 시 또는 텍스트 모드일 경우 (호환성 유지)
    @SerializedName("ai_feedback") val aiFeedback: String?
)

data class ReviewItem(
    @SerializedName("name") val name: String,
    @SerializedName("score") val score: Int,
    @SerializedName("feedback") val feedback: String
)