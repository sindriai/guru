package com.sindriai.guru.data.auth.model

data class ProfileSubmitRequest(
    val name: String,
    val dob: String?,
    val email: String?,
    val gender: String?,
    val city: String?
)