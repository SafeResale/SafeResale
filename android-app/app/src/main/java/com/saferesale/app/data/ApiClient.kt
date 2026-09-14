package com.saferesale.app.data

import com.saferesale.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class LoginReq(val email: String, val password: String)
data class LoginRes(val access_token: String, val refresh_token: String, val user: Map<String, Any>)
data class DraftReq(val category: String, val title: String, val price: Double)
data class UploadTokenReq(val angle: String, val filename: String, val content_type: String, val size: Int)

interface ApiService {
    @POST("auth/login") suspend fun login(@Body b: LoginReq): LoginRes
    @POST("auth/register") suspend fun register(@Body b: Map<String, String>): Map<String, Any>
    @POST("listings/create-draft") suspend fun createDraft(@Body b: DraftReq, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/upload-token") suspend fun uploadToken(@Path("id") id: String, @Body b: UploadTokenReq, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/run-vision") suspend fun runVision(@Path("id") id: String, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/compute-risk") suspend fun computeRisk(@Path("id") id: String, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/run-diagnostics") suspend fun runDiagnostics(@Path("id") id: String, @Body body: Map<String, Any>, @Header("Authorization") auth: String): Map<String, Any>
    @GET("health") suspend fun health(): Map<String, Any>
}

object ApiClient {
    val service: ApiService by lazy {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(log).build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
    }
}
