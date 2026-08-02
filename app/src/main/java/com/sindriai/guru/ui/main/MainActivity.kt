package com.sindriai.guru.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sindriai.guru.ui.theme.GuruTheme
import com.sindriai.guru.BuildConfig

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val token = BuildConfig.RKHuggingFaceToken

        val mainFactory = MainViewModelFactory(
                application,
            token
            )
        setContent {

            val mainViewModel: MainViewModel = viewModel(factory = mainFactory)

            GuruTheme {
                MainScreen(
                    activity = this,
                    mainViewModel = mainViewModel
                )
            }

        }

    }
}
