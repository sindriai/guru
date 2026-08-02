package com.sindriai.guru.ui.learning.ui_components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedMathView(latex: String) {

    val measurer = rememberTextMeasurer()
    val lines = remember(latex) { latex.split("\n") }

    val strokes = remember(lines) {
        lines.map { line ->
            val ast = MathParser(line).parse()
            buildStroke(ast, measurer)
        }
    }

    // Fix for .sumOf: Explicitly convert result to Double or use sumByDouble logic
    val totalHeight = remember(strokes) {
        strokes.fold(0f) { acc, stroke -> acc + stroke.height + 20f }
    }

    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(latex) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(2000))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            // Fix for with(...): Moving the density logic outside or
            // ensuring clear scope for toDp()
            .height(with(density) { totalHeight.toDp() })
    ) {
        var yOffset = 20f

        strokes.forEach { stroke ->
            stroke.draw(this, progress.value, 0f, yOffset)
            yOffset += stroke.height + 20f
        }
    }
}