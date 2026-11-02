package com.callbook.journal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.Candle
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Faint
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Profit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val StudyChartHeight = 220.dp

private data class ChartFrame(val origin: Float, val window: Float)

@Composable
fun CandleChart(
    candles: List<Candle>,
    resetKey: Any,
    modifier: Modifier = Modifier,
) {
    if (candles.size < 2) return
    val fitted = ChartFrame(0f, candles.size.toFloat())
    val frameState = remember(resetKey) { mutableStateOf(fitted) }
    var frame by frameState
    val countState = rememberUpdatedState(candles.size)
    val count = candles.size.toFloat()
    val minWindow = min(5f, count)
    val window = frame.window.coerceIn(minWindow, count)
    val origin = frame.origin.coerceIn(0f, (count - window).coerceAtLeast(0f))
    val zoomed = window < count - 0.5f
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(StudyChartHeight)
                .pointerInput(resetKey) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val pinching = event.changes.count { it.pressed } >= 2 || abs(zoom - 1f) > 0.01f
                            val sideways = abs(pan.x) > abs(pan.y) * 1.2f && abs(pan.x) > 1.5f
                            if (!pinching && !sideways) continue
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                            val current = frameState.value
                            val seen = countState.value.toFloat()
                            val floor = min(5f, seen)
                            var nextWindow = current.window.coerceIn(floor, seen)
                            var nextOrigin = current.origin
                            if (abs(zoom - 1f) > 0.01f) {
                                val widened = (nextWindow / zoom).coerceIn(floor, seen)
                                val mid = nextOrigin + nextWindow / 2f
                                nextWindow = widened
                                nextOrigin = mid - widened / 2f
                            }
                            if (sideways && size.width > 0f) {
                                nextOrigin -= pan.x / (size.width / nextWindow)
                            }
                            val maxOrigin = (seen - nextWindow).coerceAtLeast(0f)
                            frameState.value = ChartFrame(nextOrigin.coerceIn(0f, maxOrigin), nextWindow)
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val from = origin.toInt().coerceIn(0, candles.lastIndex)
            val to = (origin + window).toInt().coerceIn(from + 1, candles.size)
            val shown = candles.subList(from, to)
            var low = shown.minOf { it.low }
            var high = shown.maxOf { it.high }
            val pad = ((high - low) * 0.08).takeIf { it > 0.0001 } ?: 1.0
            low -= pad
            high += pad
            val span = (high - low).takeIf { it > 0.0001 } ?: 1.0
            val slot = size.width / window
            val body = (slot * 0.62f).coerceAtLeast(1.5f)
            candles.forEachIndexed { index, candle ->
                val x = (index - origin) * slot + slot / 2f
                if (x < -slot || x > size.width + slot) return@forEachIndexed
                fun y(price: Double) = (size.height * (1.0 - (price - low) / span)).toFloat()
                val up = candle.close >= candle.open
                val color = if (up) Profit else Loss
                drawLine(color, Offset(x, y(candle.high)), Offset(x, y(candle.low)), strokeWidth = 1.6f)
                val top = y(max(candle.open, candle.close))
                val bottom = y(min(candle.open, candle.close))
                drawRect(
                    color = color,
                    topLeft = Offset(x - body / 2f, top),
                    size = Size(body, (bottom - top).coerceAtLeast(1.5f)),
                )
            }
        }
        if (candles.size > 5) {
            Text(
                text = if (zoomed) "Fit" else "Pinch to zoom · drag to move",
                modifier = Modifier
                    .padding(top = 4.dp)
                    .then(if (zoomed) Modifier.clickable { frame = fitted } else Modifier),
                color = if (zoomed) Accent else Faint,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            )
        }
    }
}
