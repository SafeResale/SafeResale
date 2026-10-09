package com.saferesale.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A lightweight live line graph that accepts a list of [values] in 0..100 range
 * and draws a smooth gradient-filled curve.
 */
@Composable
fun LiveLineGraph(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    label: String = "",
    showCurrentValue: Boolean = true,
    maxValue: Float = 100f,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    Box(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = color.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                ),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp)
            )
        }
        if (showCurrentValue && values.isNotEmpty()) {
            Text(
                text = "${values.last().toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = color,
                ),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 2.dp)
            )
        }
        Canvas(modifier = Modifier.fillMaxSize().padding(top = if (label.isNotEmpty()) 18.dp else 0.dp)) {
            if (values.size < 2) return@Canvas
            drawGrid(onSurface)
            drawLine(values, color, maxValue)
        }
    }
}

private fun DrawScope.drawGrid(color: Color) {
    val rows = 4
    for (i in 0..rows) {
        val y = size.height * i / rows
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawLine(values: List<Float>, color: Color, maxValue: Float) {
    if (values.size < 2) return
    val step = size.width / (values.size - 1).toFloat()
    val pts = values.mapIndexed { i, v ->
        Offset(i * step, size.height - (v / maxValue * size.height).coerceIn(0f, size.height))
    }

    // Fill path
    val path = Path().apply {
        moveTo(pts.first().x, size.height)
        lineTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) {
            val cp1 = Offset((pts[i - 1].x + pts[i].x) / 2, pts[i - 1].y)
            val cp2 = Offset((pts[i - 1].x + pts[i].x) / 2, pts[i].y)
            cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, pts[i].x, pts[i].y)
        }
        lineTo(pts.last().x, size.height)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.0f))
        )
    )

    // Stroke
    val strokePath = Path().apply {
        moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) {
            val cp1 = Offset((pts[i - 1].x + pts[i].x) / 2, pts[i - 1].y)
            val cp2 = Offset((pts[i - 1].x + pts[i].x) / 2, pts[i].y)
            cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, pts[i].x, pts[i].y)
        }
    }
    drawPath(strokePath, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f, cap = StrokeCap.Round))
}
