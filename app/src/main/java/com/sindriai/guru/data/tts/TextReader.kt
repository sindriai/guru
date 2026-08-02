package com.sindriai.guru.data.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.Closeable
import java.util.Locale

// ─────────────────────────────────────────────
// Markdown/LaTeX stripping for speech
// ─────────────────────────────────────────────

/**
 * Cleaned text for TTS, plus a map from clean-text index -> original
 * source index. fullTextContent inside TextReader becomes [text] — all
 * internal parsing/position tracking happens in clean-text space exactly
 * as before. [speechToSource] is used ONLY at the point WordInfo is handed
 * back to the caller, to translate it into source-space offsets that match
 * what MixedContentRenderer/highlightRange expect.
 */
private data class SpeechText(
    val text: String,
    val speechToSource: IntArray
)

// Set true to skip speaking formula bodies entirely instead of reading
// the LaTeX content without its $ delimiters.
private const val DROP_MATH_CONTENT = false

// Group 1: **bold**  2: *italic*  3: ~~strike~~  4: `code`
// 5: $$block math$$  6: $inline math$  7: [link text](url)  8: <ihtml>...</i>
private val SPEECH_STRIP_PATTERN = Regex(
    """\*\*(.+?)\*\*|\*(.+?)\*|~~(.+?)~~|`(.+?)`|\$\$(.+?)\$\$|\$(.+?)\$|\[(.+?)\]\((.+?)\)|<ihtml>(.*?)</i>"""
)

private fun stripMarkdownForSpeech(source: String): SpeechText {
    val builder = StringBuilder()
    val map = ArrayList<Int>(source.length + 1)

    fun appendPlain(start: Int, end: Int) {
        for (i in start until end) map.add(i)
        builder.append(source, start, end)
    }

    fun appendKeepInner(textStart: Int, textEnd: Int) {
        for (i in textStart until textEnd) map.add(i)
        builder.append(source, textStart, textEnd)
    }

    var lastIndex = 0
    SPEECH_STRIP_PATTERN.findAll(source).forEach { m ->
        val range = m.range
        if (range.first > lastIndex) appendPlain(lastIndex, range.first)

        when {
            m.groups[1] != null -> appendKeepInner(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1) // bold
            m.groups[2] != null -> appendKeepInner(m.groups[2]!!.range.first, m.groups[2]!!.range.last + 1) // italic
            m.groups[3] != null -> appendKeepInner(m.groups[3]!!.range.first, m.groups[3]!!.range.last + 1) // strike
            m.groups[4] != null -> appendKeepInner(m.groups[4]!!.range.first, m.groups[4]!!.range.last + 1) // code
            m.groups[5] != null -> if (!DROP_MATH_CONTENT) // $$math$$
                appendKeepInner(m.groups[5]!!.range.first, m.groups[5]!!.range.last + 1)
            m.groups[6] != null -> if (!DROP_MATH_CONTENT) // $math$
                appendKeepInner(m.groups[6]!!.range.first, m.groups[6]!!.range.last + 1)
            m.groups[7] != null -> appendKeepInner(m.groups[7]!!.range.first, m.groups[7]!!.range.last + 1) // [text](url)
            m.groups[9] != null -> { /* <ihtml> block: never spoken, content fully dropped */ }
        }
        lastIndex = range.last + 1
    }
    if (lastIndex < source.length) appendPlain(lastIndex, source.length)
    map.add(source.length) // sentinel so map.size == text.length + 1

    return SpeechText(builder.toString(), map.toIntArray())
}

private var fullTextContent: String = ""
//private var speechToSourceMap: IntArray = IntArray(0)   // ← add this
private var words: List<WordInfo> = emptyList()
private var sentences: List<SentenceInfo> = emptyList()

// ─────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────

data class WordInfo(
    val text: String,
    val index: Int,
    val startChar: Int,
    val endChar: Int,
    val sentenceIndex: Int
)

data class SentenceInfo(
    val index: Int,
    val startChar: Int,
    val endChar: Int,
    val startWordIndex: Int,
    val endWordIndex: Int
)

enum class ReaderState {
    INITIALIZING,
    IDLE,
    READING,
    PAUSED,
    ERROR,
    STOPPED
}

// ─────────────────────────────────────────────
// TextReader
// ─────────────────────────────────────────────

class TextReader(
    context: Context,
    // Delivers the full WordInfo so callers get startChar/endChar without recomputing.
    private val onWordSpoken: (WordInfo) -> Unit,
    private val onReadingCompleted: (Boolean) -> Unit,
    private val onTtsReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) : Closeable {

    private var speechToSourceMap: IntArray = IntArray(0)

    // ── Constants ────────────────────────────

    companion object {
        private const val UTTERANCE_ID = "TextReaderUtterance"
    }

    // ── Threading ────────────────────────────

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── TTS engine ───────────────────────────

    private var tts: TextToSpeech? = null

    // ── Reader state ─────────────────────────

    @Volatile
    private var state: ReaderState = ReaderState.INITIALIZING

    // ── Text & metadata ──────────────────────

    private var fullTextContent: String = ""
    private var words: List<WordInfo> = emptyList()
    private var sentences: List<SentenceInfo> = emptyList()

    // ── Playback positions ───────────────────

    private var currentCharPosition: Int = 0
    private var currentWordIndex: Int = 0
    private var currentSentenceIndex: Int = 0
    private var currentWordStartChar: Int = 0

    private var currentUtteranceOffset: Int = 0
    private var lastTrackedWordIndex: Int = 0

    // ── Initialization ───────────────────────

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post {
                if (isStopped()) return@post
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.getDefault())
                    when (result) {
                        TextToSpeech.LANG_MISSING_DATA -> {
                            state = ReaderState.ERROR
                            onError("TTS language data is missing.")
                            onTtsReady(false)
                        }
                        TextToSpeech.LANG_NOT_SUPPORTED -> {
                            state = ReaderState.ERROR
                            onError("TTS language is not supported.")
                            onTtsReady(false)
                        }
                        else -> {
                            tts?.setOnUtteranceProgressListener(utteranceListener)
                            state = ReaderState.IDLE
                            onTtsReady(true)
                        }
                    }
                } else {
                    state = ReaderState.ERROR
                    onError("TTS initialization failed with status: $status")
                    onTtsReady(false)
                }
            }
        }
    }

    // ── UtteranceProgressListener ─────────────

    private val utteranceListener = object : UtteranceProgressListener() {

        override fun onStart(utteranceId: String?) {
            if (utteranceId != UTTERANCE_ID) return
            // State was already set to READING before speak() was called.
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId != UTTERANCE_ID) return
            mainHandler.post {
                if (isStopped()) return@post
                // Only treat as natural completion if we were actually reading.
                if (state == ReaderState.READING) {
                    resetPositions()
                    state = ReaderState.IDLE
                    onReadingCompleted(true)
                }
            }
        }

        @Deprecated("Deprecated in API 21, still required for pre-21 compat")
        override fun onError(utteranceId: String?) {
            if (utteranceId != UTTERANCE_ID) return
            mainHandler.post {
                if (isStopped()) return@post
                state = ReaderState.ERROR
                onError("TTS playback error.")
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId != UTTERANCE_ID) return
            mainHandler.post {
                if (isStopped()) return@post
                state = ReaderState.ERROR
                onError("TTS playback error. Code: $errorCode")
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            if (utteranceId != UTTERANCE_ID) return

            val absoluteStart = currentUtteranceOffset + start
            val absoluteEnd   = currentUtteranceOffset + end

            val wordIndex = findWordAtPosition(absoluteStart, absoluteEnd)
            if (wordIndex < 0) return

            val word = words[wordIndex]

            currentWordIndex      = wordIndex
            currentSentenceIndex  = word.sentenceIndex
            currentCharPosition   = word.startChar
            currentWordStartChar  = word.startChar

            mainHandler.post {
                if (isStopped()) return@post
                // word.startChar/endChar are in clean-text space (correct for internal
                // tracking above). Translate to source-space ONLY for the outgoing
                // WordInfo, since that's what becomes highlightRange downstream.
                onWordSpoken(translateToSourceSpace(word))
            }
        }

        private fun translateToSourceSpace(word: WordInfo): WordInfo {
            val map = speechToSourceMap
            if (map.isEmpty()) return word
            val srcStart = map.getOrElse(word.startChar.coerceIn(0, map.lastIndex)) { word.startChar }
            val srcEndExclusive = map.getOrElse((word.endChar + 1).coerceIn(0, map.lastIndex)) { word.endChar + 1 }
            val srcEnd = (srcEndExclusive - 1).coerceAtLeast(srcStart)
            return word.copy(startChar = srcStart, endChar = srcEnd)
        }
    }

    // ── Public API ───────────────────────────

    /**
     * Set the text to be read. Only allowed while IDLE.
     */
    fun setText(fullTextContent: String) {
        if (isStopped()) return
        if (state == ReaderState.READING || state == ReaderState.PAUSED) {
            mainHandler.post {
                onError("Cannot set text while reading or paused. Call reset() first.")
            }
            return
        }
        val speechText = stripMarkdownForSpeech(fullTextContent)
        this.fullTextContent = speechText.text          // clean text — this is what gets spoken/parsed
        this.speechToSourceMap = speechText.speechToSource
        parseText(this.fullTextContent)
        resetPositions()
    }

    /**
     * Start reading from the current position, or resume after a pause.
     */
    fun startAndResume() {
        if (isStopped()) return
        when (state) {
            ReaderState.IDLE, ReaderState.PAUSED -> speakFromCurrentPosition()
            ReaderState.INITIALIZING,
            ReaderState.ERROR,
            ReaderState.STOPPED,
            ReaderState.READING -> return
        }
    }

    /**
     * Pause reading. Preserves current word position so resume continues
     * from the start of the word that was being spoken.
     */
    fun pause() {
        if (isStopped()) return
        if (state != ReaderState.READING) return
        tts?.stop()
        state = ReaderState.PAUSED
    }

    /**
     * Stop reading and reset to the beginning. Does not start reading.
     */
    fun reset() {
        if (isStopped()) return
        when (state) {
            ReaderState.IDLE, ReaderState.READING, ReaderState.PAUSED -> {
                tts?.stop()
                resetPositions()
                state = ReaderState.IDLE
            }
            else -> return
        }
    }

    /**
     * Move to the start of the current sentence (or the previous sentence if
     * already at the start of the current one). Stops playback. Stays PAUSED.
     */
    fun readAgainSentence() {
        if (isStopped()) return
        when (state) {
            ReaderState.IDLE, ReaderState.READING, ReaderState.PAUSED -> {
                tts?.stop()

                val targetSentenceIndex = if (isAtSentenceStart()) {
                    (currentSentenceIndex - 1).coerceAtLeast(0)
                } else {
                    currentSentenceIndex
                }

                val targetSentence = sentences.getOrNull(targetSentenceIndex)
                if (targetSentence != null) {
                    currentSentenceIndex = targetSentenceIndex
                    currentWordIndex     = targetSentence.startWordIndex
                    val targetWord       = words.getOrNull(currentWordIndex)
                    currentWordStartChar = targetWord?.startChar ?: targetSentence.startChar
                    currentCharPosition  = currentWordStartChar
                    currentUtteranceOffset = currentWordStartChar
                    lastTrackedWordIndex = currentWordIndex
                } else {
                    resetPositions()
                }

                state = ReaderState.PAUSED
            }
            else -> return
        }
    }

    /**
     * Fully stop and release TTS. The reader cannot be reused after this.
     */
    fun stop() {
        if (state == ReaderState.STOPPED) return
        state = ReaderState.STOPPED
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun close() {
        stop()
    }

    // ── Private helpers ───────────────────────

    private fun isStopped() = state == ReaderState.STOPPED

    private fun speakFromCurrentPosition() {
        if (fullTextContent.isEmpty()) {
            mainHandler.post { onError("No text has been set. Call setText() first.") }
            return
        }

        val startIndex = currentWordStartChar.coerceIn(0, fullTextContent.length)
        val substring  = fullTextContent.substring(startIndex)

        if (substring.isBlank()) {
            resetPositions()
            state = ReaderState.IDLE
            mainHandler.post { onReadingCompleted(true) }
            return
        }

        currentUtteranceOffset = startIndex
        lastTrackedWordIndex   = currentWordIndex

        state = ReaderState.READING

        val params = Bundle()
        tts?.speak(substring, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    private fun resetPositions() {
        currentCharPosition    = 0
        currentWordIndex       = 0
        currentSentenceIndex   = 0
        currentWordStartChar   = words.firstOrNull()?.startChar ?: 0
        currentUtteranceOffset = 0
        lastTrackedWordIndex   = 0
    }

    private fun isAtSentenceStart(): Boolean {
        val sentence = sentences.getOrNull(currentSentenceIndex) ?: return true
        return currentWordIndex == sentence.startWordIndex
    }

    /**
     * Efficiently find the WordInfo whose char range covers [absoluteStart, absoluteEnd).
     * Scans forward from [lastTrackedWordIndex] first; falls back to a full scan if needed.
     */
    private fun findWordAtPosition(absoluteStart: Int, absoluteEnd: Int): Int {
        for (i in lastTrackedWordIndex until words.size) {
            val w = words[i]
            if (w.startChar <= absoluteStart && absoluteEnd <= w.endChar + 1) {
                lastTrackedWordIndex = i
                return i
            }
            if (w.startChar > absoluteStart) break
        }
        for (i in (lastTrackedWordIndex - 1) downTo 0) {
            val w = words[i]
            if (w.startChar <= absoluteStart && absoluteEnd <= w.endChar + 1) {
                lastTrackedWordIndex = i
                return i
            }
        }
        for (i in words.indices) {
            val w = words[i]
            if (w.startChar <= absoluteStart && absoluteEnd <= w.endChar + 1) {
                lastTrackedWordIndex = i
                return i
            }
        }
        return -1
    }

    // ── Text parsing ─────────────────────────

    /**
     * Parse [text] into [words] and [sentences] exactly once.
     *
     * Sentence terminators: . ? ! ।
     * Words never include the terminator character.
     * All char indexes refer to the original text.
     */
    private fun parseText(text: String) {
        val wordList     = mutableListOf<WordInfo>()
        val sentenceList = mutableListOf<SentenceInfo>()

        if (text.isEmpty()) {
            words     = wordList
            sentences = sentenceList
            return
        }

        val terminators = setOf('.', '?', '!', '।')

        var sentenceIndex          = 0
        var sentenceStartChar      = -1
        var sentenceStartWordIndex = 0

        var i = 0
        while (i < text.length) {
            val ch = text[i]

            when {
                ch.isWhitespace() -> {
                    i++
                }
                ch in terminators -> {
                    if (wordList.size > sentenceStartWordIndex) {
                        val endWordIndex = wordList.size - 1
                        val sentStart    = sentenceStartChar.takeIf { it >= 0 }
                            ?: wordList[sentenceStartWordIndex].startChar
                        sentenceList.add(
                            SentenceInfo(
                                index          = sentenceIndex,
                                startChar      = sentStart,
                                endChar        = i,
                                startWordIndex = sentenceStartWordIndex,
                                endWordIndex   = endWordIndex
                            )
                        )
                        sentenceIndex++
                        sentenceStartWordIndex = wordList.size
                        sentenceStartChar      = -1
                    }
                    i++
                }
                else -> {
                    val wordStart = i
                    if (sentenceStartChar < 0) sentenceStartChar = i

                    val sb = StringBuilder()
                    while (i < text.length && !text[i].isWhitespace() && text[i] !in terminators) {
                        sb.append(text[i])
                        i++
                    }
                    val wordEnd = i - 1

                    wordList.add(
                        WordInfo(
                            text          = sb.toString(),
                            index         = wordList.size,
                            startChar     = wordStart,
                            endChar       = wordEnd,
                            sentenceIndex = sentenceIndex
                        )
                    )
                }
            }
        }

        if (wordList.size > sentenceStartWordIndex) {
            val endWordIndex = wordList.size - 1
            val sentStart    = sentenceStartChar.takeIf { it >= 0 }
                ?: wordList[sentenceStartWordIndex].startChar
            sentenceList.add(
                SentenceInfo(
                    index          = sentenceIndex,
                    startChar      = sentStart,
                    endChar        = text.length - 1,
                    startWordIndex = sentenceStartWordIndex,
                    endWordIndex   = endWordIndex
                )
            )
        }

        words     = wordList
        sentences = sentenceList
    }
}