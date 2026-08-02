package com.sindriai.guru.ui.learning.ui_components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import coil.compose.AsyncImage
import com.sindriai.guru.R
import com.sindriai.guru.data.gemma.GemmaInferenceManager

@Composable
fun PromptBar(
    context: Context,
    currentGemmaState: GemmaInferenceManager.InferenceState,
    isTtsSpeaking: Boolean,
    isMicOn: Boolean,
    modifier: Modifier = Modifier,
    initialText: String = "",
    placeholder: String = "Ask Guru...",
    onCloseClick: () -> Unit = {},
    onStylusClick: (Boolean) -> Unit = {},
    onWhiteboardGenerated:(Uri) -> Unit,
    onFullScreenClick: (Boolean) -> Unit = {},
    onAttachClick: () -> Unit = {},
    onMicClick: (Boolean) -> Unit = {},
    onSendClick: (String) -> Unit,
    onClickStopInference: () -> Unit,
    attachedImageUri: Uri?,
    onAttachedImageRemove: () -> Unit
) {
    var inputText by remember { mutableStateOf(initialText) }
    val hasInputText = inputText.isNotBlank()

    val maxTextLines = 16
    val minTextFieldHeight = 56.dp
    val verticalTextPadding = 12.dp

    val textStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = MaterialTheme.colorScheme.onSurface
    )

    val density = LocalDensity.current
    val maxTextFieldHeight = with(density) {
        textStyle.lineHeight.toDp() * maxTextLines + verticalTextPadding * 2
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var promptBarSize by remember { mutableStateOf(PromptBarSize.Mini) }
    var isStylusEnabled by remember { mutableStateOf(false) }
    var isFullScreenMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth()
            .imePadding(),
        horizontalAlignment = Alignment.End
    ) {

        AnimatedVisibility(
            visible = isStylusEnabled
        ) {

            WhiteboardCanvas(
                context = context,

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),

                onCancel = {
                    isStylusEnabled = false
                },

                onDone = { uri ->

                    // IMPORTANT:
                    // replace attached image with whiteboard image

                    onAttachedImageRemove()

                    // You should create callback for this
                    // Example:
                    onWhiteboardGenerated(uri)

                    isStylusEnabled = false
                }
            )
        }

        if(attachedImageUri != null) {
            AttachedImagePreview(
                uri = attachedImageUri,
                onRemove = onAttachedImageRemove,
                modifier = Modifier
                    .padding(bottom = 6.dp, end = 12.dp)
            )
        }

        when (promptBarSize) {
            PromptBarSize.Expanded -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp,
                    color = colorResource(id = R.color.white),
                    modifier = modifier.border(
                        width = 1.dp,
                        color = colorResource(id = R.color.border_grey),
                        shape = RoundedCornerShape(20.dp)
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .heightIn(min = minTextFieldHeight, max = maxTextFieldHeight)
                                .animateContentSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { newText -> inputText = newText },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = verticalTextPadding),
                                textStyle = textStyle,
                                maxLines = maxTextLines,
                                cursorBrush = SolidColor(colorResource(id = R.color.tiger)),
                                decorationBox = { innerTextField ->
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = placeholder,
                                            style = textStyle.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .height(42.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    promptBarSize = PromptBarSize.Mini
                                    onCloseClick()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Collapse prompt bar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    promptBarSize = PromptBarSize.Medium
                                    onCloseClick()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_right),
                                    contentDescription = "Collapse prompt bar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .animateContentSize(
                                        animationSpec = tween(durationMillis = 200)
                                    ),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        isStylusEnabled = !isStylusEnabled
                                        onStylusClick(isStylusEnabled)
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isStylusEnabled) R.drawable.ic_stylus_on
                                            else R.drawable.ic_stylus_off
                                        ),
                                        contentDescription = "Stylus mode",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isFullScreenMode = !isFullScreenMode
                                        onFullScreenClick(isFullScreenMode)
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isFullScreenMode) R.drawable.ic_screen_full
                                            else R.drawable.ic_screen_split
                                        ),
                                        contentDescription = "Toggle screen mode",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(onClick = onAttachClick) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_attach_file),
                                        contentDescription = "Attach file",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                RadiatingMicButton(
                                    context = context,
                                    currentGemmaState = currentGemmaState,
                                    isTtsSpeaking = isTtsSpeaking,
                                    isMicOn = isMicOn,
                                    onClick = { isMicOn -> onMicClick(isMicOn) },
                                    onClickStopInference = onClickStopInference,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    baseSize = 18.dp
                                )

                                val sendSlotWidth by animateDpAsState(
                                    targetValue = if (hasInputText) 40.dp else 0.dp,
                                    animationSpec = tween(
                                        durationMillis = 600,
                                        easing = FastOutSlowInEasing
                                    ),
                                    label = "sendButtonWidth"
                                )

                                Box(
                                    modifier = Modifier.width(sendSlotWidth)
                                ) {
                                    if (hasInputText) {
                                        IconButton(
                                            onClick = {
                                                val prompt = inputText.trim()
                                                if (prompt.isNotEmpty() && currentGemmaState == GemmaInferenceManager.InferenceState.IDLE) {
                                                    onSendClick(prompt)
                                                    //empty the text field after prompt is entered
                                                    inputText = ""
                                                } else {
                                                    /*Toast.makeText(
                                                        context,
                                                        "Please wait, let Guru finish his response...",
                                                        Toast.LENGTH_SHORT
                                                    ).show()*/
                                                    onSendClick(prompt)
                                                }
                                                scope.launch {
                                                    delay(500)
                                                    focusManager.clearFocus(force = true)
                                                    keyboardController?.hide()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_send_arrow),
                                                contentDescription = "Send message",
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PromptBarSize.Medium -> {
                Box(
                    modifier = modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 1.dp,
                        color = colorResource(id = R.color.white),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = colorResource(id = R.color.border_grey),
                            shape = RoundedCornerShape(20.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .height(42.dp)
                                .padding(end = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    promptBarSize = PromptBarSize.Mini
                                    onCloseClick()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Expand prompt bar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    promptBarSize = PromptBarSize.Expanded
                                    onCloseClick()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_double_left),
                                    contentDescription = "Collapse prompt bar to mic only",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isStylusEnabled = !isStylusEnabled
                                    onStylusClick(isStylusEnabled)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isStylusEnabled) R.drawable.ic_stylus_on
                                        else R.drawable.ic_stylus_off
                                    ),
                                    contentDescription = "Stylus mode",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isFullScreenMode = !isFullScreenMode
                                    onFullScreenClick(isFullScreenMode)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (isFullScreenMode) R.drawable.ic_screen_full
                                        else R.drawable.ic_screen_split
                                    ),
                                    contentDescription = "Toggle screen mode",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(onClick = onAttachClick) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_attach_file),
                                    contentDescription = "Attach file",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            RadiatingMicButton(
                                context = context,
                                currentGemmaState = currentGemmaState,
                                isTtsSpeaking = isTtsSpeaking,
                                isMicOn = isMicOn,
                                onClick = { isMicOn -> onMicClick(isMicOn) },
                                onClickStopInference = onClickStopInference,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }

            PromptBarSize.Mini -> {
                Box(
                    modifier = modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 1.dp,
                        color = colorResource(id = R.color.white),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = colorResource(id = R.color.border_grey),
                            shape = RoundedCornerShape(20.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .height(42.dp)
                                .padding(end = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            IconButton(
                                onClick = {
                                    promptBarSize = PromptBarSize.Medium
                                    onCloseClick()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_left),
                                    contentDescription = "Expend prompt bar to medium size",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            RadiatingMicButton(
                                context = context,
                                currentGemmaState = currentGemmaState,
                                isTtsSpeaking = isTtsSpeaking,
                                isMicOn = isMicOn,
                                onClick = { isMicOn -> onMicClick(isMicOn) },
                                onClickStopInference = onClickStopInference,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                baseSize = 21.dp,
                            )
                        }
                    }
                }
            }
        }

    }
}

@Composable
fun RadiatingMicButton(
    context: Context,
    currentGemmaState: GemmaInferenceManager.InferenceState,
    isTtsSpeaking: Boolean,
    isMicOn: Boolean,
    onClick: (Boolean) -> Unit,
    onClickStopInference: () -> Unit,
    modifier: Modifier = Modifier,
    baseSize: Dp = 18.dp,
) {

    val isAnswering = currentGemmaState == GemmaInferenceManager.InferenceState.ANSWERING

    val circleColor by animateColorAsState(
        targetValue = if (isMicOn) colorResource(id = R.color.tiger) else Color.Black,
        animationSpec = tween(durationMillis = 300),
        label = "micColor"
    )

    val infiniteTransition = if (isMicOn) {
        //‼️ Any animation composable causes automatic, repeated recompositions while the animation is running.
        rememberInfiniteTransition(label = "pulse")
    } else null

    val progress = infiniteTransition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )?.value ?: 0f

    val maxPulseSize = baseSize * 1.4f
    val currentPulseSize = baseSize + (maxPulseSize - baseSize) * progress
    val pulseAlpha = 0.35f * (1f - progress)

    Box(
        modifier = modifier.size(maxPulseSize),
        contentAlignment = Alignment.Center
    ) {
        if (isMicOn && !isAnswering) {
            Box(
                modifier = Modifier
                    .size(currentPulseSize)
                    .clip(CircleShape)
                    .background(circleColor.copy(alpha = pulseAlpha))
            )
        }

        if (isAnswering) {

            Icon(
                painter = painterResource(R.drawable.ic_stop_circle),
                contentDescription = "Stop generation",
                tint = Color.Black,
                modifier = Modifier
                    .size(baseSize * 1.4f)
                    .clickable {
                        onClickStopInference()
                    }
            )

        } else {

            Box(
                modifier = Modifier
                    .size(baseSize)
                    .clip(CircleShape)
                    .background(circleColor)
                    .clickable {
                        onClick(!isMicOn)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (isMicOn)
                            R.drawable.ic_mic_on
                        else
                            R.drawable.ic_mic_off
                    ),
                    contentDescription = if (isMicOn)
                        "Stop recording"
                    else
                        "Start recording",
                    tint = Color.White,
                    modifier = Modifier.size(baseSize * 0.7f)
                )
            }
        }
    }
}

private enum class PromptBarSize {
    Expanded,
    Medium,
    Mini
}

@Composable
fun AttachedImagePreview(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(120.dp)
            .background(
                Color(0xFFF1F1F1),
                shape = RoundedCornerShape(8.dp)
            )
    ) {

        AsyncImage(
            model = uri,
            contentDescription = "Attached Image",
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxSize()
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(0.5.dp, Color.LightGray, RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape
                )
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove attached image",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}