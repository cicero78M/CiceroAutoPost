package com.cicero.igtools

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

object TiktokSessionManager {
    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            "tiktok",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun saveProfile(context: Context, data: JSONObject) {
        prefs(context).edit { putString("profile", data.toString()) }
    }

    fun loadProfile(context: Context): JSONObject? {
        val str = prefs(context).getString("profile", null) ?: return null
        return try { JSONObject(str) } catch (_: Exception) { null }
    }

    fun saveCookies(context: Context, cookies: String) {
        prefs(context).edit { putString("cookies", cookies) }
    }

    fun loadCookies(context: Context): String? {
        return prefs(context).getString("cookies", null)
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }
}
