package com.sindriai.guru.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Parent config store with grouped/nested classes.
 * Uses EncryptedSharedPreferences for secure persistence.
 */

private const val KEY_GEMMA_MODEL_PATH = "gemma_model_path"

class AppConfig(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Expose grouped configs
    val user = UserDetails()
    val subscription = Subscription()
    val settings = AppSettings()
    val links = Links()

    /* -------------------- Group: User Details -------------------- */
    inner class UserDetails {

        var userId: String?
            get() = prefs.getString(KEY_USER_ID, null)
            set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

        var username: String?
            get() = prefs.getString(KEY_USERNAME, null)
            set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

        var fullName: String?
            get() = prefs.getString(KEY_FULL_NAME, null)
            set(value) = prefs.edit().putString(KEY_FULL_NAME, value).apply()

        var email: String?
            get() = prefs.getString(KEY_EMAIL, null)
            set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

        var phone: String?
            get() = prefs.getString(KEY_PHONE, null)
            set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

        fun isLoggedIn(): Boolean = !userId.isNullOrBlank()

        fun clear() {
            prefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .remove(KEY_FULL_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_PHONE)
                .apply()
        }
    }

    /* -------------------- Group: Subscription -------------------- */
    inner class Subscription {

        var planId: String?
            get() = prefs.getString(KEY_PLAN_ID, null)
            set(value) = prefs.edit().putString(KEY_PLAN_ID, value).apply()

        /**
         * e.g. "ACTIVE", "EXPIRED", "CANCELLED", "TRIAL"
         */
        var status: String?
            get() = prefs.getString(KEY_PLAN_STATUS, null)
            set(value) = prefs.edit().putString(KEY_PLAN_STATUS, value).apply()

        /**
         * Purchase token (store only if truly needed).
         */
        var purchaseToken: String?
            get() = prefs.getString(KEY_PURCHASE_TOKEN, null)
            set(value) = prefs.edit().putString(KEY_PURCHASE_TOKEN, value).apply()

        var expiryTimeMillis: Long
            get() = prefs.getLong(KEY_PLAN_EXPIRY, 0L)
            set(value) = prefs.edit().putLong(KEY_PLAN_EXPIRY, value).apply()

        fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
            val st = status?.uppercase()
            if (st != "ACTIVE" && st != "TRIAL") return false
            val exp = expiryTimeMillis
            return exp <= 0L || exp > nowMillis
        }

        fun clear() {
            prefs.edit()
                .remove(KEY_PLAN_ID)
                .remove(KEY_PLAN_STATUS)
                .remove(KEY_PURCHASE_TOKEN)
                .remove(KEY_PLAN_EXPIRY)
                .apply()
        }
    }

    /* -------------------- Group: App Settings -------------------- */
    inner class AppSettings {

        var gemmaModelPath: String?
            get() = prefs.getString(KEY_GEMMA_MODEL_PATH, null)
            set(value) = prefs.edit().putString(KEY_GEMMA_MODEL_PATH, value).apply()

        var preferredLanguage: String
            get() = prefs.getString(KEY_LANG, "en") ?: "en"
            set(value) = prefs.edit().putString(KEY_LANG, value).apply()

        var isDarkMode: Boolean
            get() = prefs.getBoolean(KEY_DARK_MODE, false)
            set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

        var isTtsEnabled: Boolean
            get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
            set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

        var fontScale: Float
            get() = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
            set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()

        fun clear() {
            prefs.edit()
                .remove(KEY_LANG)
                .remove(KEY_DARK_MODE)
                .remove(KEY_TTS_ENABLED)
                .remove(KEY_FONT_SCALE)
                .apply()
        }
    }

    /* -------------------- Group: Links / URLs -------------------- */
    inner class Links {

        var apiBaseUrl: String?
            get() = prefs.getString(KEY_API_BASE_URL, null)
            set(value) = prefs.edit().putString(KEY_API_BASE_URL, value).apply()

        var privacyPolicyUrl: String?
            get() = prefs.getString(KEY_PRIVACY_URL, null)
            set(value) = prefs.edit().putString(KEY_PRIVACY_URL, value).apply()

        var termsUrl: String?
            get() = prefs.getString(KEY_TERMS_URL, null)
            set(value) = prefs.edit().putString(KEY_TERMS_URL, value).apply()

        var helpUrl: String?
            get() = prefs.getString(KEY_HELP_URL, null)
            set(value) = prefs.edit().putString(KEY_HELP_URL, value).apply()

        fun clear() {
            prefs.edit()
                .remove(KEY_API_BASE_URL)
                .remove(KEY_PRIVACY_URL)
                .remove(KEY_TERMS_URL)
                .remove(KEY_HELP_URL)
                .apply()
        }
    }

    /* -------------------- Parent-level Helpers -------------------- */

    fun clearUserAndSubscription() {
        user.clear()
        subscription.clear()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "secure_user_prefs"

        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHONE = "phone"

        private const val KEY_PLAN_ID = "plan_id"
        private const val KEY_PLAN_STATUS = "plan_status"
        private const val KEY_PURCHASE_TOKEN = "purchase_token"
        private const val KEY_PLAN_EXPIRY = "plan_expiry"

        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_PRIVACY_URL = "privacy_url"
        private const val KEY_TERMS_URL = "terms_url"
        private const val KEY_HELP_URL = "help_url"

        private const val KEY_LANG = "preferred_language"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_FONT_SCALE = "font_scale"
    }
}
