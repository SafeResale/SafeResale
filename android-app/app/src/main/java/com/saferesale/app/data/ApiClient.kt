package com.saferesale.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.saferesale.app.BuildConfig
import com.saferesale.app.domain.model.ContactRequest
import com.saferesale.app.domain.model.ListingImageInfo
import com.saferesale.app.domain.model.ListingResponse
import com.saferesale.app.domain.model.ListingsResponse
import com.saferesale.app.domain.model.MarketCategory
import com.saferesale.app.domain.model.ReportRequest
import com.saferesale.app.domain.model.UserProfile
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
data class FirebaseIdTokenReq(val id_token: String)
data class PhoneOtpReq(val phone: String)
data class PhoneVerifyReq(val phone: String, val code: String, val name: String? = null)
data class DraftReq(
    val category: String,
    val title: String,
    val price: Double,
    val brand: String? = null,
    val model: String? = null,
    val storage: String? = null,
    val battery_health: String? = null,
    val condition: String? = null,
    val notes: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
)
data class InspectionReq(val listing_id: String, val preferred_date: String? = null, val note: String? = null)
data class SubmissionReq(
    val score: Double? = null,
    val drawbacks: List<String> = emptyList(),
    val condition: Map<String, String> = emptyMap(),
    val condition_summary: String? = null,
    val submitter: String? = null,
)
data class UploadTokenReq(val angle: String, val filename: String, val content_type: String, val size: Int)
// Concrete body: Retrofit forbids Map<String, Any> (wildcard) as a @Body parameter.
data class ConfirmReq(val upload_token: String, val stored_key: String, val angle: String, val quality: Map<String, Any?>? = null)
data class DiagReportReq(val device: Map<String, Any?>, val category: String, val skipped: Boolean, val tests: List<Map<String, Any?>>)

interface ApiService {
    @POST("auth/login") suspend fun login(@Body b: LoginReq): LoginRes
    @POST("auth/register") suspend fun register(@Body b: Map<String, String>): Map<String, Any>
    @POST("auth/refresh") suspend fun refresh(@Body body: Map<String, String>): LoginRes
    @POST("auth/firebase") suspend fun firebaseLogin(@Body b: FirebaseIdTokenReq): LoginRes
    @POST("auth/phone/otp") suspend fun phoneOtp(@Body b: PhoneOtpReq): Map<String, Any>
    @POST("auth/phone/verify") suspend fun phoneVerify(@Body b: PhoneVerifyReq): LoginRes
    @POST("listings/create-draft") suspend fun createDraft(@Body b: DraftReq, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/upload-token") suspend fun uploadToken(@Path("id") id: String, @Body b: UploadTokenReq, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/confirm-upload") suspend fun confirmUpload(@Path("id") id: String, @Body b: ConfirmReq, @Header("Authorization") auth: String): Map<String, Any>
    @GET("listings/{id}/images") suspend fun listImages(@Path("id") id: String, @Header("Authorization") auth: String): List<Map<String, Any>>
    @GET("listings/{id}/latest-scores") suspend fun latestScores(@Path("id") id: String, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/run-vision") suspend fun runVision(@Path("id") id: String, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/compute-risk") suspend fun computeRisk(@Path("id") id: String, @Header("Authorization") auth: String): Map<String, Any>
    @POST("listings/{id}/run-diagnostics") suspend fun runDiagnostics(@Path("id") id: String, @Body body: DiagReportReq, @Header("Authorization") auth: String): Map<String, Any>
    @GET("listings") suspend fun listings(@Query("category") category: String?, @Query("page") page: Int, @Query("page_size") pageSize: Int, @Header("Authorization") auth: String): ListingsResponse
    @GET("listings/my") suspend fun myListings(@Header("Authorization") auth: String): ListingsResponse
    @GET("listings/categories") suspend fun categories(): List<MarketCategory>
    @GET("listings/{id}") suspend fun listing(@Path("id") id: String, @Header("Authorization") auth: String): ListingResponse
    @POST("listings/{id}/report") suspend fun reportListing(@Path("id") id: String, @Body b: ReportRequest, @Header("Authorization") auth: String): Map<String, Any>
    @POST("contact") suspend fun contact(@Body b: ContactRequest): Map<String, Any>
    @GET("auth/me") suspend fun me(@Header("Authorization") auth: String): UserProfile
    @PATCH("auth/me") suspend fun updateMe(@Body b: Map<String, String>, @Header("Authorization") auth: String): UserProfile
    @POST("inspections") suspend fun createInspection(@Body b: InspectionReq, @Header("Authorization") auth: String): Map<String, Any>
    @GET("inspections") suspend fun listInspections(@Header("Authorization") auth: String): Map<String, Any>
    @PATCH("inspections/{id}") suspend fun patchInspection(@Path("id") id: String, @Body b: Map<String, String>, @Header("Authorization") auth: String): Map<String, Any>
    @GET("p/submit/{token}") suspend fun getSubmission(@Path("token") token: String): Map<String, Any>
    @POST("p/submit/{token}") suspend fun postSubmission(@Path("token") token: String, @Body b: SubmissionReq): Map<String, Any>
    @GET("health") suspend fun health(): Map<String, Any>
}

object ApiClient {
    private const val TAG = "ApiClient"
    private val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    /** Backend root. Real devices reach the dev machine over Wi-Fi (LAN IP);
     * emulators use the host loopback 10.0.2.2. */
    val baseUrl: String = run {
        val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.BRAND.startsWith("generic")
        if (isEmulator) BuildConfig.EMULATOR_API_BASE_URL else BuildConfig.API_BASE_URL
    }

    private var authInterceptor: AuthInterceptor? = null
    private var http: OkHttpClient? = null
    private var _service: ApiService? = null

    /** Called once from MainActivity.onCreate() before any API calls. */
    fun init(appContext: Context) {
        if (_service != null) return
        val interceptor = AuthInterceptor(appContext.applicationContext)
        authInterceptor = interceptor
        http = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor(log)
            .build()
        _service = Retrofit.Builder()
            .baseUrl(baseUrl + "/")
            .client(http!!)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ApiService::class.java)
    }

    /** Register a callback that fires when the refresh token is also invalid. */
    fun onSessionCleared(listener: () -> Unit) {
        authInterceptor?.onSessionCleared = listener
    }

    val service: ApiService
        get() = _service ?: throw IllegalStateException("ApiClient.init() must be called first")

    /** PUT raw bytes to a server-issued upload_url (e.g. "/uploads/{key}"). */
    suspend fun putBytes(uploadUrl: String, bytes: ByteArray, contentType: String, auth: String? = null): Unit = withContext(Dispatchers.IO) {
        val url = if (uploadUrl.startsWith("http")) uploadUrl else baseUrl + uploadUrl
        val builder = Request.Builder().url(url).put(bytes.toRequestBody(contentType.toMediaType()))
        if (auth != null) builder.header("Authorization", auth)
        http!!.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Upload failed: HTTP ${resp.code}")
        }
    }

    /** Reverse-geocode via OSM Nominatim (no API key). Returns a display address. */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&accept-language=en"
        try {
            val req = Request.Builder().url(url).header("User-Agent", "SafeResale/1.0 (android)").build()
            http!!.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                val obj = org.json.JSONObject(text)
                obj.optString("display_name").ifBlank { null }
            }
        } catch (e: Exception) {
            null
        }
    }
}
