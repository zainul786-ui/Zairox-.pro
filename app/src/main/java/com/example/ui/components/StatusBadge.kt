package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VoiceState
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.GlassSurfaceLight
import com.example.ui.theme.ListeningCyan
import com.example.ui.theme.SpeakingBlue
import com.example.ui.theme.ThinkingAmber

@Composable
fun StatusBadge(
    voiceState: VoiceState,
    modifier: Modifier = Modifier
) {
    val stateColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.LISTENING -> ListeningCyan
            VoiceState.THINKING -> ThinkingAmber
            VoiceState.SPEAKING -> SpeakingBlue
            VoiceState.ERROR -> ErrorRed
            VoiceState.IDLE -> CyanNeon
        },
        label = "status_color_anim"
    )

    val labelText = when (voiceState) {
        VoiceState.LISTENING -> "RECEPTIVE // LISTENING"
        VoiceState.THINKING -> "COMPUTING // SYNTHESIZING"
        VoiceState.SPEAKING -> "VOCALIZING // TRANSMITTING"
        VoiceState.ERROR -> "SYSTEM NOTICE // ALERT"
        VoiceState.IDLE -> "SYSTEM READY // STANDBY"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_indicator")
    val dotAlpha by infiniteTransition.animateColor(
        initialValue = stateColor.copy(alpha = 0.35f),
        targetValue = stateColor.copy(alpha = 1.0f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha_anim"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_halo"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_halo_alpha"
    )

    Box(
        modifier = modifier
            .testTag("status_badge")
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        GlassSurfaceDark,
                        Color(0xE608142E),
                        GlassSurfaceDark
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        stateColor.copy(alpha = 0.7f),
                        stateColor.copy(alpha = 0.2f),
                        stateColor.copy(alpha = 0.6f)
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // High-Tech LED with pulsing ripple halo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(stateColor.copy(alpha = pulseAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotAlpha)
                )
            }

            Text(
                text = labelText,
                color = stateColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.8.sp
            )
        }
    }
}
