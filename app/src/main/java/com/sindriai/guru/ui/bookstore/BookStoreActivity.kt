package com.sindriai.guru.ui.bookstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class BookStoreActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BookStoreScreen(
                onBack = { finish() },
                onOpenCourse = { courseId ->
                    // handle open course if needed
                    finish()
                }
            )
        }
    }

}