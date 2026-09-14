package com.saferesale.app.data

import com.saferesale.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class LoginReq(val email: String, val password: String)
data class LoginRes(val access_token: String, val refresh_token: String, val user: Map<String, Any>)
data class DraftReq(val category: String, val title: String, val price: Double)
data class UploadTokenReq(val angle: String, val filename: String, val content_type: String, val size: Int)
// Concrete body: Retrofit forbids Map<String, Any> (wildcard) as a @Body parameter.
data class ConfirmReq(val upload_token: String, val stored_key: String, val angle: String, val quality: Map<String, Any?>? = null)
data class DiagReportReq(val device: Map<String, Any?>, val category: String, val skipped: Boolean, val tests: List<Map<String, Any?>>)

interface ApiService {
    @POST("auth/login") suspend fun login(@Body b: LoginReq): LoginRes
    @POST("auth/register") suspend fun register(@Body b: Map<String, String>): Map<String, Any>
    @POST("listings/create-draft") suspend fun createDraft(@Body b: DraftReq, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/upload-token") suspend fun uploadToken(@Path("id") id: String, @Body b: UploadTokenReq, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/confirm-upload") suspend fun confirmUpload(@Path("id") id: String, @Body b: ConfirmReq, @Header("Authorization") auth: String): Map<String, Any>
    @GET("listings/{id}/images") suspend fun listImages(@Path("id") id: String, @Header("Authorization") auth: String): List<Map<String, Any>>
    @GET("listings/{id}/latest-scores") suspend fun latestScores(@Path("id") id: String, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/run-vision") suspend fun runVision(@Path("id") id: String, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/compute-risk") suspend fun computeRisk(@Path("id") id: String, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/run-diagnostics") suspend fun runDiagnostics(@Path("id") id: String, @Body body: DiagReportReq, @Header("Authorization") auth: String): Map<String, Any>
    @GET("health") suspend fun health(): Map<String, Any>
}

object ApiClient {
    private val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    private val http = OkHttpClient.Builder().addInterceptor(log).build()

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
    }

    /** PUT raw bytes to a server-issued upload_url (e.g. "/uploads/{key}"). */
    suspend fun putBytes(uploadUrl: String, bytes: ByteArray, contentType: String, auth: String? = null): Unit = withContext(Dispatchers.IO) {
        val url = if (uploadUrl.startsWith("http")) uploadUrl else BuildConfig.API_BASE_URL + uploadUrl
        val builder = Request.Builder().url(url).put(bytes.toRequestBody(contentType.toMediaType()))
        if (auth != null) builder.header("Authorization", auth)
        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Upload failed: HTTP ${resp.code}")
        }
    }
}
