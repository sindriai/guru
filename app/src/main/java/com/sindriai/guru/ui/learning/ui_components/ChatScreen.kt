package com.sindriai.guru.ui.learning.ui_components

import android.util.LruCache
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownTheme
import com.sindriai.guru.data.chatsession.ChatHistory
import com.sindriai.guru.data.chatsession.Message
import com.sindriai.guru.data.chatsession.Sender
import com.sindriai.guru.data.gemma.GemmaInferenceManager
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.key
import kotlinx.coroutines.delay
import kotlin.text.take
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration

/**
 * Result of converting raw inline markdown into an AnnotatedString.
 *
 * [sourceToOutput] maps every character index of the ORIGINAL [markdown] source
 * (the same string highlightRange is indexed against) to the corresponding
 * character index in [annotated]. Size is source.length + 1 (sentinel at end).
 * This lets us translate a highlightRange computed against raw markdown into
 * the correct position in the rendered AnnotatedString after delimiters
 * (**, `, ~~, []()  etc.) have been stripped.
 */
data class ParsedMarkdownText(
    val annotated: AnnotatedString,
    val sourceToOutput: IntArray
)

private val INLINE_MD_PATTERN = Regex(
    """\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|~~(.+?)~~|\[(.+?)\]\((.+?)\)"""
)

/**
 * Minimal, offset-preserving inline markdown -> AnnotatedString converter.
 * Supports **bold**, *italic*, `code`, ~~strike~~, [text](url).
 *
 * Intentionally does NOT handle block-level markdown (#, -, |, >, ``` ```).
 * Those are still rendered correctly by the full Markdown() composable when
 * no highlight is active — this converter only runs while a word is being
 * highlighted, and in this app that's always a plain paragraph segment.
 */
fun inlineMarkdownToAnnotatedString(source: String): ParsedMarkdownText {
    val builder = AnnotatedString.Builder()
    val map = IntArray(source.length + 1)
    var outLen = 0

    fun appendPlain(start: Int, end: Int) {
        for (i in start until end) { map[i] = outLen; outLen++ }
        builder.append(source.substring(start, end))
    }

    // textStart/textEnd: source range of the styled text itself (inside delimiters)
    // syntaxBefore/syntaxAfter: how many delimiter chars precede/follow it
    fun appendStyled(textStart: Int, textEnd: Int, style: SpanStyle, syntaxBefore: Int, syntaxAfter: Int) {
        val outStart = outLen
        for (i in (textStart - syntaxBefore) until textStart) map[i] = outStart
        for (i in textStart until textEnd) { map[i] = outLen; outLen++ }
        val outEnd = outLen
        for (i in textEnd until (textEnd + syntaxAfter)) map[i] = outEnd
        builder.append(AnnotatedString(source.substring(textStart, textEnd), spanStyle = style))
    }

    var lastIndex = 0
    INLINE_MD_PATTERN.findAll(source).forEach { m ->
        val range = m.range
        if (range.first > lastIndex) appendPlain(lastIndex, range.first)

        when {
            m.groups[1] != null -> { // **bold**
                val g = m.groups[1]!!
                appendStyled(g.range.first, g.range.last + 1, SpanStyle(fontWeight = FontWeight.Bold), 2, 2)
            }
            m.groups[2] != null -> { // *italic*
                val g = m.groups[2]!!
                appendStyled(g.range.first, g.range.last + 1, SpanStyle(fontStyle = FontStyle.Italic), 1, 1)
            }
            m.groups[3] != null -> { // `code`
                val g = m.groups[3]!!
                appendStyled(
                    g.range.first, g.range.last + 1,
                    SpanStyle(fontFamily = FontFamily.Monospace, background = Color.LightGray.copy(alpha = 0.3f)),
                    1, 1
                )
            }
            m.groups[4] != null -> { // ~~strike~~
                val g = m.groups[4]!!
                appendStyled(g.range.first, g.range.last + 1, SpanStyle(textDecoration = TextDecoration.LineThrough), 2, 2)
            }
            m.groups[5] != null -> { // [text](url)
                val g = m.groups[5]!!
                val before = g.range.first - range.first
                val after = range.last + 1 - (g.range.last + 1)
                appendStyled(
                    g.range.first, g.range.last + 1,
                    SpanStyle(color = Color(0xFF4A9EFF), textDecoration = TextDecoration.Underline),
                    before, after
                )
            }
        }
        lastIndex = range.last + 1
    }
    if (lastIndex < source.length) appendPlain(lastIndex, source.length)
    map[source.length] = outLen

    return ParsedMarkdownText(builder.toAnnotatedString(), map)
}

object ParsedMarkdownCache {
    private const val MAX_SIZE = 200
    private val cache = object : LruCache<String, ParsedMarkdownText>(MAX_SIZE) {}
    fun get(key: String): ParsedMarkdownText? = cache.get(key)
    fun put(key: String, value: ParsedMarkdownText) { cache.put(key, value) }
}
// ─────────────────────────────────────────────────────────────────────────────
// ChatRenderBlock  — sealed block types produced by parseMixedContent()
// ─────────────────────────────────────────────────────────────────────────────

sealed class ChatRenderBlock {

    /**
     * A Markdown run.
     *
     * @param content     The markdown substring.
     * @param blockStart  Start offset of this block inside the full message string.
     * @param blockEnd    End offset (exclusive) of this block inside the full message string.
     */
    data class Markdown(
        val content: String,
        val blockStart: Int = 0,
        val blockEnd: Int = content.length
    ) : ChatRenderBlock()

    /** An embedded HTML asset — never highlighted. */
    data class Html(
        val fileName: String
    ) : ChatRenderBlock()
}

// ─────────────────────────────────────────────────────────────────────────────
// MarkdownUiCache
// ─────────────────────────────────────────────────────────────────────────────

object MarkdownUiCache {
    private const val MAX_SIZE = 200
    private val cache = object : LruCache<String, String>(MAX_SIZE) {}
    fun get(key: String): String? = cache.get(key)
    fun put(key: String, value: String) { cache.put(key, value) }
}

// ─────────────────────────────────────────────────────────────────────────────
// CachedMarkdownText
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a markdown block with an optional live word highlight.
 *
 * Highlight is injected as an inline HTML <mark> tag directly into the markdown
 * string. AnnotatedString spans cannot be used here — they are destroyed the
 * moment the value is converted back to a String for the Markdown renderer.
 *
 * @param highlightRange  Character range relative to [markdown] to highlight,
 *                        or null for no highlight. Changes here do NOT invalidate
 *                        the cached markdown string — only the display overlay
 *                        recomputes.
 */
@Composable
fun CachedMarkdownText(
    messageId: String,
    markdown: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    highlightRange: IntRange? = null
) {
    // No active highlight -> use the full markdown renderer, unchanged.
    // Nothing about this path changes from what you have today.
    if (highlightRange == null) {
        Markdown(
            markdown = markdown,
            modifier = modifier.fillMaxWidth(),
            theme = MarkdownTheme.auto(),
            isStreaming = isStreaming,
            enableScroll = false
        )
        return
    }

    // Active highlight -> bypass the markdown parser entirely and render
    // through Compose Text + AnnotatedString. background/color SpanStyle
    // never affects measurement, so the highlighted word moving cannot
    // change width/height/baseline/line-wrap — only its paint color.
    val parsed = remember(messageId, markdown, isStreaming) {
        if (isStreaming) {
            inlineMarkdownToAnnotatedString(markdown)
        } else {
            ParsedMarkdownCache.get(messageId) ?: inlineMarkdownToAnnotatedString(markdown).also {
                ParsedMarkdownCache.put(messageId, it)
            }
        }
    }

    val displayAnnotated = remember(parsed, highlightRange.first, highlightRange.last) {
        val map = parsed.sourceToOutput
        val rawStart = highlightRange.first.coerceIn(0, markdown.length)
        val rawEnd = (highlightRange.last + 1).coerceIn(rawStart, markdown.length)
        val outStart = map[rawStart].coerceIn(0, parsed.annotated.length)
        val outEnd = map[rawEnd].coerceIn(outStart, parsed.annotated.length)

        buildAnnotatedString {
            append(parsed.annotated)
            addStyle(SpanStyle(background = Color.Black, color = Color.White), outStart, outEnd)
        }
    }

    Text(
        text = displayAnnotated,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge // see note below
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// parseMixedContent
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Splits [text] into alternating Markdown / Html blocks.
 *
 * Each [ChatRenderBlock.Markdown] carries [blockStart] / [blockEnd] so that a
 * global highlight range can be mapped to a per-block local range with pure
 * arithmetic — no string scanning at playback time.
 */
fun parseMixedContent(text: String): List<ChatRenderBlock> {

    val regex     = Regex("<ihtml>(.*?)</i>")
    val blocks    = mutableListOf<ChatRenderBlock>()
    var lastIndex = 0

    regex.findAll(text).forEach { match ->
        val start = match.range.first
        val end   = match.range.last + 1

        if (start > lastIndex) {
            val mdText = text.substring(lastIndex, start)
            if (mdText.isNotBlank()) {
                blocks.add(
                    ChatRenderBlock.Markdown(
                        content    = mdText,
                        blockStart = lastIndex,
                        blockEnd   = start
                    )
                )
            }
        }

        blocks.add(ChatRenderBlock.Html(match.groupValues[1].trim()))
        lastIndex = end
    }

    if (lastIndex < text.length) {
        val mdText = text.substring(lastIndex)
        if (mdText.isNotBlank()) {
            blocks.add(
                ChatRenderBlock.Markdown(
                    content    = mdText,
                    blockStart = lastIndex,
                    blockEnd   = text.length
                )
            )
        }
    }

    return blocks
}

// ─────────────────────────────────────────────────────────────────────────────
// HtmlAssetView
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HtmlAssetView(
    topicId: String?,
    fileName: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(12.dp, 0.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(
                    "file:///android_asset/course_materials/${topicId?.take(8)}/${topicId}/htmls/$fileName"
                )
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// MixedContentRenderer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a mixed Markdown + HTML message.
 *
 * @param highlightRange  Global character range (into the original [content] string)
 *                        of the word currently spoken, or null. Mapped to the correct
 *                        Markdown sub-block with pure arithmetic. HTML blocks are
 *                        never highlighted.
 */
@Composable
fun MixedContentRenderer(
    messageId: String,
    content: String,
    topicId: String?,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    highlightRange: IntRange? = null
) {
    val blocks = remember(content) { parseMixedContent(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            when (block) {

                is ChatRenderBlock.Markdown -> {
                    // Convert global range → local range for this block.
                    // Pure arithmetic, no string scanning.
                    val localHighlight: IntRange? = if (highlightRange != null) {
                        val lo = highlightRange.first
                        val hi = highlightRange.last
                        if (lo < block.blockEnd && hi >= block.blockStart) {
                            val localStart = (lo - block.blockStart).coerceAtLeast(0)
                            val localEnd   = (hi - block.blockStart).coerceAtMost(block.content.length - 1)
                            localStart..localEnd
                        } else {
                            null
                        }
                    } else {
                        null
                    }

                    CachedMarkdownText(
                        messageId      = "${messageId}_$index",
                        markdown       = block.content,
                        isStreaming    = isStreaming,
                        highlightRange = localHighlight,
                        modifier       = Modifier.fillMaxWidth()
                    )
                }

                is ChatRenderBlock.Html -> {
                    HtmlAssetView(
                        topicId  = topicId,
                        fileName = block.fileName,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AiMessageItem
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a single AI message: TTS controls + content.
 *
 * WHY THIS EXISTS AS A SEPARATE COMPOSABLE:
 *
 * LazyColumn wraps each item lambda in a subcomposition scope. State reads
 * that happen directly inside an `items {}` lambda are NOT reliably tracked
 * for recomposition — when the map entry changes, the lazy item won't redraw.
 *
 * By moving the [highlightRanges] read into this proper @Composable function,
 * Compose correctly registers the snapshot dependency and recomposes ONLY this
 * item when its specific map entry changes. Every other message stays still.
 */
@Composable
private fun AiMessageItem(
    message: Message,
    topicId: String?,
    coordinator: ReaderCoordinator,
    highlightRanges: SnapshotStateMap<String, IntRange?>
) {
    // State read is here, inside a real @Composable — tracked correctly.
    val currentHighlight = highlightRanges[message.id]

    Column(modifier = Modifier.fillMaxWidth()) {

        ReaderControls(
            text        = message.content,
            messageId   = message.id,
            coordinator = coordinator,
            onHighlightChanged = { range ->
                if (range == null) {
                    highlightRanges.remove(message.id)
                } else {
                    highlightRanges[message.id] = range
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        MixedContentRenderer(
            messageId      = message.id,
            content        = message.content,
            isStreaming    = false,
            topicId        = topicId,
            highlightRange = currentHighlight,
            modifier       = Modifier.fillMaxWidth()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChatScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(
    chatHistory: ChatHistory,
    streamingGuruText: String?,
    topicId: String?,
    currentGemmaState: GemmaInferenceManager.InferenceState,
    modifier: Modifier = Modifier
) {
    // One coordinator shared across all AI messages — enforces single-speaker rule.
    val readerCoordinator = remember { ReaderCoordinator() }

    // Highlight state: messageId → active global char range (null = no highlight).
    val highlightRanges = remember { mutableStateMapOf<String, IntRange?>() }

    val hasFirstToken = !streamingGuruText.isNullOrBlank()

    val showThinkingUi =
        currentGemmaState == GemmaInferenceManager.InferenceState.THINKING ||
                (currentGemmaState == GemmaInferenceManager.InferenceState.ANSWERING && !hasFirstToken)

    var dotCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(showThinkingUi) {
        if (!showThinkingUi) { dotCount = 0; return@LaunchedEffect }
        while (true) {
            delay(500)
            dotCount = (dotCount + 1) % 4
        }
    }

    // NOTE: no LazyColumn here anymore — this Column is a normal child of
    // whatever scrollable container hosts ChatScreen (TopicScreen's outer
    // Column(Modifier.verticalScroll(...))). A LazyColumn cannot legally be
    // measured with the infinite height that a verticalScroll parent hands
    // its children, which is exactly what was crashing before. A plain
    // Column has no such restriction — it just lays every child out and lets
    // the ancestor scroll container handle scrolling.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // ── Previous messages ────────────────────────────────────────────
        chatHistory.chatHistory.forEach { message ->

            key(message.id) {
                if (message.sender == Sender.USER) {

                    Text(
                        text  = "\n" + message.content,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )

                } else {

                    AiMessageItem(
                        message         = message,
                        topicId         = topicId,
                        coordinator     = readerCoordinator,
                        highlightRanges = highlightRanges
                    )
                }
            }
        }

        // ── Streaming AI response — no controls, no highlighting ─────────
        streamingGuruText
            ?.takeIf { it.isNotBlank() }
            ?.let { streamingText ->
                MixedContentRenderer(
                    messageId   = "streaming_ai_response",
                    content     = streamingText,
                    isStreaming = true,
                    topicId     = topicId,
                    modifier    = Modifier.fillMaxWidth()
                )
            }

        // ── Thinking indicator ───────────────────────────────────────────
        if (showThinkingUi) {
            Text(
                text  = "Let me remember previous chats..." + ".".repeat(dotCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // ── Bottom spacing ───────────────────────────────────────────────
        Spacer(modifier = Modifier.height(48.dp))
    }
}