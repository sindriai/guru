package com.sindriai.guru.data.auth.model

data class VerifyOtpResponse(
    val success: Boolean,
    val message: String,
    val is_new_user: Boolean,
    val token: String? = null,   // backend may return token after login
    val user: User? = null      // backend may return user details after login
)

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val mobile: String,
    val mobile_verified: Boolean
)