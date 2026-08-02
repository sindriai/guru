package com.sindriai.guru.ui.learning.ui_components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.sindriai.guru.ui.learning.AttachmentUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

private data class RenderedPdfPage(
    val bitmap: Bitmap,
    val widthPx: Int,
    val heightPx: Int
)

@Composable
fun TopScreen(
    modifier: Modifier = Modifier,
    state: AttachmentUiState,
    onClear: () -> Unit
) {
    val context = LocalContext.current

    var renderedPages by remember { mutableStateOf<List<RenderedPdfPage>>(emptyList()) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        renderedPages = emptyList()
        renderError = null
        isLoading = false

        when (state) {
            AttachmentUiState.None -> Unit

            is AttachmentUiState.StudyMaterial -> {
                isLoading = true

                val result = withContext(Dispatchers.IO) {
                    try {
                        val file = File(
                            context.cacheDir,
                            "pdf_${state.assetFileName.hashCode()}.pdf"
                        )

                        context.assets.open(state.assetFileName).use { input ->
                            FileOutputStream(file, false).use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }

                        val fd = ParcelFileDescriptor.open(
                            file,
                            ParcelFileDescriptor.MODE_READ_ONLY
                        )

                        val pages = PdfRenderer(fd).use { renderer ->
                            val density = context.resources.displayMetrics.density
                            val renderScale = (density * 1.8f).coerceIn(1.5f, 3.0f)

                            buildList {
                                for (pageIndex in 0 until renderer.pageCount) {
                                    renderer.openPage(pageIndex).use { page ->
                                        val width = max(1, (page.width * renderScale).toInt())
                                        val height = max(1, (page.height * renderScale).toInt())

                                        val bmp = Bitmap.createBitmap(
                                            width,
                                            height,
                                            Bitmap.Config.ARGB_8888
                                        )

                                        val canvas = android.graphics.Canvas(bmp)
                                        canvas.drawColor(android.graphics.Color.WHITE)

                                        val matrix = Matrix().apply {
                                            postScale(renderScale, renderScale)
                                        }

                                        page.render(
                                            bmp,
                                            null,
                                            matrix,
                                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                        )

                                        add(
                                            RenderedPdfPage(
                                                bitmap = bmp,
                                                widthPx = width,
                                                heightPx = height
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        fd.close()
                        Result.success(pages)
                    } catch (e: Exception) {
                        Result.failure<List<RenderedPdfPage>>(e)
                    }
                }

                isLoading = false

                result
                    .onSuccess { pages ->
                        renderedPages = pages
                        if (pages.isEmpty()) {
                            renderError = "No pages found in this PDF."
                        }
                    }
                    .onFailure { e ->
                        e.printStackTrace()
                        renderError = "Couldn’t load PDF."
                    }
            }
        }
    }

    when (state) {
        AttachmentUiState.None -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Selected topic material will be shown here.\nPlease select a topic from the menu.",
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }
        }

        is AttachmentUiState.StudyMaterial -> {
            when {
                isLoading -> {
                    Box(
                        modifier = modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading PDF…",
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }

                renderError != null -> {
                    Box(
                        modifier = modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = renderError ?: "Couldn’t load PDF.",
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }

                renderedPages.isNotEmpty() -> {
                    FullPdfCanvasView(
                        modifier = modifier.fillMaxSize(),
                        pages = renderedPages,
                        stateKey = state.assetFileName
                    )
                }
            }
        }
    }
}

@Composable
private fun FullPdfCanvasView(
    modifier: Modifier = Modifier,
    pages: List<RenderedPdfPage>,
    stateKey: String
) {
    val density = LocalDensity.current
    val pageGapPx = with(density) { 12.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }

        val contentWidthPx = pages.maxOfOrNull { it.widthPx }?.toFloat() ?: 1f
        val contentHeightPx = pages.sumOf { it.heightPx }.toFloat() +
                pageGapPx * (pages.size - 1).coerceAtLeast(0)

        val fitWidthScale = viewportWidthPx / contentWidthPx
        val fitHeightScale = viewportHeightPx / contentHeightPx

        val minScale = min(fitWidthScale, fitHeightScale).coerceAtMost(1f)
        val maxScale = 5f

        var scale by rememberSaveable(stateKey) { mutableFloatStateOf(minScale) }
        var offsetX by rememberSaveable(stateKey) { mutableFloatStateOf(0f) }
        var offsetY by rememberSaveable(stateKey) { mutableFloatStateOf(0f) }
        var initialized by rememberSaveable(stateKey) { mutableStateOf(false) }

        fun clampOffsets(
            currentScale: Float,
            proposedX: Float,
            proposedY: Float
        ): Pair<Float, Float> {
            val scaledWidth = contentWidthPx * currentScale
            val scaledHeight = contentHeightPx * currentScale

            val maxPanX = max(0f, (scaledWidth - viewportWidthPx) / 2f)
            val maxPanY = max(0f, (scaledHeight - viewportHeightPx) / 2f)

            return proposedX.coerceIn(-maxPanX, maxPanX) to
                    proposedY.coerceIn(-maxPanY, maxPanY)
        }

        LaunchedEffect(stateKey, minScale, viewportWidthPx, viewportHeightPx, contentWidthPx, contentHeightPx) {
            if (!initialized) {
                scale = minScale
                offsetX = 0f
                offsetY = 0f
                initialized = true
            } else {
                if (scale < minScale) {
                    scale = minScale
                }

                val (clampedX, clampedY) = clampOffsets(
                    currentScale = scale,
                    proposedX = offsetX,
                    proposedY = offsetY
                )
                offsetX = clampedX
                offsetY = clampedY
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stateKey, pages, minScale, maxScale, viewportWidthPx, viewportHeightPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(minScale, maxScale)

                        val oldCenterX = viewportWidthPx / 2f + offsetX
                        val oldCenterY = viewportHeightPx / 2f + offsetY

                        val dx = centroid.x - oldCenterX
                        val dy = centroid.y - oldCenterY
                        val scaleRatio = newScale / oldScale

                        val proposedX = offsetX + pan.x + dx * (1 - scaleRatio)
                        val proposedY = offsetY + pan.y + dy * (1 - scaleRatio)

                        val (clampedX, clampedY) = clampOffsets(
                            currentScale = newScale,
                            proposedX = proposedX,
                            proposedY = proposedY
                        )

                        scale = newScale
                        offsetX = clampedX
                        offsetY = clampedY
                    }
                }
        ) {
            drawRect(
                color = Color(0xFFF2F2F2),
                size = size
            )

            val scaledContentWidth = contentWidthPx * scale
            val scaledContentHeight = contentHeightPx * scale

            val contentLeft = (size.width - scaledContentWidth) / 2f + offsetX
            val contentTop = (size.height - scaledContentHeight) / 2f + offsetY

            var y = contentTop

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)

                pages.forEach { page ->
                    val pageWidth = page.widthPx * scale
                    val pageHeight = page.heightPx * scale
                    val x = contentLeft + (scaledContentWidth - pageWidth) / 2f

                    drawRect(
                        color = Color.White,
                        topLeft = Offset(x, y),
                        size = Size(pageWidth, pageHeight)
                    )

                    nativeCanvas.drawBitmap(
                        page.bitmap,
                        null,
                        RectF(x, y, x + pageWidth, y + pageHeight),
                        paint
                    )

                    y += pageHeight + (pageGapPx * scale)
                }
            }
        }
    }
}