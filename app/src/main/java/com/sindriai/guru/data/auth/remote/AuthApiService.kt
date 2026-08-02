package com.sindriai.guru.data.auth.remote

import com.sindriai.guru.data.auth.model.GetProfileResponse
import com.sindriai.guru.data.auth.model.ProfileSubmitRequest
import com.sindriai.guru.data.auth.model.SendOtpRequest
import com.sindriai.guru.data.auth.model.SendOtpResponse
import com.sindriai.guru.data.auth.model.VerifyOtpRequest
import com.sindriai.guru.data.auth.model.VerifyOtpResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApiService {

    @POST("send-otp")
    suspend fun sendOtp(
        @Body request: SendOtpRequest
    ): SendOtpResponse

    @POST("verify-otp")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest
    ): Response<VerifyOtpResponse>

    @GET("profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<GetProfileResponse>

    @PUT("profile")
    suspend fun submitProfile(
        @Header("Authorization") token: String,
        @Body request: ProfileSubmitRequest
    ): Response<GetProfileResponse>


}