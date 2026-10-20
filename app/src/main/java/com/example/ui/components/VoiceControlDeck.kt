package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VoiceState
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.ListeningCyan
import com.example.ui.theme.SpeakingBlue
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSilver
import com.example.ui.theme.TextWhite
import com.example.ui.theme.ThinkingAmber

@Composable
fun VoiceControlDeck(
    voiceState: VoiceState,
    isSessionActive: Boolean,
    isContinuousMode: Boolean,
    onMicClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    onEndSession: () -> Unit,
    onToggleContinuousMode: () -> Unit,
    onSendTextMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isKeyboardOpen by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState == VoiceState.LISTENING) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale_anim"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expandable Text Chat Drawer
        AnimatedVisibility(
            visible = isKeyboardOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            "Transmit text query to JARVIS...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = CyanNeon,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textInput.isNotBlank()) {
                                onSendTextMessage(textInput)
                                textInput = ""
                                isKeyboardOpen = false
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("text_input_field"),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSendTextMessage(textInput)
                            textInput = ""
                            isKeyboardOpen = false
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CyanNeon)
                        .testTag("send_text_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send text to JARVIS",
                        tint = DeepMidnight,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Primary Control Deck Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Action: Keyboard / Text mode toggle
            IconButton(
                onClick = { isKeyboardOpen = !isKeyboardOpen },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isKeyboardOpen) SurfaceVariantDark else Color.Transparent)
                    .testTag("keyboard_toggle_button")
            ) {
                Icon(
                    imageVector = if (isKeyboardOpen) Icons.Default.Close else Icons.Default.Keyboard,
                    contentDescription = "Toggle Keyboard Input",
                    tint = if (isKeyboardOpen) CyanNeon else TextSilver,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Left secondary: Continuous mode toggle
            IconButton(
                onClick = onToggleContinuousMode,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isContinuousMode) CyanGlow else Color.Transparent)
                    .testTag("continuous_mode_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Toggle Continuous Mode",
                    tint = if (isContinuousMode) CyanNeon else TextSilver,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Center Primary Glowing Microphone Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                // Outer glow halo when listening
                if (voiceState == VoiceState.LISTENING) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(micScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        ListeningCyan.copy(alpha = 0.5f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            when (voiceState) {
                                VoiceState.LISTENING -> Brush.verticalGradient(listOf(ListeningCyan, ElectricBlue))
                                VoiceState.THINKING -> Brush.verticalGradient(listOf(ThinkingAmber, Color(0xFFFF8F00)))
                                VoiceState.SPEAKING -> Brush.verticalGradient(listOf(SpeakingBlue, CyanNeon))
                                VoiceState.ERROR -> Brush.verticalGradient(listOf(ErrorRed, Color(0xFFC2185B)))
                                VoiceState.IDLE -> Brush.verticalGradient(listOf(CyanNeon, ElectricBlue))
                            }
                        )
                        .border(
                            2.dp,
                            if (voiceState == VoiceState.LISTENING) Color.White else CyanNeon.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .testTag("primary_mic_button")
                ) {
                    Icon(
                        imageVector = if (voiceState == VoiceState.SPEAKING) Icons.Default.VolumeOff else Icons.Default.Mic,
                        contentDescription = "Voice Conversation Button",
                        tint = DeepMidnight,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Right Action: Stop / Mute speaking
            IconButton(
                onClick = onStopSpeaking,
                enabled = voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (voiceState == VoiceState.SPEAKING) ErrorRed.copy(alpha = 0.2f) else Color.Transparent)
                    .testTag("stop_speaking_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Speaking",
                    tint = if (voiceState == VoiceState.SPEAKING) ErrorRed else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Right secondary: End Active Session
            IconButton(
                onClick = onEndSession,
                enabled = isSessionActive || voiceState != VoiceState.IDLE,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSessionActive) SurfaceVariantDark else Color.Transparent)
                    .testTag("end_session_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "End Voice Session",
                    tint = if (isSessionActive) TextSilver else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Active Status indicator helper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isContinuousMode) "LOOP: CONTINUOUS" else "LOOP: PUSH-TO-TALK",
                color = if (isContinuousMode) CyanNeon.copy(alpha = 0.8f) else TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = if (isSessionActive) "SESSION: ACTIVE" else "SESSION: STANDBY",
                color = if (isSessionActive) ElectricBlue.copy(alpha = 0.8f) else TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}
