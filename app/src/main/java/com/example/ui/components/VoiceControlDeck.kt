package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import com.example.ui.theme.CyanUltra
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderElectric
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.GlassSurfaceLight
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

    val infiniteTransition = rememberInfiniteTransition(label = "deck_anim")

    // Ripple wave 1 for glowing central mic
    val rippleScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_scale_1"
    )

    val rippleAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha_1"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expandable Cyber Console Text Input Drawer
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
                            "Enter tactical terminal query...",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = GlassBorderCyan,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = CyanNeon,
                        focusedContainerColor = Color(0xF00A142D),
                        unfocusedContainerColor = Color(0xD9060C1B)
                    ),
                    shape = RoundedCornerShape(20.dp),
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
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(CyanNeon, ElectricBlue))
                        )
                        .testTag("send_text_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send text to JARVIS",
                        tint = DeepMidnight,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Floating Frosted Glass Capsule Dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xDD09142E),
                            Color(0xF2040916)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            GlassBorderCyan.copy(alpha = 0.6f),
                            GlassBorderElectric.copy(alpha = 0.2f),
                            GlassBorderCyan.copy(alpha = 0.5f)
                        )
                    ),
                    RoundedCornerShape(36.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left 1: Keyboard Drawer Toggle
                IconButton(
                    onClick = { isKeyboardOpen = !isKeyboardOpen },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isKeyboardOpen) GlassSurfaceLight else Color.Transparent)
                        .testTag("keyboard_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isKeyboardOpen) Icons.Default.Close else Icons.Default.Keyboard,
                        contentDescription = "Toggle Keyboard Input",
                        tint = if (isKeyboardOpen) CyanNeon else TextSilver,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Left 2: Continuous Conversation Loop Toggle
                IconButton(
                    onClick = onToggleContinuousMode,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isContinuousMode) Color(0x3300F2FE) else Color.Transparent)
                        .testTag("continuous_mode_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Toggle Continuous Mode",
                            tint = if (isContinuousMode) CyanNeon else TextSilver,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isContinuousMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(CyanNeon)
                            )
                        }
                    }
                }

                // Center: Monumental Glowing Arc Reactor Mic
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(76.dp)
                ) {
                    // Outward pulsing energy ripples when active
                    if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.SPEAKING) {
                        val activeColor = if (voiceState == VoiceState.LISTENING) ListeningCyan else SpeakingBlue
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(rippleScale1)
                                .clip(CircleShape)
                                .background(activeColor.copy(alpha = rippleAlpha1))
                        )
                    }

                    IconButton(
                        onClick = onMicClick,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                when (voiceState) {
                                    VoiceState.LISTENING -> Brush.verticalGradient(listOf(CyanUltra, ListeningCyan, ElectricBlue))
                                    VoiceState.THINKING -> Brush.verticalGradient(listOf(Color(0xFFFFD54F), ThinkingAmber, Color(0xFFFF8F00)))
                                    VoiceState.SPEAKING -> Brush.verticalGradient(listOf(CyanNeon, SpeakingBlue, Color(0xFF0052D4)))
                                    VoiceState.ERROR -> Brush.verticalGradient(listOf(Color(0xFFFF8A80), ErrorRed, Color(0xFFB71C1C)))
                                    VoiceState.IDLE -> Brush.verticalGradient(listOf(CyanNeon, ElectricBlue, Color(0xFF0052D4)))
                                }
                            )
                            .border(
                                2.dp,
                                Brush.verticalGradient(listOf(Color.White, CyanNeon.copy(alpha = 0.7f))),
                                CircleShape
                            )
                            .testTag("primary_mic_button")
                    ) {
                        Icon(
                            imageVector = if (voiceState == VoiceState.SPEAKING) Icons.Default.VolumeOff else Icons.Default.Mic,
                            contentDescription = "Voice Conversation Button",
                            tint = DeepMidnight,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Right 1: Stop / Mute Speaking
                IconButton(
                    onClick = onStopSpeaking,
                    enabled = voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (voiceState == VoiceState.SPEAKING) Color(0x33FF3366) else Color.Transparent)
                        .testTag("stop_speaking_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Speaking",
                        tint = if (voiceState == VoiceState.SPEAKING) ErrorRed else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Right 2: End Active Session
                IconButton(
                    onClick = onEndSession,
                    enabled = isSessionActive || voiceState != VoiceState.IDLE,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isSessionActive) GlassSurfaceLight else Color.Transparent)
                        .testTag("end_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "End Voice Session",
                        tint = if (isSessionActive) TextSilver else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Sub-Dock Telemetry Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isContinuousMode) "MODE: CONTINUOUS ECHO // ACTIVE" else "MODE: PUSH-TO-TALK // MANUAL",
                color = if (isContinuousMode) CyanNeon.copy(alpha = 0.85f) else TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
            Text(
                text = if (isSessionActive) "SESSION: ENGAGED" else "SESSION: STANDBY",
                color = if (isSessionActive) ElectricBlue.copy(alpha = 0.85f) else TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
        }
    }
}
