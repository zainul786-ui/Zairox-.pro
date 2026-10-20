package com.example.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VoiceState
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassSurface
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
        VoiceState.LISTENING -> "LISTENING..."
        VoiceState.THINKING -> "PROCESSING..."
        VoiceState.SPEAKING -> "VOCALIZING..."
        VoiceState.ERROR -> "SYSTEM NOTICE"
        VoiceState.IDLE -> "SYSTEM READY"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_indicator")
    val dotAlpha by infiniteTransition.animateColor(
        initialValue = stateColor.copy(alpha = 0.35f),
        targetValue = stateColor.copy(alpha = 1.0f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha_anim"
    )

    Row(
        modifier = modifier
            .testTag("status_badge")
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSurface)
            .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotAlpha)
        )
        Text(
            text = labelText,
            color = stateColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )
    }
}
