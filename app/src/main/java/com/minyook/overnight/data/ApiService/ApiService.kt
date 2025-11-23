package com.minyook.overnight.data.network

import com.minyook.overnight.data.model.AnalysisResponse
import com.minyook.overnight.data.model.StatusResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    // 1. 분석 요청 (파일 업로드 + 채점 기준 JSON)
    @Multipart
    @POST("analyze")
    fun analyzeVideo(
        @Part file: MultipartBody.Part,      // 영상 파일
        @Part("criteria") criteria: RequestBody // 채점 기준 (JSON 문자열)
    ): Call<AnalysisResponse>

    // 2. 상태 확인 (Polling)
    @GET("status/{job_id}")
    fun checkStatus(
        @Path("job_id") jobId: String
    ): Call<StatusResponse>
}