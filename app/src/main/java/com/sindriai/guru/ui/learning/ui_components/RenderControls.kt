package com.sindriai.guru.ui.learning.ui_components

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sindriai.guru.R
import com.sindriai.guru.data.tts.TextReader

// ─────────────────────────────────────────────────────────────────────────────
// ReaderCoordinator — enforces at-most-one-speaking rule across all messages.
//
// Create once at ChatScreen level:
//   val readerCoordinator = remember { ReaderCoordinator() }
// ─────────────────────────────────────────────────────────────────────────────

class ReaderCoordinator {

    private var pauseActive: (() -> Unit)? = null

    /**
     * Ask to become the active reader.
     * The previously active reader (if different) is paused automatically.
     */
    fun requestPlay(pauseMe: () -> Unit) {
        if (pauseActive !== pauseMe) {
            pauseActive?.invoke()
        }
        pauseActive = pauseMe
    }

    /** Notify the coordinator this reader stopped on its own. */
    fun relinquish(pauseMe: () -> Unit) {
        if (pauseActive === pauseMe) {
            pauseActive = null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ReaderControls
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Self-contained TTS control row for a single AI message.
 *
 * Renders:  ▶/⏸   ⏮   ↺
 *
 * @param text                The plain text to be read aloud.
 * @param messageId           Stable unique ID for this message.
 * @param coordinator         Shared [ReaderCoordinator] — enforces single-speaker.
 * @param onHighlightChanged  Called whenever the highlighted char range changes.
 *                            null  → no highlight (reset / completed / disposed).
 *                            Pause keeps the LAST highlight; this callback is NOT
 *                            called on pause.
 */
@Composable
fun ReaderControls(
    text: String,
    messageId: String,
    coordinator: ReaderCoordinator,
    onHighlightChanged: (IntRange?) -> Unit = {}
) {
    val context: Context = LocalContext.current

    // ── UI state ──────────────────────────────────────────────────────────
    var isReading  by remember { mutableStateOf(false) }
    var isTtsReady by remember { mutableStateOf(false) }

    // Stable mutable ref so the TextReader (created once) always calls the
    // *current* onHighlightChanged lambda even after recompositions.
    val onHighlightRef = remember { mutableStateOf<(IntRange?) -> Unit>({}) }
    onHighlightRef.value = onHighlightChanged

    // ── TextReader — one instance per message, survives recompositions ────
    val reader = remember(messageId) {
        TextReader(
            context = context,
            onWordSpoken = { wordInfo ->
                // Route through the ref so we always hit the current callback,
                // not the stale one captured at construction time.
                onHighlightRef.value(wordInfo.startChar..wordInfo.endChar)
            },
            onReadingCompleted = { _ ->
                isReading = false
                onHighlightRef.value(null)   // ← clear on natural completion
            },
            onTtsReady = { ready ->
                isTtsReady = ready
            },
            onError = { _ ->
                isReading = false
                // Leave highlight unchanged on error — matches pause semantics.
            }
        )
    }

    // Stable identity so the coordinator can compare by reference (===).
    val pauseThisReader: () -> Unit = remember(reader) {
        {
            if (isReading) {
                reader.pause()
                isReading = false
                // Intentionally do NOT clear highlight — pause keeps last word lit.
            }
        }
    }

    // Feed text once TTS is ready (or when text changes while idle).
    LaunchedEffect(isTtsReady, text) {
        if (isTtsReady) {
            reader.setText(text)
        }
    }

    // Clean up when the composable leaves composition.
    DisposableEffect(reader) {
        onDispose {
            coordinator.relinquish(pauseThisReader)
            onHighlightRef.value(null)   // ← clear highlight when item leaves the list
            reader.close()
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────

    val onPlayPause: () -> Unit = {
        if (isReading) {
            reader.pause()
            isReading = false
            coordinator.relinquish(pauseThisReader)
            // highlight intentionally NOT cleared — spec: pause keeps last word
        } else {
            coordinator.requestPlay(pauseThisReader)
            reader.startAndResume()
            isReading = true
        }
    }

    val onReset: () -> Unit = {
        reader.reset()
        isReading = false
        coordinator.relinquish(pauseThisReader)
        onHighlightRef.value(null)   // ← clear immediately on reset
    }

    val onPreviousSentence: () -> Unit = {
        // If currently reading, pause first so readAgainSentence() gets PAUSED state.
        if (isReading) {
            reader.pause()
            isReading = false
            coordinator.relinquish(pauseThisReader)
            // highlight stays on last spoken word — spec: prev-sentence doesn't move it
        }
        reader.readAgainSentence()
        // Playback does NOT resume automatically; user must press Play.
    }

    // ── UI ────────────────────────────────────────────────────────────────

    Row(verticalAlignment = Alignment.CenterVertically) {

        // ▶ / ⏸
        IconButton(
            onClick = onPlayPause,
            enabled = isTtsReady,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isReading) R.drawable.ic_pause_bold
                    else R.drawable.ic_play_circle
                ),
                contentDescription = if (isReading) "Pause" else "Play",
                tint = if (isTtsReady)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }

        Spacer(Modifier.width(4.dp))

        // ⏮  Previous sentence
        IconButton(
            onClick = onPreviousSentence,
            enabled = isTtsReady,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(
                    R.drawable.ic_skip_back
                ),
                contentDescription = "Previous sentence",
                tint = if (isTtsReady)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }

        Spacer(Modifier.width(4.dp))

        // ↺  Reset
        IconButton(
            onClick = onReset,
            enabled = isTtsReady,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(
                    R.drawable.ic_refresh
                ),
                contentDescription = "Reset",
                tint = if (isTtsReady)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}