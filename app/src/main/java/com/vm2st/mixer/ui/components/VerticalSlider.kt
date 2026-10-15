package com.vm2st.mixer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vm2st.mixer.data.SliderMode
import kotlin.math.abs

/** Длительность мягкого движения слайдера в режиме «Плавно», мс. */
private const val DRAG_FOLLOW_MS = 300
private const val RELEASE_SETTLE_MS = 160

/**
 * Вертикальный слайдер с двумя режимами:
 *  - FAST   — значение идёт ровно за пальцем;
 *  - SMOOTH — значение мягко догоняет палец и быстро доходит до цели, когда палец отпущен.
 * Долгое нажатие — сброс к значению по умолчанию.
 */
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    accent: Color,
    onAccent: Color,
    trackColor: Color,
    title: String,
    valueText: String,
    syncTick: Int,
    mode: SliderMode,
    onModeChange: (SliderMode) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animated = remember { Animatable(value.coerceIn(0f, 1f)) }
    var target by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    var dragging by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnReset by rememberUpdatedState(onReset)
    val smooth = mode == SliderMode.SMOOTH

    // Внешнее изменение: пресет, сброс, синхронизация с системной громкостью.
    LaunchedEffect(syncTick) {
        if (syncTick > 0) target = value.coerceIn(0f, 1f)
    }

    // Режим «Быстро»: значение идёт ровно за пальцем.
    LaunchedEffect(target, smooth) {
        if (!smooth) animated.snapTo(target.coerceIn(0f, 1f))
    }

    // Режим «Плавно»: мягкое движение с постоянной длительностью —
    // слайдер отстаёт от пальца, но доводит ход сразу после отпускания.
    LaunchedEffect(target, smooth, dragging) {
        if (!smooth) return@LaunchedEffect
        val clamped = target.coerceIn(0f, 1f)
        val distance = abs(clamped - animated.value)
        if (distance < 0.002f) {
            animated.snapTo(clamped)
        } else {
            animated.animateTo(
                targetValue = clamped,
                animationSpec = tween(
                    durationMillis = if (dragging) DRAG_FOLLOW_MS else RELEASE_SETTLE_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { animated.value }.collect { currentOnValueChange(it.coerceIn(0f, 1f)) }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    fun valueAt(y: Float): Float =
                        (1f - (y / size.height.toFloat())).coerceIn(0f, 1f)

                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            target = valueAt(offset.y)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            target = valueAt(change.position.y)
                        }
                    )
                }
                .pointerInput(Unit) {
                    fun valueAt(y: Float): Float =
                        (1f - (y / size.height.toFloat())).coerceIn(0f, 1f)

                    detectTapGestures(
                        onTap = { offset -> target = valueAt(offset.y) },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnReset()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawVerticalSlider(
                    value = animated.value.coerceIn(0f, 1f),
                    accent = accent,
                    onAccent = onAccent,
                    track = trackColor,
                    dragging = dragging
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(trackColor)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeSegment(
                text = "Быстро",
                selected = !smooth,
                accent = accent,
                onAccent = onAccent,
                onClick = {
                    if (smooth) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onModeChange(SliderMode.FAST)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            ModeSegment(
                text = "Плавно",
                selected = smooth,
                accent = accent,
                onAccent = onAccent,
                onClick = {
                    if (!smooth) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onModeChange(SliderMode.SMOOTH)
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeSegment(
    text: String,
    selected: Boolean,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.Transparent)
            .pointerInput(selected) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun DrawScope.drawVerticalSlider(
    value: Float,
    accent: Color,
    onAccent: Color,
    track: Color,
    dragging: Boolean
) {
    val trackWidth = 10.dp.toPx()
    val knobRadius = (if (dragging) 16.dp else 13.dp).toPx()
    val centerX = size.width / 2f
    val trackTop = knobRadius + 2.dp.toPx()
    val trackBottom = size.height - knobRadius - 2.dp.toPx()
    val trackHeight = (trackBottom - trackTop).coerceAtLeast(1f)

    drawRoundRect(
        color = track,
        topLeft = Offset(centerX - trackWidth / 2f, trackTop),
        size = Size(trackWidth, trackHeight),
        cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
    )

    val tickX = centerX + trackWidth / 2f + 4.dp.toPx()
    for (step in 0..10) {
        val y = trackBottom - trackHeight * step / 10f
        val length = if (step % 5 == 0) 11.dp.toPx() else 6.dp.toPx()
        drawLine(
            color = track,
            start = Offset(tickX, y),
            end = Offset(tickX + length, y),
            strokeWidth = 1.4.dp.toPx()
        )
    }

    val knobY = trackBottom - trackHeight * value
    val fillHeight = (trackBottom - knobY).coerceAtLeast(0f)
    if (fillHeight > 0f) {
        drawRoundRect(
            color = accent,
            topLeft = Offset(centerX - trackWidth / 2f, knobY),
            size = Size(trackWidth, fillHeight),
            cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
        )
    }

    drawCircle(color = accent.copy(alpha = 0.16f), radius = knobRadius * 2f, center = Offset(centerX, knobY))
    drawCircle(color = accent.copy(alpha = 0.28f), radius = knobRadius * 1.45f, center = Offset(centerX, knobY))
    drawCircle(color = accent, radius = knobRadius, center = Offset(centerX, knobY))
    drawCircle(color = onAccent.copy(alpha = 0.9f), radius = knobRadius * 0.32f, center = Offset(centerX, knobY))
}
