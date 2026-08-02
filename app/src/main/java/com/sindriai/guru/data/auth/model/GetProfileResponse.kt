package com.sindriai.guru.data.auth.model

data class GetProfileResponse(
    val success: Boolean,
    val data: ProfileData?,
    val message: String?,
    val error: String?
)

data class ProfileData(
    val user: UserData,
    val student: Student,
    val wallet: Wallet,
)

data class Wallet(
    val balance: Double
)

data class UserData(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String,
    val status: Boolean
)

data class Student(
    val id: Int,
    val student_id: Int?,
    val date_of_birth: String?,
    val gender: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val pincode: String?,
    val subscription_status: Boolean?,
    val subscription_plan: String?,
    val subscription_start_date: String?,
    val subscription_end_date: String?,
)


