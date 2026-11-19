package com.rahulbaberwal.wattwarden.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WW.Surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = WW.Muted,
        letterSpacing = 1.2.sp,
    )
}

@Composable
fun Mono(text: String, size: TextUnit, color: Color = WW.Text, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        color = color,
    )
}

@Composable
fun StatTile(
    label: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        Label(label)
        Mono(value, 24.sp, color, Modifier.padding(top = 4.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = WW.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 270° arc gauge. [fraction] is 0..1. Content is drawn in the centre. */
@Composable
fun ArcGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "gauge")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = WW.SurfaceHi, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (anim > 0.005f) {
                drawArc(
                    color = color, startAngle = 135f, sweepAngle = 270f * anim, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Minimal dependency-free line chart with gradient fill. */
@Composable
fun LineChart(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Baseline grid
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(WW.SurfaceHi, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 1e-3f } ?: 1f
        val stepX = size.width / (values.size - 1)
        fun yOf(v: Float) = size.height * 0.95f - (v - min) / range * size.height * 0.85f

        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            if (i == 0) {
                line.moveTo(x, yOf(v))
                fill.moveTo(x, size.height)
                fill.lineTo(x, yOf(v))
            } else {
                line.lineTo(x, yOf(v))
                fill.lineTo(x, yOf(v))
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
