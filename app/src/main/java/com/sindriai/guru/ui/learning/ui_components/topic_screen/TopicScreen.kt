package com.sindriai.guru.ui.learning.ui_components.topic_screen

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sindriai.guru.data.chatsession.ChatHistory
import com.sindriai.guru.data.gemma.GemmaInferenceManager
import com.sindriai.guru.ui.learning.ui_components.ChatScreen
import com.sindriai.guru.ui.main.MainViewModel
import com.sindriai.guru.ui.main.dialogs.DownloadGemma3nDialog
import io.noties.markwon.Markwon

private val VIDEO_CORNER_RADIUS = 20.dp
private val SCREEN_HORIZONTAL_PADDING = 16.dp
private val BUTTON_BOTTOM_PADDING = 20.dp
private val CONTENT_TOP_SPACING = 20.dp
private val GRADIENT_FULL_BLACK_OFFSET = 500.dp
private const val CTA_FADE_IN_MS = 200
private const val CTA_FADE_OUT_MS = 300
private val LISTEN_BUTTON_FLOAT_TOP_PADDING = 16.dp
private const val LISTEN_BUTTON_FADE_MS = 180
private val ASK_DOUBT_TOP_SPACING = 24.dp
private val LESSON_IMAGE_CORNER_RADIUS = 12.dp

// Separator inserted between consecutive text blocks when they're
// concatenated into one string for TTS. Must be used consistently both when
// building the combined speech string and when computing each block's
// offset into it (see `renderedTextBlocks` below) -- otherwise highlight
// ranges will drift out of sync with the text actually being spoken.
private const val SPEECH_BLOCK_SEPARATOR = "\n\n"

// --- ordered content model for a lesson. Parsed once per topic load
// (see loadLessonBlocks) instead of handing Markwon one big string. Kept
// deliberately small/sealed so it's trivial to extend later with
// LessonBlock.Video, LessonBlock.Table, LessonBlock.Quiz, etc. without
// touching the parsing or rendering call sites for existing block types.
sealed class LessonBlock {
    data class Text(val markdown: String) : LessonBlock()

    /** [assetPath] is already fully resolved (lesson directory + filename), ready to hand to `context.assets.open(...)`. */
    data class Image(val assetPath: String) : LessonBlock()

    /** Same resolution rule as Image: lesson directory + filename, loaded via `file:///android_asset/...`. */
    data class Html(val assetPath: String) : LessonBlock()
}

// A Text block that has already been run through Markwon, plus where it
// sits inside the combined TTS speech string. `startOffsetInSpeech`/
// `length` are used to translate a *global* TTS highlight range back into a
// range local to this block's own Spanned text.
private data class RenderedTextBlock(
    val spanned: Spanned,
    val startOffsetInSpeech: Int,
    val length: Int
)

@OptIn(UnstableApi::class)
@Composable
fun TopicScreen(
    chatHistory: ChatHistory,
    streamingGuruText: String?,
    topicId: String?,
    currentGemmaState: GemmaInferenceManager.InferenceState,
    mainViewModel: MainViewModel,
    showPromptBar: (Boolean) -> Unit,
    onModelReady: () -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val density = LocalDensity.current

    // --- CHANGED: keyed by topicId. These represent per-topic "session"
    // state (has the user started this lesson / opened chat for it). Keying
    // the rememberSaveable call on topicId gives each topic its own saved
    // slot, so switching to a different topic starts both at their default
    // (false) instead of inheriting whatever the previously-viewed topic
    // left behind.
    var hasStartedLearning by rememberSaveable(topicId) { mutableStateOf(false) }
    var showChat by rememberSaveable(topicId) { mutableStateOf(false) }

    // Derive the real aspect ratio from the video itself instead of hardcoding 16:9
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }

    // --- CHANGED: lesson content is now an ordered list of text/image/html
    // blocks instead of one markdown string, so `<image>` and `<html>` tags
    // can render as actual images / embedded pages instead of literal text.
    var lessonBlocks by remember { mutableStateOf<List<LessonBlock>>(emptyList()) }

    // --- CHANGED: keyed by topicId instead of Unit. LaunchedEffect only
    // restarts its coroutine when its key changes identity across
    // recompositions -- Unit never changes, so the old version ran exactly
    // once for the lifetime of this composable instance and then never
    // again, regardless of how many new topicId values flowed in afterward.
    // Keying on topicId means: every time a new topic is selected, Compose
    // cancels the in-flight load for the old topic (if any) and launches a
    // fresh one for the new topic -- this is also what protects against a
    // slow old request resolving after a newer one and overwriting it.
    LaunchedEffect(topicId) {
        // Clear immediately so a topic switch never shows the previous
        // topic's content while the new one is still loading off the main
        // thread.
        lessonBlocks = emptyList()
        lessonBlocks = withContext(Dispatchers.IO) {
            loadLessonBlocks(
                context,
                "course_materials/${topicId?.take(8)}/${topicId}/${topicId}.json"
            )
        }
        //by default making prompt bar invisible on each topic rendering.
        showPromptBar(false)
    }

    // Text-to-speech (Android's built-in, on-device engine). Deliberately
    // NOT re-keyed by topicId -- rememberLessonTextToSpeech() almost
    // certainly owns a real TextToSpeech engine handle, and re-keying it
    // would tear down + reinitialize that engine on every topic switch.
    // Instead we explicitly tell it to stop/reset below.
    val lessonTts = rememberLessonTextToSpeech()

    val markwon = remember { Markwon.create(context) }

    // --- render each Text block through Markwon exactly once per
    // lessonBlocks change (not on every recomposition), and record each
    // block's offset within the concatenated TTS speech string. Image and
    // Html blocks contribute nothing here -- TTS must never see them.
    val renderedTextBlocks = remember(lessonBlocks) {
        val result = mutableListOf<RenderedTextBlock>()
        var offset = 0
        for (block in lessonBlocks) {
            if (block is LessonBlock.Text) {
                val spanned = markwon.toMarkdown(block.markdown)
                val plainLength = spanned.toString().length
                result.add(RenderedTextBlock(spanned, offset, plainLength))
                offset += plainLength + SPEECH_BLOCK_SEPARATOR.length
            }
        }
        result
    }

    // The single string handed to TTS -- text blocks only, in order,
    // joined with the same separator used to compute offsets above.
    val combinedSpeechText = remember(renderedTextBlocks) {
        renderedTextBlocks.joinToString(SPEECH_BLOCK_SEPARATOR) { it.spanned.toString() }
    }

    // Pairs each block with its rendered form (null for images/html) so the
    // UI can walk one list, in the original order, for all content types.
    val displayBlocks = remember(lessonBlocks, renderedTextBlocks) {
        var textIndex = 0
        lessonBlocks.map { block ->
            when (block) {
                is LessonBlock.Text -> block to renderedTextBlocks[textIndex++]
                is LessonBlock.Image -> block to null
                is LessonBlock.Html -> block to null
            }
        }
    }

    val windowInfo = LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height.toFloat()
    val screenHeightDp = with(density) { screenHeightPx.toDp() }

    var rootTopInRoot by remember { mutableStateOf(0f) }
    var rootLeftInRoot by remember { mutableStateOf(0f) }
    var buttonTopInRoot by remember { mutableStateOf(0f) }

    var videoBoxLeftInRoot by remember { mutableStateOf(0f) }
    var videoBoxTopInRoot by remember { mutableStateOf(0f) }
    var videoBoxWidthPx by remember { mutableStateOf(0f) }
    var videoBoxHeightPx by remember { mutableStateOf(0f) }

    val scrollState = rememberScrollState()

    // --- whenever the topic changes, stop/clear any in-flight TTS
    // from the previous topic and scroll back to the top. Without this,
    // switching topics mid-narration would keep speaking the old topic's
    // text (and its highlightRange/resume-position offsets would be
    // pointing at character positions that belong to the old text, not the
    // new one), and the new topic would render already scrolled to
    // wherever the old one was left.
    //
    // NOTE: swap `lessonTts.reset()` for whatever your LessonTextToSpeech
    // exposes to fully halt playback (e.g. `stop()`) if `reset()` only
    // clears the resume position without stopping active speech -- check
    // LessonTextToSpeech.kt for the exact contract.
    LaunchedEffect(topicId) {
        lessonTts.reset()
        scrollState.scrollTo(0)
    }

    // --- root-relative Y position of the *in-layout* Listen/Stop
    // button, reported every time it moves (including on every scroll
    // tick, since onGloballyPositioned fires on scroll offset changes too).
    var listenButtonTopInRoot by remember { mutableStateOf(Float.MAX_VALUE) }

    val floatTopPaddingPx = with(density) { LISTEN_BUTTON_FLOAT_TOP_PADDING.toPx() }
    val isListenButtonFloating = hasStartedLearning &&
            renderedTextBlocks.isNotEmpty() &&
            (listenButtonTopInRoot - rootTopInRoot) < floatTopPaddingPx

    // NOTE: this ExoPlayer is created once via `remember { }` and its
    // media URI is hardcoded to "life_process.mp4" -- it is NOT currently
    // keyed to topicId at all, so every topic shows the same clip. If each
    // topic is supposed to have its own video, the right fix is NOT to key
    // this `remember` block on topicId (that would tear down/recreate the
    // whole ExoPlayer -- expensive, causes a visible flicker). Instead add
    // a LaunchedEffect(topicId) that calls exoPlayer.setMediaItem(...) +
    // exoPlayer.prepare() with the topic-specific URI, reusing the same
    // player instance. Flagging this since it's out of scope for the
    // lesson-content bug but sits right next to it.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val videoUri = Uri.parse("asset:///life_process.mp4")
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height != 0) {
                        videoAspectRatio =
                            (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.d("TopicScreen", "Player error: ${error.errorCodeName}", error)
                }
            })

            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootTopInRoot = coords.positionInRoot().y
                rootLeftInRoot = coords.positionInRoot().x
            }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {

            // --- FIX: the content is now ALWAYS laid out through
            // verticalScroll, i.e. always measured with unbounded height,
            // regardless of `hasStartedLearning`. Only two things change
            // with `hasStartedLearning` now:
            //
            //   1) whether the user can actually drag-scroll
            //      (`enabled = hasStartedLearning`)
            //   2) the OUTER viewport's height + clip, which crops the
            //      already-fully-measured content down to one screen's
            //      worth for the "peek behind the CTA" effect.
            //
            // Why this matters (root cause of the HTML-block bug):
            // Previously the pre-click branch used
            // `Modifier.height(screenHeightDp).clipToBounds()` directly on
            // the Column instead of an unbounded verticalScroll container.
            // Text/Image blocks still rendered fine there because they are
            // pure Compose canvas draws -- their pixels are computed
            // synchronously the instant they're laid out, clip or no clip.
            // LessonHtml's WebView is different: it starts at
            // `height(0.dp)` and only grows once Chromium's own
            // asynchronous pipeline (onPageFinished -> evaluateJavascript
            // -> ResizeObserver -> AndroidSizing.report()) reports a real
            // height back. That pipeline rides on the WebView's own
            // render/compositor loop, which gets throttled or suspended by
            // the platform for a view it considers effectively zero-sized
            // / clipped out of the visible viewport -- exactly the state
            // the old fixed-height, non-scrolling, clipped Column put it
            // in. Post-click, the Column switched to an unbounded
            // verticalScroll, the WebView finally got a real layout pass,
            // and the pipeline unblocked -- which is why it "just worked"
            // after tapping Start Learning.
            //
            // Always measuring through verticalScroll removes that
            // discrepancy: the WebView (and everything else) is laid out
            // identically whether or not the user has tapped "Start
            // Learning" -- only the outer crop and scroll-gesture
            // enablement change.
            val outerViewportModifier =
                if (hasStartedLearning) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(screenHeightDp)
                        .clipToBounds()
                }

            Box(modifier = outerViewportModifier) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState, enabled = hasStartedLearning)
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspectRatio)
                            .padding(horizontal = SCREEN_HORIZONTAL_PADDING, vertical = 12.dp)
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                videoBoxLeftInRoot = pos.x
                                videoBoxTopInRoot = pos.y
                                videoBoxWidthPx = coords.size.width.toFloat()
                                videoBoxHeightPx = coords.size.height.toFloat()
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(VIDEO_CORNER_RADIUS)),
                            shape = RoundedCornerShape(VIDEO_CORNER_RADIUS),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            VideoPlayerView(
                                exoPlayer = exoPlayer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = SCREEN_HORIZONTAL_PADDING,
                                end = SCREEN_HORIZONTAL_PADDING,
                                top = CONTENT_TOP_SPACING
                            )
                    ) {
                        LearningParagraphSection(
                            modifier = Modifier.fillMaxWidth(),
                            hasStartedLearning = hasStartedLearning,
                            displayBlocks = displayBlocks,
                            highlightRange = lessonTts.highlightRange,
                            isSpeaking = lessonTts.isSpeaking,
                            isTtsReady = lessonTts.isReady,
                            onToggleSpeech = {
                                if (combinedSpeechText.isNotEmpty()) {
                                    lessonTts.toggle(combinedSpeechText)
                                }
                            },
                            onListenButtonPositioned = { top -> listenButtonTopInRoot = top },
                            isListenButtonFloating = isListenButtonFloating,
                            hasResumePosition = lessonTts.hasResumePosition,
                            onResetSpeech = { lessonTts.reset() }
                        )
                    }

                    if (hasStartedLearning) {
                        if (!showChat) {
                            Button(
                                onClick = {
                                    showChat = true
                                    showPromptBar(showChat) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = SCREEN_HORIZONTAL_PADDING,
                                        end = SCREEN_HORIZONTAL_PADDING,
                                        top = ASK_DOUBT_TOP_SPACING,
                                        bottom = 100.dp
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF088F8F)
                                ),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "AI से अपने doubts clear करें",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            var modelReady by remember {
                                mutableStateOf(mainViewModel.isModelAlreadyDownloaded())
                            }

                            if (!modelReady) {
                                DownloadGemma3nDialog(
                                    mainViewModel = mainViewModel,
                                    onDownloadComplete = { modelReady = true }
                                )
                            }

                            if (modelReady) {
                                LaunchedEffect(Unit) {
                                    onModelReady()
                                }

                                Text(
                                    text = "आपके doubts और questions",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontSize = 20.sp,
                                    color = Color(0xFF088F8F),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp, 50.dp, 12.dp, 0.dp)
                                )

                                Log.d("TopicID", "topic id changed to " + topicId)

                                ChatScreen(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp, 0.dp, 12.dp, 200.dp),
                                    chatHistory = chatHistory,
                                    streamingGuruText = streamingGuruText,
                                    topicId = topicId,
                                    currentGemmaState = currentGemmaState
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !hasStartedLearning,
            modifier = Modifier
                .matchParentSize()
                .zIndex(1f),
            enter = fadeIn(tween(CTA_FADE_IN_MS)),
            exit = fadeOut(tween(CTA_FADE_OUT_MS))
        ) {
            CinematicShadowOverlay(
                modifier = Modifier.fillMaxSize(),
                buttonTopPx = buttonTopInRoot - rootTopInRoot,
                screenHeightPx = screenHeightPx
            )
        }

        val videoBoxWidthDp = with(density) { videoBoxWidthPx.toDp() }
        val videoBoxHeightDp = with(density) { videoBoxHeightPx.toDp() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (videoBoxLeftInRoot - rootLeftInRoot).roundToInt(),
                        (videoBoxTopInRoot - rootTopInRoot).roundToInt()
                    )
                }
                .size(width = videoBoxWidthDp, height = videoBoxHeightDp)
                .zIndex(2f),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = !hasStartedLearning,
                modifier = Modifier.padding(bottom = BUTTON_BOTTOM_PADDING),
                enter = fadeIn(tween(CTA_FADE_IN_MS)),
                exit = fadeOut(tween(CTA_FADE_OUT_MS))
            ) {
                Button(
                    onClick = {
                        exoPlayer.playWhenReady = false
                        exoPlayer.pause()
                        hasStartedLearning = true
                    },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        buttonTopInRoot = coords.positionInRoot().y
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF088F8F)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Start Learning",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isListenButtonFloating,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = LISTEN_BUTTON_FLOAT_TOP_PADDING, end = SCREEN_HORIZONTAL_PADDING)
                .zIndex(3f),
            enter = fadeIn(tween(LISTEN_BUTTON_FADE_MS)),
            exit = fadeOut(tween(LISTEN_BUTTON_FADE_MS))
        ) {
            ListenStopButtonContent(
                isSpeaking = lessonTts.isSpeaking,
                isTtsReady = lessonTts.isReady,
                onToggle = {
                    if (combinedSpeechText.isNotEmpty()) {
                        lessonTts.toggle(combinedSpeechText)
                    }
                },
                modifier = Modifier
                    .shadow(elevation = 0.dp, shape = RoundedCornerShape(50), clip = true)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                setBackgroundColor(android.graphics.Color.WHITE)
                setShutterBackgroundColor(android.graphics.Color.WHITE)
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    )
}

@Composable
private fun CinematicShadowOverlay(
    modifier: Modifier = Modifier,
    buttonTopPx: Float,
    screenHeightPx: Float
) {
    Box(
        modifier = modifier.drawBehind {
            if (screenHeightPx <= 0f) return@drawBehind

            val buttonFraction =
                (buttonTopPx / screenHeightPx).coerceIn(0f, 1f)

            val fullDarkFraction =
                ((buttonTopPx + GRADIENT_FULL_BLACK_OFFSET.toPx()) / screenHeightPx)
                    .coerceIn(buttonFraction, 1f)

            val gradient = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    buttonFraction to Color.Black.copy(alpha = 0.50f),
                    fullDarkFraction to Color.Black,
                    1f to Color.Black
                ),
                startY = 0f,
                endY = screenHeightPx
            )

            drawRect(
                brush = gradient,
                size = size
            )
        }
    )
}

@Composable
private fun ListenStopButtonContent(
    isSpeaking: Boolean,
    isTtsReady: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onToggle,
        enabled = isTtsReady,
        shape = RoundedCornerShape(50.dp),
        modifier = modifier
    ) {
        Text(if (isSpeaking) "⏹ Stop" else "▶ Listen")
    }
}

// --- renders the ordered list of text/image/html blocks instead of a
// single Spanned. `highlightRange` is still the *global* TTS range (in
// terms of combinedSpeechText); it gets translated into a local range for
// each Text block right before being handed to the untouched
// MarkdownText/highlightedText logic below.
@Composable
private fun LearningParagraphSection(
    modifier: Modifier = Modifier,
    hasStartedLearning: Boolean,
    displayBlocks: List<Pair<LessonBlock, RenderedTextBlock?>>,
    highlightRange: TtsWordRange?,
    isSpeaking: Boolean,
    isTtsReady: Boolean,
    onToggleSpeech: () -> Unit,
    onListenButtonPositioned: (Float) -> Unit,
    isListenButtonFloating: Boolean,
    hasResumePosition: Boolean,
    onResetSpeech: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListenStopButtonContent(
                isSpeaking = isSpeaking,
                isTtsReady = isTtsReady,
                onToggle = onToggleSpeech,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        onListenButtonPositioned(coords.positionInRoot().y)
                    }
                    .alpha(if (isListenButtonFloating) 0f else 1f)
            )

            Spacer(modifier = Modifier.weight(1f))

            if (hasResumePosition && !isSpeaking) {
                OutlinedButton(
                    onClick = onResetSpeech,
                    enabled = isTtsReady
                ) {
                    Text("Reset")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "परिचय (Introduction)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        for ((block, rendered) in displayBlocks) {
            when (block) {
                is LessonBlock.Text -> {
                    // rendered is never null for a Text block (see
                    // displayBlocks construction), but the null-check
                    // keeps this safe if that invariant ever changes.
                    rendered?.let { r ->
                        val localHighlightRange = highlightRange?.let { hr ->
                            val localStart = (hr.start - r.startOffsetInSpeech)
                                .coerceIn(0, r.length)
                            val localEnd = (hr.end - r.startOffsetInSpeech)
                                .coerceIn(0, r.length)
                            if (localStart < localEnd) {
                                TtsWordRange(localStart, localEnd)
                            } else {
                                null
                            }
                        }

                        MarkdownText(
                            renderedMarkdown = r.spanned,
                            highlightRange = localHighlightRange,
                            learningStarted = hasStartedLearning,
                            modifier = Modifier.fillMaxWidth(),
                            textSizeSp = 17f
                        )
                    }
                }

                is LessonBlock.Image -> {
                    LessonImage(
                        assetPath = block.assetPath,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is LessonBlock.Html -> {
                    LessonHtml(
                        assetPath = block.assetPath,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Text(text = "", modifier = Modifier.padding(bottom = 24.dp))
    }
}

// --- loads a single lesson image from assets (png/jpg/jpeg/webp all
// go through the same BitmapFactory path) and renders it full-width with
// its real aspect ratio preserved and rounded corners. Loading happens off
// the main thread and is keyed by assetPath, so switching topics (which
// produces new LessonBlock.Image instances with different paths) reloads
// automatically without any manual cache invalidation.
@Composable
private fun LessonImage(
    assetPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageBitmap by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(assetPath) {
        imageBitmap = withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                Log.e("TopicScreen", "Failed to load lesson image from $assetPath", e)
                null
            }
        }
    }

    imageBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .clip(RoundedCornerShape(LESSON_IMAGE_CORNER_RADIUS))
        )
    }
}

// --- renders a lesson HTML block via WebView, loaded straight from
// assets so relative <link>/<script> refs beside the HTML resolve
// automatically (same directory as the lesson JSON, exactly like images).
//
// The WebView's own scrolling is never engaged because we size it in
// Compose to exactly match its measured content height, so scrolling is
// always delegated to the outer lesson Column's verticalScroll.
//
// The WebView instance is created once per slot (factory{}) and reused
// across recompositions; `update{}` only calls loadUrl when assetPath
// actually changed (tracked via webView.tag), so switching topics or
// recomposing this screen never tears down/recreates the WebView unless
// its underlying HTML block truly changed.
//
// IMPORTANT: this composable's measurement now always happens inside an
// unbounded verticalScroll container (see TopicScreen), regardless of
// hasStartedLearning. That's what lets onPageFinished -> evaluateJavascript
// -> ResizeObserver -> AndroidSizing.report() reliably complete even
// before the user taps "Start Learning" -- previously it was measured
// inside a fixed-height, clipped, non-scrolling Column pre-click, which
// could leave the WebView's own render pipeline stuck at its initial
// zero-height state indefinitely. Do not reintroduce a bounded/clipped
// ancestor around this composable without going through an unbounded
// verticalScroll first.
@Composable
private fun LessonHtml(
    assetPath: String,
    modifier: Modifier = Modifier
) {
    // 0.dp instead of 1.dp: keep it at a true zero rather than a
    // near-zero size that's still just large enough for Chromium to
    // attempt (and cache) a degenerate first paint.
    var contentHeightDp by remember(assetPath) { mutableStateOf(0.dp) }

    // Tracks whether we've received a real measurement yet, so the WebView
    // is never drawn while sized at 0dp/near-0dp -- it's laid out and
    // loading in the background, just not painted to the screen.
    var isMeasured by remember(assetPath) { mutableStateOf(false) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeightDp)
            .alpha(if (isMeasured) 1f else 0f),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                setBackgroundColor(android.graphics.Color.WHITE)

                addJavascriptInterface(
                    HtmlSizingBridge { measuredHeightPx ->
                        post {
                            contentHeightDp = measuredHeightPx.dp
                            isMeasured = true
                            // Resizing purely via Compose's outer
                            // Modifier.height() updates this View's layout
                            // bounds, but doesn't reliably force Chromium's
                            // own compositor to repaint its surface at the
                            // new size. Force it explicitly so we never end
                            // up showing stale (blank) content stretched
                            // into the new bounds.
                            requestLayout()
                            invalidate()
                        }
                    },
                    "AndroidSizing"
                )

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(MEASURE_AND_OBSERVE_HEIGHT_JS, null)
                    }
                }
            }
        },
        update = { webView ->
            val targetUrl = "file:///android_asset/$assetPath"
            if (webView.tag != targetUrl) {
                webView.tag = targetUrl
                contentHeightDp = 0.dp
                isMeasured = false
                webView.loadUrl(targetUrl)
            }

            // Read contentHeightDp here so this `update` block is a
            // recomposition scope for it -- otherwise Compose has no
            // reason to re-invoke `update` on a pure height-state change,
            // and the requestLayout/invalidate below would never run from
            // here.
            @Suppress("UNUSED_EXPRESSION")
            contentHeightDp
            webView.requestLayout()
            webView.invalidate()
        }
    )
}

// Bridges JS -> Kotlin so the WebView can report its own measured content
// height back to Compose. Kept as a tiny standalone class (rather than a
// lambda passed directly to addJavascriptInterface) because the
// @JavascriptInterface annotation must sit on a concrete method.
private class HtmlSizingBridge(private val onHeightMeasured: (Int) -> Unit) {
    @android.webkit.JavascriptInterface
    fun report(heightPx: Int) {
        onHeightMeasured(heightPx)
    }
}

// Measures content height once on load, then keeps re-reporting on any
// later resize (images finishing decode, JS-driven DOM changes, collapsible
// sections toggling, MathJax re-layout, etc.) so the WebView keeps tracking
// its true content height instead of freezing at the first measurement.
private const val MEASURE_AND_OBSERVE_HEIGHT_JS = """
(function() {
    function report() {
        var h = Math.max(
            document.body ? document.body.scrollHeight : 0,
            document.documentElement.scrollHeight
        );
        AndroidSizing.report(h);
    }
    report();
    if (window.ResizeObserver) {
        new ResizeObserver(report).observe(document.documentElement);
    } else {
        window.addEventListener('load', report);
    }
})();
"""

@Composable
private fun MarkdownText(
    renderedMarkdown: Spanned,
    highlightRange: TtsWordRange?,
    learningStarted: Boolean,
    modifier: Modifier = Modifier,
    textColor: Int = android.graphics.Color.BLACK,
    textSizeSp: Float = 16f
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                setTextColor(textColor)
                textSize = textSizeSp
                setLineSpacing(0f, 1.3f)
            }
        },
        update = { textView ->

            textView.text = highlightRange?.let { range ->
                highlightedText(renderedMarkdown, range)
            } ?: renderedMarkdown

            if (learningStarted) {
                textView.setOnTouchListener(null)
            } else {
                textView.setOnTouchListener { _, _ ->
                    true
                }
            }
        }
    )
}

private fun highlightedText(base: Spanned, range: TtsWordRange): CharSequence {
    val length = base.length
    val start = range.start.coerceIn(0, length)
    val end = range.end.coerceIn(start, length)
    if (start >= end) return base

    return SpannableString(base).apply {
        setSpan(BackgroundColorSpan(android.graphics.Color.BLACK), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(android.graphics.Color.WHITE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

// Matches `<tag>filename</tag>` for any of the supported lesson tags,
// tolerant of the tag body spanning multiple lines (shouldn't normally
// happen, but DOT_MATCHES_ALL costs nothing and avoids a silent parse miss
// if a JSON author adds a stray newline inside the tag).
//
// To support a new block type later (e.g. <video>, <table>, <quiz>,
// <audio>), just add its name to this alternation and add one branch in
// the `when` inside parseLessonContent -- the splitting/ordering logic
// itself never needs to change.
private val LESSON_TAG_REGEX = Regex(
    "<(image|html)>(.*?)</\\1>",
    RegexOption.DOT_MATCHES_ALL
)

// --- splits raw lesson content into ordered Text/Image/Html blocks.
// `baseDir` is the asset directory the lesson JSON itself lives in (lesson
// images/html are always siblings of the JSON per the course_materials
// layout), so each tag's filename is resolved to a full asset path here,
// once, rather than re-resolved every time it's rendered.
private fun parseLessonContent(content: String, baseDir: String): List<LessonBlock> {
    val blocks = mutableListOf<LessonBlock>()
    var lastIndex = 0

    for (match in LESSON_TAG_REGEX.findAll(content)) {
        val textPart = content.substring(lastIndex, match.range.first).trim()
        if (textPart.isNotEmpty()) {
            blocks.add(LessonBlock.Text(textPart))
        }

        val tagName = match.groupValues[1]
        val fileName = match.groupValues[2].trim()
        if (fileName.isNotEmpty()) {
            val assetPath = "$baseDir/$fileName"
            blocks.add(
                when (tagName) {
                    "image" -> LessonBlock.Image(assetPath)
                    "html" -> LessonBlock.Html(assetPath)
                    else -> LessonBlock.Text(match.value) // unreachable given the regex alternation
                }
            )
        }

        lastIndex = match.range.last + 1
    }

    val trailingText = content.substring(lastIndex).trim()
    if (trailingText.isNotEmpty()) {
        blocks.add(LessonBlock.Text(trailingText))
    }

    return blocks
}

// --- reads the lesson JSON's "content" field and parses it into
// LessonBlocks, resolving <image>/<html> tags against the directory the
// JSON itself was loaded from.
private fun loadLessonBlocks(context: Context, assetPath: String): List<LessonBlock> {
    return try {
        val jsonText = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val content = JSONObject(jsonText).getString("content")
        val baseDir = assetPath.substringBeforeLast("/")
        parseLessonContent(content, baseDir)
    } catch (e: Exception) {
        Log.e("TopicScreen", "Failed to load lesson content from $assetPath", e)
        emptyList()
    }
}