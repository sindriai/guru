package com.sindriai.guru.data.auth.model

data class VerifyOtpRequest(
    val mobile: String,
    val otp: String
)