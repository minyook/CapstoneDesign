package com.minyook.overnight.data.network

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // ⚠️ [중요] 에뮬레이터라면 "http://10.0.2.2:8000/"
    // ⚠️ [중요] 실제 폰이라면 "http://192.168.X.X:8000/" (컴퓨터 내부 IP)
    // 사용자님이 "127.0.0.1"이라고 하셨지만 안드로이드에서는 아래 주소를 써야 합니다.
    private const val BASE_URL = "http://10.0.2.2:8000/"
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // 통신 로그 자세히 보기
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS) // 연결 타임아웃
            .readTimeout(60, TimeUnit.SECONDS)    // 읽기 타임아웃
            .writeTimeout(60, TimeUnit.SECONDS)   // 쓰기 타임아웃
            .build()
    }

    val instance: ApiService by lazy {
        val gson = GsonBuilder()
            .setLenient()
            .create()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}