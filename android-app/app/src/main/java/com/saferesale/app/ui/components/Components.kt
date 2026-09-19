package com.saferesale.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.saferesale.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferesale.app.ui.theme.DarkOutline

// ── Glass Card ─────────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        content = content
    )
}

// ── Section Header ──────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        ),
        modifier = modifier.padding(bottom = 8.dp)
    )
}

// ── Info Row ────────────────────────────────────────────────────────────────────
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
        )
    }
}

// ── Progress Bar Row ────────────────────────────────────────────────────────────
@Composable
fun ProgressBarRow(
    label: String,
    percent: Float,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = "${percent.toInt()}%",
) {
    val animPct by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "progressAnim"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(suffix, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = color)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animPct)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(color.copy(alpha = 0.7f), color)
                        )
                    )
            )
        }
    }
}

// ── Arc Gauge ───────────────────────────────────────────────────────────────────
@Composable
fun ArcGauge(
    percent: Float,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 12.dp,
) {
    val animPct by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "gaugeAnim"
    )
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                // Always guarantee a 1:1 circular aspect ratio regardless of container bounds
                val diameter = minOf(this.size.width, this.size.height) - stroke
                val left = (this.size.width - diameter) / 2f
                val top = (this.size.height - diameter) / 2f
                val sweepAngle = 240f * animPct

                // Track arc
                drawArc(
                    color = trackColor,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(left, top),
                    size = Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                // Progress arc
                if (animPct > 0f) {
                    drawArc(
                        color = color,
                        startAngle = 150f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(left, top),
                        size = Size(diameter, diameter),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val titleStyle = if (size < 90.dp) {
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                }
                Text(
                    "${percent.toInt()}%",
                    style = titleStyle,
                    color = color,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ── Status Chip ──────────────────────────────────────────────────────────────────
@Composable
fun StatusChip(
    text: String,
    isGood: Boolean?,
    modifier: Modifier = Modifier,
) {
    val bg = when (isGood) {
        true  -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        false -> Color(0xFFF44336).copy(alpha = 0.15f)
        null  -> Color(0xFF78909C).copy(alpha = 0.15f)
    }
    val fg = when (isGood) {
        true  -> Color(0xFF4CAF50)
        false -> Color(0xFFF44336)
        null  -> Color(0xFF78909C)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(color = fg, fontWeight = FontWeight.SemiBold))
    }
}

// ── CoreV Loading Indicator ───────────────────────────────────────────────────
@Composable
fun CoreVLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    message: String? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loadingRing")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF6C63FF).copy(alpha = 0.3f),
                            Color(0xFF00D4AA),
                            Color(0xFF6C63FF)
                        )
                    ),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Image(
                painter = painterResource(R.mipmap.ic_launcher_round),
                contentDescription = "Loading",
                modifier = Modifier
                    .size(size * 0.72f)
                    .scale(pulse)
                    .clip(CircleShape)
            )
        }
        if (message != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

