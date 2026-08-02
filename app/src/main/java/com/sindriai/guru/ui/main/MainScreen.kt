package com.sindriai.guru.ui.main

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sindriai.guru.ui.bookstore.BookStoreActivity
import com.sindriai.guru.ui.learning.LearningScreen
import com.sindriai.guru.ui.learning.LearningViewModel
import com.sindriai.guru.ui.learning.LearningViewModelFactory
import com.sindriai.guru.ui.main.dialogs.DownloadGemma3nDialog
import com.sindriai.guru.ui.main.dialogs.PermissionDialog
import kotlin.jvm.java

@Composable
fun MainScreen(
    activity: ComponentActivity,
    mainViewModel: MainViewModel
) {

    var canEnterLearning by remember { mutableStateOf(false) }

    PermissionDialog {

        // ✅ Compute once after permissions are granted.
        // If already downloaded, immediately enter Learning and NEVER show dialog.
        val alreadyDownloaded = remember {
            mainViewModel.isModelAlreadyDownloaded()
        }

        LaunchedEffect(alreadyDownloaded) {
            if (alreadyDownloaded) {
                canEnterLearning = true
            }
        }

        Log.d("MainScreenFlag","alreadyDownloaded = "+alreadyDownloaded)
        Log.d("MainScreenFlag","canEnterLearning = "+canEnterLearning)

        // ✅ Show download dialog only if model not downloaded yet
        if (!alreadyDownloaded && !canEnterLearning) {
            DownloadGemma3nDialog(
                mainViewModel = mainViewModel,
                onDownloadComplete = { canEnterLearning = true }
            )
        }

        if (canEnterLearning) {

            //We can create LearningViewModel after the gemma3n model is downloaded otherwise it will crash
            val learningFactory = LearningViewModelFactory(activity)
            val learningViewModel: LearningViewModel = viewModel(factory = learningFactory)

            Scaffold(
                modifier = Modifier.fillMaxWidth(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->

                LearningScreen(
                    context = activity,
                    learningViewModel = learningViewModel,
                    modifier = Modifier.padding(innerPadding),

                    // ✅ NEW: this will be called when user taps "Course Store"
                    onOpenCourseStore = {
                        val intent = Intent(activity, BookStoreActivity::class.java)
                        activity.startActivity(intent)
                    }
                )

            }
        }
    }
}
