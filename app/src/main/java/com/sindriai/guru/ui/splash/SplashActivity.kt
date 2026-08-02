package com.sindriai.guru.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.sindriai.guru.data.auth.local.TokenManager
import com.sindriai.guru.ui.main.MainActivity
import com.sindriai.guru.ui.login.LoginActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {

    private val viewModel: SplashViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenManager = TokenManager(this@SplashActivity)
        lifecycleScope.launch {
            delay(1500) // Splash delay (1.5 sec)

            val isLoggedIn = tokenManager.isLoggedIn()
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))

            if (isLoggedIn) {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }

            finish()
        }
    }
}