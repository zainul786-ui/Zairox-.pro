package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.CyanUltra
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderElectric
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.GlassSurfaceLight
import com.example.ui.theme.TextCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSilver
import com.example.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TranscriptView(
    messages: List<ChatMessage>,
    partialTranscript: String,
    onSpeakMessage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(messages.size, partialTranscript) {
        if (messages.isNotEmpty() || partialTranscript.isNotEmpty()) {
            val targetIndex = (messages.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Box(modifier = modifier.testTag("transcript_view")) {
        if (messages.isEmpty() && partialTranscript.isEmpty()) {
            // High-Tech Stark HUD Empty State
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x2200F2FE),
                                    Color(0x08060D1E)
                                )
                            )
                        )
                        .border(1.dp, GlassBorderCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "JARVIS NEURAL CORE // ONLINE",
                                color = CyanNeon,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Awaiting audio input. Speak freely or type queries below. Continuous conversation protocol active.",
                            color = TextSilver,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(messages, key = { it.id }) { message ->
                    FrostedChatMessageCard(
                        message = message,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onSpeak = onSpeakMessage
                    )
                }

                if (partialTranscript.isNotBlank()) {
                    item {
                        HolographicLiveSpeechBubble(text = partialTranscript)
                    }
                }
            }
        }
    }
}

@Composable
fun FrostedChatMessageCard(
    message: ChatMessage,
    onCopy: () -> Unit,
    onSpeak: ((String) -> Unit)?
) {
    val isUser = message.role == "user"
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message_${message.id}" else "assistant_message_${message.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Sci-Fi Header Tag Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 4.dp, start = 6.dp, end = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isUser) Color(0x334FACFE) else if (message.isError) Color(0x33FF3366) else Color(0x3300F2FE))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isUser) "USER // COMMAND" else if (message.isError) "ALERT // SYSTEM" else "JARVIS // INTEL",
                    color = if (message.isError) ErrorRed else if (isUser) ElectricBlue else CyanNeon,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
            }

            Text(
                text = timeStr,
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Frosted Glassmorphism Message Bubble
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.88f else 0.94f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    Brush.linearGradient(
                        colors = if (isUser) {
                            listOf(
                                Color(0xE60D1E42),
                                Color(0xD908122A)
                            )
                        } else {
                            listOf(
                                Color(0xF207132B),
                                Color(0xE6040A18)
                            )
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = if (message.isError) {
                            listOf(ErrorRed.copy(0.7f), Color.Transparent, ErrorRed.copy(0.4f))
                        } else if (isUser) {
                            listOf(ElectricBlue.copy(0.6f), Color.Transparent, ElectricBlue.copy(0.3f))
                        } else {
                            listOf(CyanNeon.copy(0.7f), Color.Transparent, ElectricBlue.copy(0.4f))
                        }
                    ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    color = if (message.isError) ErrorRed else TextWhite,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )

                // Bottom Action Tray
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isUser && !message.isError && onSpeak != null) {
                        IconButton(
                            onClick = { onSpeak(message.content) },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(GlassSurfaceLight)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Play Audio",
                                tint = CyanNeon,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GlassSurfaceLight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            tint = TextSilver,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HolographicLiveSpeechBubble(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    AnimatedVisibility(
        visible = text.isNotBlank(),
        enter = fadeIn() + slideInVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "NEURAL STREAMING VOICE INPUT...",
                color = ElectricBlue,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 3.dp, end = 6.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xDD002244),
                                Color(0xBB001122)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(CyanNeon, ElectricBlue)),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = text,
                        color = TextCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 8.dp, height = 16.dp)
                            .background(CyanNeon.copy(alpha = cursorAlpha))
                    )
                }
            }
        }
    }
}
