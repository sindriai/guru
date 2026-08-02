package com.sindriai.guru.ui.main.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sindriai.guru.ui.main.MainViewModel

@Composable
fun DownloadGemma3nDialog(
    mainViewModel: MainViewModel,
    onDownloadComplete: () -> Unit
) {
    val state by mainViewModel.uiState.collectAsState()

    // ✅ Toggle for showing/hiding the extra size details
    var showDetails by remember { mutableStateOf(false) }

    // Start immediately when shown (i.e., right after permissions granted)
    LaunchedEffect(Unit) {
        mainViewModel.startModelDownloadIfNeeded()
    }

    // Auto continue when successful
    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onDownloadComplete()
    }

    // If success, don't show dialog
    if (state.isSuccess) return

    val title = if (state.isError) "Download failed" else "Downloading required model"

    AlertDialog(
        onDismissRequest = { /* block dismiss */ },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (state.isError)
                        "We couldn’t download the Gemma model. Please check your connection and try again."
                    else
                        "This one-time download is required to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!state.isError) {
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )

                    Text(
                        text = "${state.percent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ✅ Only show MB/GB details if expanded
                    if (showDetails) {
                        val detailText = if (state.totalBytes > 0) {
                            "${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)}"
                        } else {
                            "${formatBytes(state.bytesDownloaded)} downloaded"
                        }

                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // ✅ Toggle text at bottom
                    TextButton(
                        onClick = { showDetails = !showDetails },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showDetails) "Less details" else "More details")
                    }

                } else {
                    state.errorMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.isError) {
                Button(
                    onClick = { mainViewModel.retry() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry")
                }
            } else {
                Spacer(Modifier.height(1.dp))
            }
        },
        dismissButton = { /* none */ }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"

    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.2f MB", bytes / mb)
        else -> String.format("%.2f KB", bytes / kb)
    }
}