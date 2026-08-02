package com.sindriai.guru.ui.learning.ui_components.topic_screen

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private const val LESSON_UTTERANCE_ID = "guru_lesson_utterance"

/**
 * A [start, end) character range (end exclusive) within the ORIGINAL full
 * text last passed to [LessonTextToSpeech.toggle], identifying the word
 * currently being spoken. Null when nothing is speaking or the engine
 * hasn't reported a range yet.
 */
data class TtsWordRange(val start: Int, val end: Int)

/**
 * Wraps Android's built-in (on-device) TextToSpeech engine for reading a
 * lesson aloud, with word-level highlight tracking AND stop/resume support.
 *
 * IMPORTANT: whatever text is passed to [toggle] must be EXACTLY the string
 * the UI is displaying, character for character -- [highlightRange] is a
 * pair of character offsets into that same string.
 *
 * RESUME BEHAVIOR:
 * Android's TextToSpeech has no native "start speaking at character N" --
 * speak() always reads its argument from its own position 0. To fake
 * resume, we keep [resumeFromIndex] (the offset into the ORIGINAL full
 * text where we last confirmed the engine had gotten to, via
 * onRangeStart), and on the next [toggle] we hand the engine
 * `fullText.substring(resumeFromIndex)` instead of the whole string.
 * Because the engine's onRangeStart offsets are then relative to that
 * substring, we re-add [speakBaseOffset] to translate them back into the
 * original text's coordinate space before publishing [highlightRange] or
 * updating [resumeFromIndex].
 *
 * [resumeFromIndex] is intentionally NOT cleared by [stop] -- that's the
 * whole point. It only resets to 0 on natural completion ([onDone]), on an
 * explicit [reset] call, or when [toggle] is given different text than last
 * time (a new lesson, so any old position is meaningless).
 */
class LessonTextToSpeech(context: Context) {

    private var engine: TextToSpeech? = null

    // The full text of the current reading session. Compared against the
    // text passed into toggle() each time, so switching to a different
    // lesson automatically starts that lesson from the beginning.
    private var fullText: String? = null

    // Offset into fullText where the NEXT speak() call should start.
    // 0 = from the beginning. Kept up to date word-by-word by onRangeStart
    // while speaking, and survives stop() untouched.
    private var resumeFromIndex: Int = 0

    // Offset into fullText that the CURRENTLY-PLAYING speak() call started
    // from. Needed to translate the engine's onRangeStart (start, end) --
    // which are relative to whatever substring was actually spoken -- back
    // into fullText's coordinate space.
    private var speakBaseOffset: Int = 0

    var isReady by mutableStateOf(false)
        private set

    var isSpeaking by mutableStateOf(false)
        private set

    /** The word currently being spoken, as a range into the ORIGINAL full text. */
    var highlightRange by mutableStateOf<TtsWordRange?>(null)
        private set

    /** True once some progress has been made and stopped mid-read (i.e. a resume is available). */
    val hasResumePosition: Boolean
        get() = resumeFromIndex > 0

    init {
        val tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) {
                Log.e("LessonTextToSpeech", "TextToSpeech init failed, status=$status")
            }
        }
        tts.language = Locale.getDefault()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                highlightRange = null
                // Reached the end of the text naturally -- next Listen
                // press should start over from the top.
                resumeFromIndex = 0
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                highlightRange = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                highlightRange = null
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                // Re-offset into fullText's coordinate space, since `start`
                // and `end` here are relative to the substring we actually
                // handed to speak(), not the original text.
                val base = speakBaseOffset
                highlightRange = TtsWordRange(base + start, base + end)
                // This word is now confirmed spoken -- remember its start
                // as the resume point if the user stops right after this.
                resumeFromIndex = base + start
            }
        })
        engine = tts
    }

    /**
     * Toggles between reading [speechText] aloud and stopping mid-read.
     * If a resume position exists for this exact text (i.e. it was
     * stopped mid-read rather than finished or reset), playback continues
     * from there instead of from the beginning.
     */
    fun toggle(speechText: String) {
        if (isSpeaking) {
            stop()
            return
        }

        if (fullText != speechText) {
            // Different text than last time (new lesson) -- old resume
            // position doesn't apply anymore.
            fullText = speechText
            resumeFromIndex = 0
        }

        val startFrom = resumeFromIndex.coerceIn(0, speechText.length)
        val toSpeak = speechText.substring(startFrom)
        if (toSpeak.isBlank()) {
            // We were sitting exactly at (or past) the end -- treat this
            // as "done", so the next press starts fresh.
            resumeFromIndex = 0
            return
        }

        speakBaseOffset = startFrom
        engine?.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null, LESSON_UTTERANCE_ID)
    }

    /**
     * Stops any in-progress speech. Unlike before, this does NOT reset the
     * resume position -- [resumeFromIndex] was already kept current by
     * onRangeStart, so the next [toggle] will pick up from here. The
     * on-screen highlight is cleared since nothing is actively being read.
     */
    fun stop() {
        engine?.stop()
        isSpeaking = false
        highlightRange = null
    }

    /**
     * Explicitly clears the remembered reading position, so the next
     * [toggle] starts from the very beginning regardless of where playback
     * was previously stopped. Call this from a "Reset" button.
     */
    fun reset() {
        engine?.stop()
        isSpeaking = false
        highlightRange = null
        resumeFromIndex = 0
    }

    /**
     * Releases the underlying TextToSpeech engine. Call exactly once when
     * this controller is no longer needed. [rememberLessonTextToSpeech]
     * calls this for you automatically.
     */
    fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        isSpeaking = false
        highlightRange = null
    }
}

/**
 * Creates and remembers a [LessonTextToSpeech] instance, tying its lifecycle
 * to the calling composable -- it's released automatically via
 * DisposableEffect when the composable leaves composition.
 */
@Composable
fun rememberLessonTextToSpeech(): LessonTextToSpeech {
    val context = LocalContext.current
    val controller = remember { LessonTextToSpeech(context) }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    return controller
}