package com.sindriai.guru.ui.splash

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun isUserLoggedIn(): Boolean {
        return prefs.getBoolean("is_logged_in", false)
    }

}