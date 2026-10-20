package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AIResult
import com.example.data.AIProviderConfig
import com.example.data.DEFAULT_JARVIS_PROMPT
import com.example.data.ProviderPreset
import com.example.data.VoiceSettings
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSilver
import com.example.ui.theme.TextWhite
import com.example.ui.theme.ThinkingAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentConfig: AIProviderConfig,
    currentVoiceSettings: VoiceSettings,
    storedApiKey: String,
    onBack: () -> Unit,
    onSaveConfig: (AIProviderConfig, String) -> Unit,
    onSaveVoiceSettings: (VoiceSettings) -> Unit,
    onTestConnection: (AIProviderConfig, String, (AIResult<String>) -> Unit) -> Unit,
    onTestVoice: (String, Float, Float) -> Unit,
    onClearHistory: () -> Unit,
    onClearApiKey: () -> Unit,
    onResetPersonality: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("AI Core", "Voice", "Personality", "Privacy")

    // AI Provider State
    var selectedPreset by remember(currentConfig) { mutableStateOf(currentConfig.preset) }
    var baseUrl by remember(currentConfig) { mutableStateOf(currentConfig.baseUrl) }
    var apiKey by remember(storedApiKey) { mutableStateOf(storedApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelName by remember(currentConfig) { mutableStateOf(currentConfig.modelName) }
    var temperature by remember(currentConfig) { mutableFloatStateOf(currentConfig.temperature) }
    var maxTokens by remember(currentConfig) { mutableIntStateOf(currentConfig.maxTokens) }
    var timeoutSeconds by remember(currentConfig) { mutableIntStateOf(currentConfig.timeoutSeconds) }
    var customHeadersText by remember(currentConfig) {
        mutableStateOf(currentConfig.customHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    // Voice State
    var speechRate by remember(currentVoiceSettings) { mutableFloatStateOf(currentVoiceSettings.speechRate) }
    var speechPitch by remember(currentVoiceSettings) { mutableFloatStateOf(currentVoiceSettings.speechPitch) }
    var continuousMode by remember(currentVoiceSettings) { mutableStateOf(currentVoiceSettings.continuousMode) }
    var backgroundSession by remember(currentVoiceSettings) { mutableStateOf(currentVoiceSettings.backgroundSessionEnabled) }

    // Personality State
    var systemPrompt by remember(currentConfig) { mutableStateOf(currentConfig.systemPrompt) }

    // Test Connection State
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AIResult<String>?>(null) }
    var hasSavedFeedback by remember { mutableStateOf(false) }

    HolographicBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "JARVIS SYSTEM PROTOCOLS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = CyanNeon
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Return to Assistant",
                                tint = CyanNeon
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xCC040814)
                    )
                )
            },
            containerColor = Color.Transparent,
            modifier = modifier.testTag("settings_screen")
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            // Futuristic Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DeepMidnight,
                contentColor = CyanNeon,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CyanNeon,
                        height = 2.dp
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) CyanNeon else TextSilver,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // AI Provider Tab
                        item {
                            SectionHeader("AI PROVIDER ARCHITECTURE")
                            Text(
                                "Configure Gemini Live Realtime Audio or external OpenAI-compatible models. Credentials are encrypted in hardware via Android Keystore.",
                                color = TextSilver,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Presets Chips
                        item {
                            Text("SELECT PROVIDER PRESET", color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(6.dp))
                            val allPresets = ProviderPreset.entries
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    allPresets.take(3).forEach { preset ->
                                        FilterChip(
                                            selected = selectedPreset == preset,
                                            onClick = {
                                                selectedPreset = preset
                                                baseUrl = preset.defaultBaseUrl
                                                modelName = preset.defaultModel
                                            },
                                            label = { Text(preset.displayName, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                                                selectedLabelColor = CyanNeon,
                                                containerColor = SurfaceDark,
                                                labelColor = TextSilver
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = selectedPreset == preset,
                                                borderColor = if (selectedPreset == preset) CyanNeon else GlassBorder
                                            )
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    allPresets.drop(3).take(3).forEach { preset ->
                                        FilterChip(
                                            selected = selectedPreset == preset,
                                            onClick = {
                                                selectedPreset = preset
                                                baseUrl = preset.defaultBaseUrl
                                                modelName = preset.defaultModel
                                            },
                                            label = { Text(preset.displayName, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                                                selectedLabelColor = CyanNeon,
                                                containerColor = SurfaceDark,
                                                labelColor = TextSilver
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = selectedPreset == preset,
                                                borderColor = if (selectedPreset == preset) CyanNeon else GlassBorder
                                            )
                                        )
                                    }
                                }
                                if (allPresets.size > 6) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        allPresets.drop(6).forEach { preset ->
                                            FilterChip(
                                                selected = selectedPreset == preset,
                                                onClick = {
                                                    selectedPreset = preset
                                                    baseUrl = preset.defaultBaseUrl
                                                    modelName = preset.defaultModel
                                                },
                                                label = { Text(preset.displayName, fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                                                    selectedLabelColor = CyanNeon,
                                                    containerColor = SurfaceDark,
                                                    labelColor = TextSilver
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = selectedPreset == preset,
                                                    borderColor = if (selectedPreset == preset) CyanNeon else GlassBorder
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            if (selectedPreset.isRealtimeLive) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x2200E5FF), RoundedCornerShape(8.dp))
                                        .border(1.dp, CyanNeon.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "⚡ NATIVE REALTIME AUDIO: Bidirectional WebSocket stream (16kHz in -> 24kHz PCM out) with server-side barge-in interruption. Persistent mic capture stays active continuously.",
                                        color = CyanNeon,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Base URL
                        item {
                            SettingsTextField(
                                label = "API Base URL",
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                testTag = "setting_base_url_input"
                            )
                        }

                        // API Key with Keystore indicator
                        item {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Keystore Encrypted",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "API Key (Keystore Hardware AES-256-GCM)",
                                        color = SuccessGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    label = { Text("API Secret Key") },
                                    singleLine = true,
                                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showApiKey = !showApiKey }) {
                                            Icon(
                                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle API Key visibility",
                                                tint = TextSilver
                                            )
                                        }
                                    },
                                    colors = cyberTextFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("setting_api_key_input")
                                )
                            }
                        }

                        // Model Name
                        item {
                            SettingsTextField(
                                label = "Model Identifier",
                                value = modelName,
                                onValueChange = { modelName = it },
                                testTag = "setting_model_name_input"
                            )
                        }

                        // Temperature Slider
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("TEMPERATURE: ${"%.2f".format(temperature)}", color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text(if (temperature < 0.4f) "Precise" else if (temperature > 0.8f) "Creative" else "Balanced", color = CyanNeon, fontSize = 11.sp)
                                }
                                Slider(
                                    value = temperature,
                                    onValueChange = { temperature = it },
                                    valueRange = 0.0f..1.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = CyanNeon,
                                        activeTrackColor = CyanNeon,
                                        inactiveTrackColor = SurfaceVariantDark
                                    )
                                )
                            }
                        }

                        // Max Tokens Slider
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("MAX TOKENS: $maxTokens", color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text("Output length", color = TextSilver, fontSize = 11.sp)
                                }
                                Slider(
                                    value = maxTokens.toFloat(),
                                    onValueChange = { maxTokens = it.toInt() },
                                    valueRange = 128f..4096f,
                                    steps = 30,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ElectricBlue,
                                        activeTrackColor = ElectricBlue,
                                        inactiveTrackColor = SurfaceVariantDark
                                    )
                                )
                            }
                        }

                        // Timeout Slider
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("TIMEOUT: ${timeoutSeconds}s", color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text("Network limit", color = TextSilver, fontSize = 11.sp)
                                }
                                Slider(
                                    value = timeoutSeconds.toFloat(),
                                    onValueChange = { timeoutSeconds = it.toInt() },
                                    valueRange = 5f..60f,
                                    steps = 11,
                                    colors = SliderDefaults.colors(
                                        thumbColor = CyanNeon,
                                        activeTrackColor = CyanNeon,
                                        inactiveTrackColor = SurfaceVariantDark
                                    )
                                )
                            }
                        }

                        // Custom Headers (optional)
                        item {
                            SettingsTextField(
                                label = "Optional Custom Headers (e.g. HTTP-Referer: myapp)",
                                value = customHeadersText,
                                onValueChange = { customHeadersText = it },
                                singleLine = false,
                                testTag = "setting_custom_headers_input"
                            )
                        }

                        // Test Connection & Save Action buttons
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isTestingConnection = true
                                        testResult = null
                                        val testConfig = buildCurrentConfig(
                                            selectedPreset, baseUrl, apiKey, modelName,
                                            temperature, maxTokens, timeoutSeconds, customHeadersText, systemPrompt
                                        )
                                        onTestConnection(testConfig, apiKey) { result ->
                                            isTestingConnection = false
                                            testResult = result
                                        }
                                    },
                                    enabled = !isTestingConnection,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ElectricBlue)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("test_connection_button")
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ElectricBlue, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test Ping", fontSize = 12.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        val updatedConfig = buildCurrentConfig(
                                            selectedPreset, baseUrl, apiKey, modelName,
                                            temperature, maxTokens, timeoutSeconds, customHeadersText, systemPrompt
                                        )
                                        onSaveConfig(updatedConfig, apiKey)
                                        hasSavedFeedback = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanNeon,
                                        contentColor = SpaceBlack
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_settings_button")
                                ) {
                                    Text("Save Config", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            if (hasSavedFeedback) {
                                Text(
                                    "✓ Configuration saved to secure storage",
                                    color = SuccessGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }

                            // Test Connection Feedback
                            testResult?.let { res ->
                                Spacer(modifier = Modifier.height(8.dp))
                                when (res) {
                                    is AIResult.Success -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SuccessGreen.copy(alpha = 0.15f))
                                                .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                            Text(res.data, color = SuccessGreen, fontSize = 12.sp)
                                        }
                                    }
                                    is AIResult.Failure -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ErrorRed.copy(alpha = 0.15f))
                                                .border(1.dp, ErrorRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                                            Text(res.message, color = ErrorRed, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Voice Tab
                        item {
                            SectionHeader("VOICE & ACOUSTIC SYNTHESIS")
                            Text(
                                "Tune speech output characteristics and voice session loop behavior.",
                                color = TextSilver,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Speech Rate
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("SPEECH RATE: ${"%.2f".format(speechRate)}x", color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text(if (speechRate > 1.2f) "Fast" else if (speechRate < 0.8f) "Slow" else "Normal", color = CyanNeon, fontSize = 11.sp)
                                }
                                Slider(
                                    value = speechRate,
                                    onValueChange = {
                                        speechRate = it
                                        onSaveVoiceSettings(
                                            currentVoiceSettings.copy(speechRate = speechRate, speechPitch = speechPitch)
                                        )
                                    },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = CyanNeon,
                                        activeTrackColor = CyanNeon,
                                        inactiveTrackColor = SurfaceVariantDark
                                    )
                                )
                            }
                        }

                        // Speech Pitch
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("SPEECH PITCH: ${"%.2f".format(speechPitch)}x", color = TextWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    Text(if (speechPitch > 1.2f) "High" else if (speechPitch < 0.8f) "Deep" else "Natural", color = ElectricBlue, fontSize = 11.sp)
                                }
                                Slider(
                                    value = speechPitch,
                                    onValueChange = {
                                        speechPitch = it
                                        onSaveVoiceSettings(
                                            currentVoiceSettings.copy(speechRate = speechRate, speechPitch = speechPitch)
                                        )
                                    },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ElectricBlue,
                                        activeTrackColor = ElectricBlue,
                                        inactiveTrackColor = SurfaceVariantDark
                                    )
                                )
                            }
                        }

                        // Test Voice Button
                        item {
                            OutlinedButton(
                                onClick = {
                                    onTestVoice(
                                        "Greetings. JARVIS neural voice synthesis operational.",
                                        speechRate,
                                        speechPitch
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanNeon)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("test_voice_button")
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Audition Voice Synthesis", fontSize = 12.sp)
                            }
                        }

                        // Continuous Conversation Toggle
                        item {
                            SettingsToggleRow(
                                title = "Continuous Voice Conversation",
                                subtitle = "Automatically re-engages microphone listening when JARVIS finishes speaking a response.",
                                isChecked = continuousMode,
                                onCheckedChange = {
                                    continuousMode = it
                                    onSaveVoiceSettings(currentVoiceSettings.copy(continuousMode = it))
                                },
                                testTag = "toggle_continuous_mode"
                            )
                        }

                        // Background Voice Session Toggle
                        item {
                            SettingsToggleRow(
                                title = "Background Voice Session",
                                subtitle = "Keeps voice session active via Android Foreground Service notification when leaving the app.",
                                isChecked = backgroundSession,
                                onCheckedChange = {
                                    backgroundSession = it
                                    onSaveVoiceSettings(currentVoiceSettings.copy(backgroundSessionEnabled = it))
                                },
                                testTag = "toggle_background_session"
                            )
                        }
                    }

                    2 -> {
                        // Personality Tab
                        item {
                            SectionHeader("JARVIS PERSONALITY MATRIX")
                            Text(
                                "Edit the system prompt controlling tone, intellect, language switching (English/Hindi/Hinglish), and behavioral boundaries.",
                                color = TextSilver,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = systemPrompt,
                                onValueChange = { systemPrompt = it },
                                label = { Text("Core System Instructions") },
                                minLines = 8,
                                maxLines = 14,
                                colors = cyberTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("system_prompt_input")
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        systemPrompt = DEFAULT_JARVIS_PROMPT
                                        onResetPersonality()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThinkingAmber),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ThinkingAmber)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reset to Default", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val updatedConfig = currentConfig.copy(systemPrompt = systemPrompt)
                                        onSaveConfig(updatedConfig, apiKey)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanNeon,
                                        contentColor = SpaceBlack
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Apply Prompt", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    3 -> {
                        // Privacy & Diagnostics Tab
                        item {
                            SectionHeader("PRIVACY, SECURITY & PROTOCOLS")
                            Text(
                                "Your voice and requests travel directly from your device to your configured AI provider over TLS 1.3 encrypted HTTPS. No intermediate telemetry or third-party servers.",
                                color = TextSilver,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceDark)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("HARDWARE SECURITY STATUS", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text("• Credential Vault: AndroidKeyStore (AES-256-GCM)", color = TextWhite, fontSize = 12.sp)
                                    Text("• Audio Processing: Local SpeechRecognizer & TextToSpeech", color = TextWhite, fontSize = 12.sp)
                                    Text("• Network: Direct Provider HTTPS, No Analytics", color = TextWhite, fontSize = 12.sp)
                                    Text("• Foreground Service: Microphone runtime policy verified", color = TextWhite, fontSize = 12.sp)
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onClearHistory,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ErrorRed)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("clear_history_button")
                            ) {
                                Text("Purge Conversation Memory", fontSize = 12.sp)
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = {
                                    apiKey = ""
                                    onClearApiKey()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ErrorRed)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("clear_api_key_button")
                            ) {
                                Text("Erase Hardware-Stored API Key", fontSize = 12.sp)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "PROJECT JARVIS // V1.0 // NATIVE KOTLIN & JETPACK COMPOSE",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = CyanNeon,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        colors = cyberTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextSilver, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyanNeon,
                checkedTrackColor = CyanNeon.copy(alpha = 0.35f),
                uncheckedThumbColor = TextSilver,
                uncheckedTrackColor = SurfaceVariantDark
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun cyberTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyanNeon,
    unfocusedBorderColor = GlassBorder,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite,
    focusedLabelColor = CyanNeon,
    unfocusedLabelColor = TextSilver,
    cursorColor = CyanNeon,
    focusedContainerColor = SurfaceDark,
    unfocusedContainerColor = SurfaceDark
)

private fun buildCurrentConfig(
    preset: ProviderPreset,
    baseUrl: String,
    apiKey: String,
    modelName: String,
    temp: Float,
    maxTokens: Int,
    timeout: Int,
    headersRaw: String,
    prompt: String
): AIProviderConfig {
    val headersMap = mutableMapOf<String, String>()
    headersRaw.split("\n").forEach { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank()) {
            headersMap[parts[0].trim()] = parts[1].trim()
        }
    }
    return AIProviderConfig(
        preset = preset,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        temperature = temp,
        maxTokens = maxTokens,
        timeoutSeconds = timeout,
        customHeaders = headersMap,
        systemPrompt = prompt
    )
}
