package com.sindriai.guru.data.auth.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.sindriai.guru.BuildConfig

object RetrofitClient {

    private const val BASE_URL = BuildConfig.BASE_URL

    val apiService: AuthApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}