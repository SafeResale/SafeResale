package com.saferesale.app.data

import android.content.Context
import android.util.Base64
import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OkHttp interceptor that attaches the Bearer token and transparently refreshes
 * it on 401 responses. Uses a synchronous HTTP call inside the interceptor
 * (OkHttp requires interceptors to be synchronous).
 *
 * Skips auth for public endpoints (login, register, refresh, phone OTP, etc.).
 */
class AuthInterceptor(private val appContext: Context) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
        private val PUBLIC_PATHS = listOf(
            "auth/login", "auth/register", "auth/refresh",
            "auth/phone", "auth/firebase", "health",
        )
        private val isRefreshing = AtomicBoolean(false)
    }

    /** Called when the refresh token is also invalid — navigate to login. */
    var onSessionCleared: (() -> Unit)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.encodedPath

        // Skip auth for public endpoints
        if (PUBLIC_PATHS.any { url.contains(it) }) {
            return chain.proceed(original)
        }

        val accessToken = TokenStore.access(appContext)
        if (accessToken == null) {
            onSessionCleared?.invoke()
            return chain.proceed(original)
        }

        // Attach access token
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

        val response = chain.proceed(authed)

        if (response.code != 401) return response

        // 401 received — try to refresh
        response.close()

        if (isRefreshing.compareAndSet(false, true)) {
            try {
                val refreshed = doRefresh()
                if (refreshed) {
                    val newToken = TokenStore.access(appContext) ?: return chain.proceed(original)
                    val retried = original.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return chain.proceed(retried)
                }
            } finally {
                isRefreshing.set(false)
            }
        } else {
            // Another thread is refreshing — wait briefly then retry with the updated token
            Thread.sleep(300)
            val newToken = TokenStore.access(appContext)
            if (newToken != null && newToken != accessToken) {
                val retried = original.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(retried)
            }
        }

        // Refresh failed — clear session
        TokenStore.clear(appContext)
        onSessionCleared?.invoke()
        return chain.proceed(original)
    }

    /**
     * Synchronous refresh call using a plain OkHttpClient (no interceptors to avoid recursion).
     */
    private fun doRefresh(): Boolean {
        val rt = TokenStore.refresh(appContext) ?: return false
        return try {
            val client = OkHttpClient.Builder().build()
            val body = JSONObject().put("refresh_token", rt).toString()
            val req = Request.Builder()
                .url("${ApiClient.baseUrl}/auth/refresh")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val json = resp.body?.string() ?: return false
                val obj = JSONObject(json)
                val newAccess = obj.optString("access_token", null) ?: return false
                val newRefresh = obj.optString("refresh_token", null) ?: rt
                TokenStore.save(appContext, newAccess, newRefresh)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed", e)
            false
        }
    }
}
