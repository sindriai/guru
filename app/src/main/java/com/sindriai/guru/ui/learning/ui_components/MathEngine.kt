package com.sindriai.guru.ui.learning.ui_components

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

// ---------------- AST ----------------

sealed class MathNode {
    data class Text(val value: String) : MathNode()
    data class Fraction(val num: MathNode, val den: MathNode) : MathNode()
    data class Sqrt(val value: MathNode) : MathNode()
    data class Power(val base: MathNode, val exp: MathNode) : MathNode()
    data class Group(val children: List<MathNode>) : MathNode()
}

// ---------------- PARSER ----------------

class MathParser(private val input: String) {
    private var pos = 0

    private fun peek(): Char? = input.getOrNull(pos)
    private fun next(): Char? = input.getOrNull(pos++)

    fun parse(): MathNode = parseExpression()

    private fun parseExpression(): MathNode {
        val nodes = mutableListOf<MathNode>()

        while (true) {
            val ch = peek() ?: break

            when {
                ch == '{' -> {
                    next()
                    nodes.add(parseExpression())
                    next()
                }

                ch == '\\' -> nodes.add(parseCommand())

                ch == '^' -> {
                    next()
                    val exp = parseAtom()
                    if (nodes.isNotEmpty()) {
                        val base = nodes.removeAt(nodes.size - 1)
                        nodes.add(MathNode.Power(base, exp))
                    } else {
                        nodes.add(MathNode.Power(MathNode.Text(""), exp))
                    }
                }

                ch == '}' -> break

                else -> nodes.add(MathNode.Text(next().toString()))
            }
        }

        return if (nodes.size == 1) nodes[0] else MathNode.Group(nodes)
    }

    private fun parseAtom(): MathNode {
        return if (peek() == '{') {
            next()
            val node = parseExpression()
            next()
            node
        } else {
            MathNode.Text(next()?.toString() ?: "")
        }
    }

    private fun parseCommand(): MathNode {
        next() // skip backslash

        return when {
            input.startsWith("frac", pos) -> {
                pos += 4
                val num = parseAtom()
                val den = parseAtom()
                MathNode.Fraction(num, den)
            }

            input.startsWith("sqrt", pos) -> {
                pos += 4
                val value = parseAtom()
                MathNode.Sqrt(value)
            }

            else -> MathNode.Text("?")
        }
    }
}

// ---------------- STROKE MODEL ----------------

data class StrokeBox(
    val width: Float,
    val height: Float,
    val totalLength: Float,
    val draw: (DrawScope, Float, Float, Float) -> Unit
)

// ---------------- BUILDER ----------------

fun buildStroke(
    node: MathNode,
    measurer: TextMeasurer
): StrokeBox {

    fun textBox(text: String): StrokeBox {
        val layout = measurer.measure(
            AnnotatedString(text),
            style = TextStyle(fontSize = 22.sp, fontFamily = FontFamily.Cursive)
        )

        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()

        return StrokeBox(w, h, w) { scope, progress, x, y ->
            // Use drawText directly from the scope
            scope.drawText(layout, topLeft = Offset(x, y), alpha = progress)
        }
    }

    return when (node) {

        is MathNode.Text -> textBox(node.value)

        is MathNode.Group -> {
            val children = node.children.map { buildStroke(it, measurer) }

            val width = children.sumOf { it.width.toDouble() }.toFloat()
            val height = if (children.isEmpty()) 0f else children.maxOf { it.height }
            val total = children.sumOf { it.totalLength.toDouble() }.toFloat()

            StrokeBox(width, height, total) { scope, progress, x, y ->
                var cursor = x
                var remaining = progress * total

                children.forEach {
                    val p = if (it.totalLength > 0) (remaining / it.totalLength).coerceIn(0f, 1f) else 1f
                    it.draw(scope, p, cursor, y)
                    remaining -= it.totalLength
                    cursor += it.width
                }
            }
        }

        is MathNode.Fraction -> {
            val num = buildStroke(node.num, measurer)
            val den = buildStroke(node.den, measurer)

            val width = maxOf(num.width, den.width) + 20f
            val height = num.height + den.height + 20f
            val total = num.totalLength + den.totalLength + width

            StrokeBox(width, height, total) { scope, progress, x, y ->
                var p = progress * total

                val numP = (p / num.totalLength).coerceIn(0f, 1f)
                num.draw(scope, numP, x + (width - num.width) / 2, y)
                p -= num.totalLength

                val lineP = (p / width).coerceIn(0f, 1f)
                scope.drawLine(
                    Color.Black,
                    Offset(x, y + num.height + 5),
                    Offset(x + width * lineP, y + num.height + 5),
                    strokeWidth = 2f
                )
                p -= width

                val denP = (p / den.totalLength).coerceIn(0f, 1f)
                den.draw(scope, denP, x + (width - den.width) / 2, y + num.height + 10)
            }
        }

        is MathNode.Sqrt -> {
            val inner = buildStroke(node.value, measurer)

            val width = inner.width + 25f
            val height = inner.height + 15f
            val total = inner.totalLength + width

            StrokeBox(width, height, total) { scope, progress, x, y ->
                val path = Path().apply {
                    moveTo(x, y + height - 5)
                    lineTo(x + 5, y + height)
                    lineTo(x + 10, y)
                    lineTo(x + width, y)
                }

                scope.drawPath(path, Color.Black, style = Stroke(2f))

                val innerP = if (inner.totalLength > 0) (progress * total / inner.totalLength).coerceIn(0f, 1f) else 1f
                inner.draw(scope, innerP, x + 15f, y + 5f)
            }
        }

        is MathNode.Power -> {
            val base = buildStroke(node.base, measurer)
            val exp = buildStroke(node.exp, measurer)

            val total = base.totalLength + exp.totalLength

            StrokeBox(
                base.width + exp.width,
                base.height + exp.height,
                total
            ) { scope, progress, x, y ->

                var p = progress * total

                val baseP = (p / base.totalLength).coerceIn(0f, 1f)
                base.draw(scope, baseP, x, y + exp.height * 0.3f)
                p -= base.totalLength

                val expP = if (exp.totalLength > 0) (p / exp.totalLength).coerceIn(0f, 1f) else 1f
                exp.draw(scope, expP, x + base.width, y)
            }
        }
    }
}