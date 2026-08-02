package com.sindriai.guru.data.auth.model

data class SendOtpResponse(
    val success: Boolean,
    val message: String,
    val otp:String?
)