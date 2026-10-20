package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.VoiceState
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ListeningCyan
import com.example.ui.theme.SpeakingBlue
import com.example.ui.theme.ThinkingAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisOrbVisualizer(
    voiceState: VoiceState,
    audioRmsDb: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb_animations")

    // Slow rotation for outer tech arcs
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )

    // Reverse rotation for inner segmented ring
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == VoiceState.THINKING) 3000 else 8000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )

    // Breathing pulse for core
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.LISTENING -> 800
                    VoiceState.THINKING -> 600
                    VoiceState.SPEAKING -> 700
                    else -> 2200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    // Speaking sound wave oscillation phase
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val primaryColor = when (voiceState) {
        VoiceState.LISTENING -> ListeningCyan
        VoiceState.THINKING -> ThinkingAmber
        VoiceState.SPEAKING -> SpeakingBlue
        VoiceState.ERROR -> ErrorRed
        VoiceState.IDLE -> CyanNeon
    }

    val secondaryColor = when (voiceState) {
        VoiceState.LISTENING -> ElectricBlue
        VoiceState.THINKING -> Color(0xFFFFD54F)
        VoiceState.SPEAKING -> CyanNeon
        VoiceState.ERROR -> Color(0xFFFF8A80)
        VoiceState.IDLE -> ElectricBlue
    }

    Box(
        modifier = modifier
            .size(260.dp)
            .testTag("jarvis_orb_visualizer")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 130.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * 0.85f

            // 1. Outermost audio reactive wave when listening or speaking
            val dynamicRadius = if (voiceState == VoiceState.LISTENING) {
                baseRadius + (audioRmsDb * 3.5f).coerceIn(0f, 28f)
            } else if (voiceState == VoiceState.SPEAKING) {
                baseRadius + (pulseScale - 1f) * 40f
            } else {
                baseRadius
            }

            // Outer energy aura ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.25f),
                        primaryColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius
                ),
                radius = dynamicRadius,
                center = center
            )

            // 2. Segmented Outer Geometric Arcs
            drawSegmentedArcs(
                center = center,
                radius = dynamicRadius * 0.92f,
                startAngle = outerAngle,
                color = primaryColor.copy(alpha = 0.7f),
                segments = 6,
                arcLength = 36f,
                strokeWidth = 3f
            )

            // 3. Middle Counter-Rotating Track
            drawSegmentedArcs(
                center = center,
                radius = dynamicRadius * 0.74f,
                startAngle = innerAngle,
                color = secondaryColor.copy(alpha = 0.85f),
                segments = 4,
                arcLength = 54f,
                strokeWidth = 2.5f
            )

            // 4. Harmonic Audio Wave Ring (Speaking / Listening Mode)
            if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.LISTENING) {
                drawWaveRing(
                    center = center,
                    radius = dynamicRadius * 0.58f,
                    wavePhase = wavePhase,
                    amplitude = if (voiceState == VoiceState.LISTENING) (audioRmsDb * 1.5f).coerceIn(2f, 12f) else 8f,
                    color = primaryColor
                )
            } else {
                // Static thin core track
                drawCircle(
                    color = primaryColor.copy(alpha = 0.4f),
                    radius = dynamicRadius * 0.58f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // 5. Glowing Central Arc Reactor Core
            val coreRadius = (dynamicRadius * 0.42f) * pulseScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        primaryColor.copy(alpha = 0.85f),
                        secondaryColor.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Inner core bright dot
            drawCircle(
                color = Color.White,
                radius = coreRadius * 0.22f,
                center = center
            )

            // Cross node markers on core
            val markerLength = coreRadius * 0.35f
            val nodeColor = primaryColor.copy(alpha = 0.8f)
            drawLine(
                color = nodeColor,
                start = Offset(center.x - coreRadius - markerLength, center.y),
                end = Offset(center.x - coreRadius + 2f, center.y),
                strokeWidth = 2f
            )
            drawLine(
                color = nodeColor,
                start = Offset(center.x + coreRadius - 2f, center.y),
                end = Offset(center.x + coreRadius + markerLength, center.y),
                strokeWidth = 2f
            )
            drawLine(
                color = nodeColor,
                start = Offset(center.x, center.y - coreRadius - markerLength),
                end = Offset(center.x, center.y - coreRadius + 2f),
                strokeWidth = 2f
            )
            drawLine(
                color = nodeColor,
                start = Offset(center.x, center.y + coreRadius - 2f),
                end = Offset(center.x, center.y + coreRadius + markerLength),
                strokeWidth = 2f
            )
        }
    }
}

private fun DrawScope.drawSegmentedArcs(
    center: Offset,
    radius: Float,
    startAngle: Float,
    color: Color,
    segments: Int,
    arcLength: Float,
    strokeWidth: Float
) {
    val step = 360f / segments
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val size = Size(radius * 2f, radius * 2f)

    for (i in 0 until segments) {
        val currentStart = startAngle + (i * step)
        drawArc(
            color = color,
            startAngle = currentStart,
            sweepAngle = arcLength,
            useCenter = false,
            topLeft = topLeft,
            size = size,
            style = stroke
        )
    }
}

private fun DrawScope.drawWaveRing(
    center: Offset,
    radius: Float,
    wavePhase: Float,
    amplitude: Float,
    color: Color
) {
    val pointCount = 60
    var previousPoint: Offset? = null
    var firstPoint: Offset? = null

    for (i in 0..pointCount) {
        val theta = (i.toFloat() / pointCount) * 2 * PI.toFloat()
        val waveMod = sin(theta * 6 + wavePhase) * amplitude
        val r = radius + waveMod
        val x = center.x + r * cos(theta)
        val y = center.y + r * sin(theta)
        val point = Offset(x, y)

        if (firstPoint == null) firstPoint = point
        if (previousPoint != null) {
            drawLine(
                color = color.copy(alpha = 0.9f),
                start = previousPoint,
                end = point,
                strokeWidth = 2.5f
            )
        }
        previousPoint = point
    }

    if (firstPoint != null && previousPoint != null) {
        drawLine(
            color = color.copy(alpha = 0.9f),
            start = previousPoint,
            end = firstPoint,
            strokeWidth = 2.5f
        )
    }
}
