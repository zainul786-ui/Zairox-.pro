package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VoiceState
import com.example.ui.components.HolographicBackground
import com.example.ui.components.JarvisOrbVisualizer
import com.example.ui.components.SettingsScreen
import com.example.ui.components.StatusBadge
import com.example.ui.components.TranscriptView
import com.example.ui.components.VoiceControlDeck
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.CyanUltra
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.GlassSurfaceLight
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSilver
import com.example.ui.theme.TextWhite
import com.example.ui.theme.ThinkingAmber

@Composable
fun JarvisMainScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val audioRmsDb by viewModel.audioRmsDb.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val lastError by viewModel.lastErrorMessage.collectAsStateWithLifecycle()
    val isSessionActive by viewModel.isSessionActive.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val voiceSettings by viewModel.voiceSettings.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            showPermissionRationale = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Notification permission result */ }

    LaunchedEffect(voiceSettings.backgroundSessionEnabled) {
        if (voiceSettings.backgroundSessionEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val onMicAction: () -> Unit = {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            viewModel.onOrbClick()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            currentConfig = aiConfig,
            currentVoiceSettings = voiceSettings,
            storedApiKey = viewModel.getStoredApiKey(),
            onBack = { showSettings = false },
            onSaveConfig = { config, key -> viewModel.saveAIConfig(config, key) },
            onSaveVoiceSettings = { settings -> viewModel.saveVoiceSettings(settings) },
            onTestConnection = { config, key, callback -> viewModel.testConnection(config, key, callback) },
            onTestVoice = { sample, rate, pitch -> viewModel.testVoice(sample, rate, pitch) },
            onClearHistory = { viewModel.clearConversation() },
            onClearApiKey = { viewModel.clearApiKey() },
            onResetPersonality = { viewModel.resetPersonality() }
        )
        return
    }

    HolographicBackground {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = modifier.fillMaxSize().testTag("jarvis_main_screen")
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                // A. Stark Industries Holographic Header
                CinematicTopBar(
                    presetName = aiConfig.preset.displayName,
                    modelName = aiConfig.modelName,
                    hasKey = viewModel.hasApiKey(),
                    onOpenSettings = { showSettings = true },
                    onClearHistory = { viewModel.clearConversation() }
                )

                // High-Tech Telemetry Strip
                HUDTelemetryStrip()

                // Error Notice HUD Panel
                AnimatedVisibility(
                    visible = lastError != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    lastError?.let { err ->
                        CinematicErrorBanner(
                            message = err,
                            onDismiss = { viewModel.clearError() },
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }

                // B. Center Holographic Arc Reactor Core & Dynamic Status
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    JarvisOrbVisualizer(
                        voiceState = voiceState,
                        audioRmsDb = audioRmsDb,
                        onClick = onMicAction
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StatusBadge(voiceState = voiceState)
                }

                // C. Frosted Glassmorphism Transcript
                TranscriptView(
                    messages = messages,
                    partialTranscript = partialTranscript,
                    onSpeakMessage = { viewModel.speakMessage(it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // D. Floating Control Deck
                VoiceControlDeck(
                    voiceState = voiceState,
                    isSessionActive = isSessionActive,
                    isContinuousMode = voiceSettings.continuousMode,
                    onMicClick = onMicAction,
                    onStopSpeaking = { viewModel.stopSpeaking() },
                    onEndSession = { viewModel.endSession() },
                    onToggleContinuousMode = { viewModel.toggleContinuousMode() },
                    onSendTextMessage = { text -> viewModel.sendTextMessage(text) }
                )
            }
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = {
                Text("Microphone Authorization Required", color = CyanNeon, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            },
            text = {
                Text(
                    "JARVIS requires acoustic sensor access to capture vocal frequencies and decode speech commands. Grant microphone permission to initiate tactical voice interface.",
                    color = TextWhite,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepMidnight)
                ) {
                    Text("Authorize Sensor", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Dismiss", color = TextSilver)
                }
            },
            containerColor = DeepMidnight
        )
    }
}

@Composable
fun CinematicTopBar(
    presetName: String,
    modelName: String,
    hasKey: Boolean,
    onOpenSettings: () -> Unit,
    onClearHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Metallic Cyan Brand Wordmark
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "J.A.R.V.I.S.",
                    color = CyanUltra,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.5.sp
                )
                Spacer(modifier = Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3300F2FE))
                        .border(0.5.dp, CyanNeon.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "MK-85",
                        color = CyanNeon,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = "NEURAL VOICE INTERFACE // TACTICAL HUD",
                color = ElectricBlue,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
        }

        // Action Tray & Model Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurfaceDark)
                    .border(
                        1.dp,
                        if (hasKey) GlassBorderCyan else ThinkingAmber.copy(alpha = 0.6f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (hasKey) SuccessGreen else ThinkingAmber)
                    )
                    Text(
                        text = if (hasKey) "$presetName • $modelName" else "CONFIG KEY",
                        color = if (hasKey) TextWhite else ThinkingAmber,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onClearHistory,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassSurfaceLight)
                    .testTag("top_bar_clear_history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Purge Transcript",
                    tint = TextSilver,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GlassSurfaceLight)
                    .testTag("top_bar_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open Protocols",
                    tint = CyanNeon,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
fun HUDTelemetryStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = CyanNeon.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
            Text(
                "KEYSTORE // AES-256-GCM",
                color = CyanNeon.copy(alpha = 0.6f),
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            "SYS.LOAD: NOMINAL // 44.1 kHz",
            color = ElectricBlue.copy(alpha = 0.6f),
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun CinematicErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xE62A0814),
                        Color(0xD91E040D)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(ErrorRed, Color.Transparent, ErrorRed.copy(0.4f))
                ),
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "CRITICAL ALERT // INTERRUPT",
                    color = ErrorRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = message,
                    color = TextWhite,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SETTINGS",
                    color = CyanNeon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(onClick = onOpenSettings)
                        .padding(end = 8.dp)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss error",
                        tint = TextSilver,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
