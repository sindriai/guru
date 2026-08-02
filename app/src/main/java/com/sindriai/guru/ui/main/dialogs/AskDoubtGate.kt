package com.sindriai.guru.ui.main.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sindriai.guru.ui.main.MainViewModel

/**
 * Gate for any entry point that needs the AI pipeline ready before showing
 * its content: runtime permissions granted AND the Gemma model downloaded.
 *
 * This introduces no new permission or download logic. It only sequences
 * the two gates that already exist in the app:
 *
 *   PermissionDialog { ... }               <- reused as-is
 *       -> model already downloaded?
 *            yes -> onReady()
 *            no  -> DownloadGemma3nDialog { onDownloadComplete = { onReady() } }  <- reused as-is
 *
 * Because PermissionDialog only renders `content()` once permissions are
 * actually granted (it shows its own AlertDialog otherwise), and
 * DownloadGemma3nDialog only calls `onDownloadComplete` once
 * `uiState.isSuccess` is true (it shows its own progress/error AlertDialog
 * otherwise), this composable never needs to know *how* permissions are
 * requested or *how* the model is downloaded -- it just reacts to the two
 * outcomes.
 *
 * Safe to mount even if permissions/model are already satisfied elsewhere
 * in the app (e.g. today, MainScreen already gates the whole Learning
 * flow on both) -- in that case PermissionDialog and the download check
 * both resolve instantly and `onReady()` is called with no dialog ever
 * shown. This is what makes the Ask Doubts flow self-sufficient: it does
 * not depend on MainScreen's current app-wide gating and will keep
 * working correctly if that gating is ever relaxed in the future.
 */
@Composable
fun AskDoubtGate(
    mainViewModel: MainViewModel,
    onReady: @Composable () -> Unit
) {
    PermissionDialog {
        // Computed once per time this gate enters composition (once per
        // "Ask Doubts" tap, since TopicScreen only mounts AskDoubtGate
        // after that tap) -- not on every recomposition, so a completed
        // download can never get "re-checked" back to false.
        var modelReady by remember { mutableStateOf(mainViewModel.isModelAlreadyDownloaded()) }

        if (!modelReady) {
            DownloadGemma3nDialog(
                mainViewModel = mainViewModel,
                onDownloadComplete = { modelReady = true }
            )
        }

        if (modelReady) {
            onReady()
        }
    }
}