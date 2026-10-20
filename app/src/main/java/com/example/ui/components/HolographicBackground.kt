package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.PlasmaViolet
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.SpaceVoid
import kotlin.math.sin

private data class StarParticle(
    val xRatio: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val phase: Float
)

@Composable
fun HolographicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "holographic_bg_anim")

    // Slow atmospheric ambient aura pulse
    val auraOffset by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_movement"
    )

    // Particles vertical flow
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_flow"
    )

    // Stable randomized particles
    val particles = remember {
        List(28) { index ->
            StarParticle(
                xRatio = (index * 0.037f) % 1f,
                speed = 0.6f + ((index * 7) % 10) * 0.08f,
                size = 1.5f + ((index * 3) % 4) * 0.8f,
                alpha = 0.25f + ((index * 5) % 6) * 0.12f,
                phase = (index * 0.2f)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Deep Space Base
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(SpaceVoid, SpaceBlack, Color(0xFF060D1E))
                )
            )

            // 2. Ambient Shifting Holographic Aura Behind Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CyanNeon.copy(alpha = 0.12f),
                        ElectricBlue.copy(alpha = 0.06f),
                        PlasmaViolet.copy(alpha = 0.02f),
                        Color.Transparent
                    ),
                    center = Offset(width / 2f + auraOffset, height * 0.35f + auraOffset * 0.5f),
                    radius = width * 0.75f
                ),
                radius = width * 0.75f,
                center = Offset(width / 2f + auraOffset, height * 0.35f + auraOffset * 0.5f)
            )

            // 3. Perspective Cyber Grid Lines (Bottom Area)
            val gridStartY = height * 0.65f
            val gridLines = 8
            for (i in 0 until gridLines) {
                val ratio = i.toFloat() / gridLines
                val y = gridStartY + (ratio * ratio) * (height - gridStartY)
                val lineAlpha = 0.02f + (ratio * 0.06f)
                drawLine(
                    color = CyanNeon.copy(alpha = lineAlpha),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            // 4. Floating Holographic Quantum Particles
            particles.forEach { p ->
                val progress = (particleProgress * p.speed + p.phase) % 1f
                val y = height * (1f - progress)
                val xWiggle = sin(progress * 6f + p.phase) * 18f
                val x = (width * p.xRatio) + xWiggle

                drawCircle(
                    color = CyanNeon.copy(alpha = p.alpha * (1f - (progress - 0.5f) * (progress - 0.5f) * 4f).coerceIn(0f, 1f)),
                    radius = p.size,
                    center = Offset(x, y)
                )
            }

            // 5. Tech Corner Reticles / HUD Brackets
            val bracketSize = 24f
            val margin = 20f
            val bracketColor = CyanNeon.copy(alpha = 0.3f)
            val bracketStroke = 1.5f

            // Top-Left ┌
            drawLine(bracketColor, Offset(margin, margin), Offset(margin + bracketSize, margin), bracketStroke)
            drawLine(bracketColor, Offset(margin, margin), Offset(margin, margin + bracketSize), bracketStroke)

            // Top-Right ┐
            drawLine(bracketColor, Offset(width - margin, margin), Offset(width - margin - bracketSize, margin), bracketStroke)
            drawLine(bracketColor, Offset(width - margin, margin), Offset(width - margin, margin + bracketSize), bracketStroke)

            // Bottom-Left └
            drawLine(bracketColor, Offset(margin, height - margin), Offset(margin + bracketSize, height - margin), bracketStroke)
            drawLine(bracketColor, Offset(margin, height - margin), Offset(margin, height - margin - bracketSize), bracketStroke)

            // Bottom-Right ┘
            drawLine(bracketColor, Offset(width - margin, height - margin), Offset(width - margin - bracketSize, height - margin), bracketStroke)
            drawLine(bracketColor, Offset(width - margin, height - margin), Offset(width - margin, height - margin - bracketSize), bracketStroke)
        }

        content()
    }
}
