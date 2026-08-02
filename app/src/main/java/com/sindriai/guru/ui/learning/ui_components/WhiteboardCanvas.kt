package com.sindriai.guru.ui.learning.ui_components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sindriai.guru.R
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Data class to store the path and its properties.
 */
data class DrawPath(
    val path: ComposePath,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun WhiteboardCanvas(
    context: Context,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onDone: (Uri) -> Unit
) {
    val scope = rememberCoroutineScope()
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentPath by remember { mutableStateOf(ComposePath()) }

    // GraphicsLayer to capture the canvas content
    val graphicsLayer = rememberGraphicsLayer()

    // Derived state to enable/disable the "Done" button
    val hasDrawing by remember { derivedStateOf { paths.isNotEmpty() } }

    // Bold stroke width for LLM clarity
    val strokeWidth = with(LocalDensity.current) { 10.dp.toPx() }
    val strokeColor = Color.Black

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp)
            .border(
                width = 1.dp,
                color = Color.LightGray,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // --- Header Toolbar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex) }) {
                        Icon(painter = painterResource(R.drawable.ic_undo), contentDescription = "Undo")
                    }
                    IconButton(onClick = { paths.clear() }) {
                        Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = "Clear")
                    }
                }

                Text(text = "Whiteboard", style = MaterialTheme.typography.titleMedium)

                IconButton(onClick = onCancel) {
                    Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "Cancel")
                }
            }

            // --- Canvas Area ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        // 1. Tell GraphicsLayer to record this canvas content
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = ComposePath().apply { moveTo(offset.x, offset.y) }
                                    paths.add(DrawPath(currentPath, strokeColor, strokeWidth))
                                },
                                onDrag = { change, _ ->
                                    currentPath.lineTo(change.position.x, change.position.y)
                                    // Trigger recomposition manually for path updates
                                    val lastPath = paths.removeAt(paths.lastIndex)
                                    paths.add(lastPath)
                                }
                            )
                        }
                ) {
                    // Draw all historical paths
                    paths.forEach { drawPath ->
                        drawPath(
                            path = drawPath.path,
                            color = drawPath.color,
                            style = Stroke(
                                width = drawPath.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // --- Floating "Done" Button ---
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clickable(enabled = hasDrawing) {
                            scope.launch {
                                // 2. Convert graphicsLayer directly to ImageBitmap
                                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()

                                // 3. Save the captured Bitmap to Internal Cache
                                val file = File(context.cacheDir, "whiteboard_${UUID.randomUUID()}.png")
                                FileOutputStream(file).use { output ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                                }

                                // 4. Generate Uri and send back
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                onDone(uri)
                            }
                        },
                    shape = RoundedCornerShape(100.dp),
                    color = if (hasDrawing) MaterialTheme.colorScheme.primary else Color.LightGray,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_send_arrow),
                            contentDescription = "Done",
                            tint = Color.White
                        )
                        Text(
                            text = "Done",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}