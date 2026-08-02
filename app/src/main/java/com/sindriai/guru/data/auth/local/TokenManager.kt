package com.sindriai.guru.data.auth.local

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    // Save token after login
    fun saveToken(token: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    // Get token
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    // Check login status
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // Logout user
    fun clearToken() {
        prefs.edit().clear().apply()
    }
}