package com.sindriai.guru.ui.main

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sindriai.guru.ui.bookstore.BookStoreActivity
import com.sindriai.guru.ui.learning.LearningScreen
import com.sindriai.guru.ui.learning.LearningViewModel
import com.sindriai.guru.ui.learning.LearningViewModelFactory
import com.sindriai.guru.ui.main.dialogs.PermissionDialog
import kotlin.jvm.java

@Composable
fun MainScreen(
    activity: ComponentActivity,
    mainViewModel: MainViewModel
) {

    PermissionDialog {

        val learningFactory = LearningViewModelFactory(activity)
        val learningViewModel: LearningViewModel = viewModel(factory = learningFactory)

        Scaffold(
            modifier = Modifier.fillMaxWidth(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->

            LearningScreen(
                context = activity,
                learningViewModel = learningViewModel,
                mainViewModel = mainViewModel,
                modifier = Modifier.padding(innerPadding),
                onOpenCourseStore = {
                    val intent = Intent(activity, BookStoreActivity::class.java)
                    activity.startActivity(intent)
                }
            )

        }
    }
}
