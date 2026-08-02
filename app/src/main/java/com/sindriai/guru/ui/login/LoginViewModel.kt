package com.sindriai.guru.ui.login

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sindriai.guru.data.auth.local.TokenManager
import com.sindriai.guru.data.auth.model.ProfileSubmitRequest
import com.sindriai.guru.data.auth.model.SendOtpRequest
import com.sindriai.guru.data.auth.model.VerifyOtpRequest
import com.sindriai.guru.data.auth.remote.RetrofitClient
import kotlinx.coroutines.launch

enum class LoginStep {
    PHONE,
    OTP,
    PROFILE
}

class LoginViewModel(context: Context) : ViewModel() {

    private val tokenManager = TokenManager(context)

    val phoneNumber = MutableLiveData("")
    val otp = MutableLiveData("")
    val isLoading = MutableLiveData(false)
    val message = MutableLiveData("")
    val currentStep = MutableLiveData(LoginStep.PHONE)

    private val _navigateToMain = MutableLiveData(false)
    val navigateToMain: LiveData<Boolean> = _navigateToMain

    // Optional testing value if backend returns otp
    private var backendOtp: String? = null

    fun onPhoneNumberChange(value: String) {
        phoneNumber.value = value
    }

    fun onOtpChange(value: String) {
        otp.value = value
    }

    fun sendOtp() {
        val mobile = phoneNumber.value.orEmpty().trim()

        if (mobile.length < 10) {
            message.value = "Please enter a valid mobile number"
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            message.value = ""

            try {
                val response = RetrofitClient.apiService.sendOtp(
                    SendOtpRequest(mobile)
                )

                if (response.success) {
                    backendOtp = response.otp
                    message.value = response.message
                    currentStep.value = LoginStep.OTP

                    Log.d("SendOtp", "OTP sent successfully. Debug OTP: $backendOtp")
                } else {
                    message.value = response.message
                }

            } catch (e: Exception) {
                message.value = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            }

            isLoading.value = false
        }
    }

    fun verifyOtp() {
        val mobile = phoneNumber.value.orEmpty().trim()
        val enteredOtp = otp.value.orEmpty().trim()

        if (enteredOtp.length != 6) {
            message.value = "Please enter a valid 6-digit OTP"
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            message.value = ""

            try {
                val response = RetrofitClient.apiService.verifyOtp(
                    VerifyOtpRequest(
                        mobile = mobile,
                        otp = enteredOtp
                    )
                )

                if (response.isSuccessful) {
                    response.body()?.token?.let { token ->
                        tokenManager.saveToken(token)
                        Log.d("TokenSaved", "Here is your token: ${tokenManager.getToken()}")
                    }

                    message.value = "Login successful"
                    currentStep.value = LoginStep.PROFILE
                } else {
                    message.value = response.message().ifBlank { "Wrong OTP" }
                }

            } catch (e: Exception) {
                message.value = "Network error: ${e.localizedMessage ?: "Unknown error"}"
            }

            isLoading.value = false
        }
    }

    fun getSavedToken(): String? {
        return tokenManager.getToken()
    }

    fun getProfile(token: String) {
        viewModelScope.launch {
            isLoading.value = true
            message.value = ""

            try {
                val response = RetrofitClient.apiService.getProfile("Bearer $token")

                if (response.isSuccessful) {
                    val profileResponse = response.body()
                    message.value = "Welcome ${profileResponse?.data?.user?.name}"
                } else {
                    message.value = response.message()
                }

            } catch (e: Exception) {
                message.value = "Profile error: ${e.localizedMessage ?: "Unknown error"}"
            }

            isLoading.value = false
        }
    }

    fun submitProfile(token:String,
        name: String,
        dob: String,
        email: String,
        gender: String,
        city: String
    ) {
        viewModelScope.launch {
            isLoading.value = true


            try {
                val response = RetrofitClient.apiService.submitProfile(
                    "Bearer $token", ProfileSubmitRequest(name, dob, email, gender, city)
                )

                if (response.isSuccessful) {
                    message.value = "Success"
                    _navigateToMain.value = true
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("API_ERROR", errorBody ?: "Unknown error")

                    message.value = errorBody ?: "Validation failed"
                }

            } catch (e: Exception) {
                message.value = e.message
            }finally {
                isLoading.value = false
            }
        }
    }
}