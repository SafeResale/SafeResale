package com.saferesale.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenStore {
    private const val PREF = "saferesale_tokens"
    fun save(ctx: Context, access: String, refresh: String) {
        prefs(ctx).edit().putString("access", access).putString("refresh", refresh).apply()
    }
    fun access(ctx: Context): String? = prefs(ctx).getString("access", null)
    fun refresh(ctx: Context): String? = prefs(ctx).getString("refresh", null)
    fun clear(ctx: Context) = prefs(ctx).edit().clear().apply()
    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx, PREF, MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
