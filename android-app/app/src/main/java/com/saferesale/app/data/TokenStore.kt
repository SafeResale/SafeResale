package com.saferesale.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenStore {
    private const val PREF = "saferesale_tokens"
    private const val META_PREF = "saferesale_meta"  // non-sensitive metadata

    fun save(ctx: Context, access: String, refresh: String) {
        prefs(ctx).edit().putString("access", access).putString("refresh", refresh).apply()
    }
    fun access(ctx: Context): String? = prefs(ctx).getString("access", null)
    fun refresh(ctx: Context): String? = prefs(ctx).getString("refresh", null)
    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        metaPrefs(ctx).edit().clear().apply()
    }

    /** Returns true if the user has completed (or skipped) the seller KYC flow. */
    fun kycDone(ctx: Context): Boolean = metaPrefs(ctx).getBoolean("kyc_done", false)

    /** Call this once the user successfully submits the KYC form. */
    fun setKycDone(ctx: Context, verifiedName: String? = null) {
        val editor = metaPrefs(ctx).edit().putBoolean("kyc_done", true)
        if (!verifiedName.isNullOrBlank()) {
            editor.putString("kyc_verified_name", verifiedName.trim())
        }
        editor.apply()
    }

    /** Returns verified KYC name cached locally. */
    fun getKycName(ctx: Context): String? = metaPrefs(ctx).getString("kyc_verified_name", null)

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx, PREF, MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun metaPrefs(ctx: Context) =
        ctx.getSharedPreferences(META_PREF, Context.MODE_PRIVATE)
}

