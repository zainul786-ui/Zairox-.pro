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
import com.example.ui.theme.CyanUltra
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HologramTeal
import com.example.ui.theme.ListeningCyan
import com.example.ui.theme.PlasmaViolet
import com.example.ui.theme.SpeakingBlue
import com.example.ui.theme.ThinkingAmber
import com.example.ui.theme.ThinkingGold
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
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_advanced_orb")

    // Slow clockwise outer HUD ring rotation
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_angle"
    )

    // Fast counter-clockwise inner segmented ring
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == VoiceState.THINKING) 2400 else 7000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_angle"
    )

    // Core breathing and plasma pulse
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    VoiceState.LISTENING -> 800
                    VoiceState.USER_SPEAKING -> 400
                    VoiceState.THINKING -> 450
                    VoiceState.JARVIS_SPEAKING -> 550
                    VoiceState.INTERRUPTED -> 300
                    VoiceState.RECONNECTING -> 700
                    else -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    // Acoustic wave oscillation phase
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == VoiceState.JARVIS_SPEAKING) 1000 else 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Color definitions based on state
    val primaryColor = when (voiceState) {
        VoiceState.LISTENING -> ListeningCyan
        VoiceState.USER_SPEAKING -> CyanUltra
        VoiceState.THINKING -> ThinkingGold
        VoiceState.JARVIS_SPEAKING -> SpeakingBlue
        VoiceState.INTERRUPTED -> Color(0xFFFF5252)
        VoiceState.RECONNECTING -> Color(0xFFFFAB40)
        VoiceState.ERROR -> ErrorRed
        VoiceState.READY, VoiceState.SESSION_ENDED -> CyanNeon
    }

    val secondaryColor = when (voiceState) {
        VoiceState.LISTENING -> CyanUltra
        VoiceState.USER_SPEAKING -> ElectricBlue
        VoiceState.THINKING -> ThinkingAmber
        VoiceState.JARVIS_SPEAKING -> HologramTeal
        VoiceState.INTERRUPTED -> Color(0xFFFF8A80)
        VoiceState.RECONNECTING -> ThinkingAmber
        VoiceState.ERROR -> Color(0xFFFF8A80)
        VoiceState.READY, VoiceState.SESSION_ENDED -> ElectricBlue
    }

    val tertiaryColor = when (voiceState) {
        VoiceState.LISTENING -> ElectricBlue
        VoiceState.USER_SPEAKING -> PlasmaViolet
        VoiceState.THINKING -> Color(0xFFFF6D00)
        VoiceState.JARVIS_SPEAKING -> PlasmaViolet
        VoiceState.INTERRUPTED -> Color(0xFFB71C1C)
        VoiceState.RECONNECTING -> PlasmaViolet
        VoiceState.ERROR -> Color(0xFFB71C1C)
        VoiceState.READY, VoiceState.SESSION_ENDED -> PlasmaViolet
    }

    Box(
        modifier = modifier
            .size(270.dp)
            .testTag("jarvis_orb_visualizer")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 135.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * 0.82f

            // Dynamic reactive radius boost
            val rmsBoost = when (voiceState) {
                VoiceState.LISTENING -> (audioRmsDb * 2.5f).coerceIn(0f, 18f)
                VoiceState.USER_SPEAKING -> (audioRmsDb * 4.5f).coerceIn(4f, 36f)
                VoiceState.JARVIS_SPEAKING -> (sin(wavePhase * 2) * 14f).coerceAtLeast(0f)
                VoiceState.THINKING -> (sin(wavePhase * 3) * 8f).coerceAtLeast(0f)
                VoiceState.INTERRUPTED -> 20f
                VoiceState.RECONNECTING -> 6f
                else -> 0f
            }
            val dynamicRadius = baseRadius + rmsBoost

            // 1. Ambient Hologram Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f),
                        secondaryColor.copy(alpha = 0.12f),
                        tertiaryColor.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * 1.15f
                ),
                radius = dynamicRadius * 1.15f,
                center = center
            )

            // 2. 360-Degree Circular Audio Spectrum / Frequency Equalizer Bars (40 Bars)
            val barCount = 40
            val barStep = (2 * PI / barCount).toFloat()
            val innerSpectrumRadius = dynamicRadius * 0.88f

            for (i in 0 until barCount) {
                val angle = i * barStep
                val harmonic = sin(angle * 5 + wavePhase) * cos(angle * 3)
                val barLength = when (voiceState) {
                    VoiceState.LISTENING -> 4f + (audioRmsDb * 2.5f * (0.6f + (harmonic * 0.4f).coerceAtLeast(0f))).coerceIn(2f, 20f)
                    VoiceState.USER_SPEAKING -> 6f + (audioRmsDb * 4.5f * (0.7f + (harmonic * 0.3f).coerceAtLeast(0f))).coerceIn(4f, 36f)
                    VoiceState.JARVIS_SPEAKING -> 6f + (sin(angle * 4 + wavePhase) * 14f).coerceIn(2f, 22f)
                    VoiceState.THINKING -> 5f + (sin(angle * 8 + wavePhase * 2) * 8f).coerceIn(1f, 12f)
                    VoiceState.INTERRUPTED -> 18f
                    VoiceState.RECONNECTING -> 6f
                    else -> 3f + (sin(angle * 2 + wavePhase) * 3f).coerceIn(1f, 6f)
                }

                val startX = center.x + innerSpectrumRadius * cos(angle)
                val startY = center.y + innerSpectrumRadius * sin(angle)
                val endX = center.x + (innerSpectrumRadius + barLength) * cos(angle)
                val endY = center.y + (innerSpectrumRadius + barLength) * sin(angle)

                val barColor = if (i % 2 == 0) primaryColor else secondaryColor
                drawLine(
                    color = barColor.copy(alpha = 0.75f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }

            // 3. Outer Segmented Tech Compass Arcs (Clockwise)
            drawSegmentedCompass(
                center = center,
                radius = dynamicRadius * 0.84f,
                startAngle = outerAngle,
                color = primaryColor.copy(alpha = 0.85f),
                segments = 6,
                arcLength = 34f,
                strokeWidth = 3f
            )

            // Cardinal Navigation Tick Marks
            drawCardinalTicks(
                center = center,
                radius = dynamicRadius * 0.84f,
                rotationAngle = outerAngle,
                color = CyanUltra.copy(alpha = 0.9f)
            )

            // 4. Middle Counter-Rotating Gear Ring (Counter-Clockwise)
            drawSegmentedCompass(
                center = center,
                radius = dynamicRadius * 0.68f,
                startAngle = innerAngle,
                color = secondaryColor.copy(alpha = 0.9f),
                segments = 4,
                arcLength = 52f,
                strokeWidth = 2.5f
            )

            // Inner Fine Track Circle
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                radius = dynamicRadius * 0.54f,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // 5. Orbital Particles / Spark Nodes
            val particleCount = 6
            for (p in 0 until particleCount) {
                val pAngle = outerAngle * (PI / 180f).toFloat() + (p * 2 * PI / particleCount).toFloat()
                val px = center.x + (dynamicRadius * 0.76f) * cos(pAngle)
                val py = center.y + (dynamicRadius * 0.76f) * sin(pAngle)

                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.6f),
                    radius = 5.5f,
                    center = Offset(px, py)
                )
            }

            // 6. Central Holographic Plasma Core
            val coreRadius = (dynamicRadius * 0.38f) * corePulse

            // Radial Plasma Bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f),
                        primaryColor.copy(alpha = 0.9f),
                        secondaryColor.copy(alpha = 0.5f),
                        tertiaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Core Glass Ring Reticle
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = coreRadius * 0.7f,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // Bright Center Node
            drawCircle(
                color = Color.White,
                radius = coreRadius * 0.28f,
                center = center
            )

            // Cross-hair Reticle Spikes
            val spikeLength = coreRadius * 0.45f
            val crossColor = primaryColor.copy(alpha = 0.85f)
            val strokeW = 2f

            drawLine(crossColor, Offset(center.x - coreRadius - spikeLength, center.y), Offset(center.x - coreRadius + 4f, center.y), strokeW)
            drawLine(crossColor, Offset(center.x + coreRadius - 4f, center.y), Offset(center.x + coreRadius + spikeLength, center.y), strokeW)
            drawLine(crossColor, Offset(center.x, center.y - coreRadius - spikeLength), Offset(center.x, center.y - coreRadius + 4f), strokeW)
            drawLine(crossColor, Offset(center.x, center.y + coreRadius - 4f), Offset(center.x, center.y + coreRadius + spikeLength), strokeW)
        }
    }
}

private fun DrawScope.drawSegmentedCompass(
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

private fun DrawScope.drawCardinalTicks(
    center: Offset,
    radius: Float,
    rotationAngle: Float,
    color: Color
) {
    val angles = listOf(0f, 90f, 180f, 270f)
    for (deg in angles) {
        val rad = (deg + rotationAngle) * (PI / 180f).toFloat()
        val x1 = center.x + (radius - 5f) * cos(rad)
        val y1 = center.y + (radius - 5f) * sin(rad)
        val x2 = center.x + (radius + 7f) * cos(rad)
        val y2 = center.y + (radius + 7f) * sin(rad)
        drawLine(
            color = color,
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = 2.5f,
            cap = StrokeCap.Square
        )
    }
}
