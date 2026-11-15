package com.example.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.ui.AiOperationState
import com.example.ui.MainViewModel
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedContainer
import com.example.ui.theme.BullGreen
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.GeminiAppBridge
import com.google.firebase.ai.type.AspectRatio

@Composable
fun AiCopilotScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatMessages by viewModel.chatMessages.collectAsState()
    val aiState by viewModel.aiState.collectAsState()

    val isGeminiInstalled = remember { GeminiAppBridge.isGeminiAppInstalled(context) }

    var inputText by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("gemini-3.8-flash") }
    var showModelMenu by remember { mutableStateOf(false) }

    // Image generation dialog
    var showInfographicDialog by remember { mutableStateOf(false) }
    var infographicPrompt by remember { mutableStateOf("Ilustrasi futuristik ruang kerja digital modern dengan cahaya neon biru") }
    var selectedAspectRatio by remember { mutableStateOf(AspectRatio.LANDSCAPE_16x9) }
    var aspectRatioMenuOpen by remember { mutableStateOf(false) }

    // Voice capture trigger
    var isRecordingAudio by remember { mutableStateOf(false) }

    // Android Photo Picker (Play Policy compliant - no storage permission needed)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val userQuestion = inputText.trim().ifBlank {
                            "Analisis gambar atau foto ini secara detail dalam bahasa Indonesia."
                        }
                        viewModel.processChartImage(bitmap = bitmap, prompt = userQuestion)
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Gagal membaca file gambar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Video picker via Android Photo Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.isNotEmpty()) {
                        val userQuestion = inputText.trim().ifBlank {
                            "Rangkum dan jelaskan isi video ini secara jelas dalam bahasa Indonesia."
                        }
                        viewModel.processVideo(videoBytes = bytes, prompt = userQuestion)
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Gagal membaca file video.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Gemini Chat Umum",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isGeminiInstalled) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isGeminiInstalled) BullGreen else NeonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isGeminiInstalled) {
                            "Aplikasi Gemini HP Terdeteksi"
                        } else {
                            "Gemini AI Aktif (Web Fallback)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGeminiInstalled) BullGreen else NeonCyan
                    )
                }
            }

            // Model Selector Dropdown
            Box {
                Button(
                    onClick = { showModelMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = NeonCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(1.dp, OutlineDark, RoundedCornerShape(8.dp))
                        .minimumInteractiveComponentSize()
                        .testTag("model_selector_btn")
                ) {
                    Text(
                        text = when (selectedModel) {
                            "gemini-4-argon" -> "Gemini 4 Argon"
                            "gemini-3.8-flash" -> "Gemini 3.8 Flash"
                            else -> "3.5 Flash-Lite"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { showModelMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Gemini 3.8 Flash (Chat Umum Cepat)") },
                        onClick = {
                            selectedModel = "gemini-3.8-flash"
                            showModelMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Gemini 4 Argon (High Thinking)") },
                        onClick = {
                            selectedModel = "gemini-4-argon"
                            showModelMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Gemini 3.5 Flash-Lite (Ringkas)") },
                        onClick = {
                            selectedModel = "gemini-3.5-flash-lite"
                            showModelMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Direct Connection Card to Installed Gemini App on Android Phone
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isGeminiInstalled) BullGreen.copy(alpha = 0.5f) else ElectricIndigo.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isGeminiInstalled) {
                        "Terhubung ke Aplikasi Google Gemini di HP Anda"
                    } else {
                        "Integrasi Aplikasi Google Gemini Android"
                    },
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { GeminiAppBridge.openGeminiApp(context) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("open_gemini_app_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Buka Aplikasi Gemini",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGeminiInstalled) "Buka App Gemini" else "Gemini Web",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val lastUserPrompt = chatMessages.lastOrNull { it.role == "user" }?.text.orEmpty()
                            val promptToSend = inputText.trim().ifBlank {
                                lastUserPrompt.ifBlank { "Halo Gemini, bantu saya hari ini." }
                            }
                            GeminiAppBridge.sendPromptToGeminiApp(context, promptToSend)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("send_to_gemini_app_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Kirim Prompt ke App Gemini",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kirim ke Gemini App",
                            fontSize = 11.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!isGeminiInstalled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = { GeminiAppBridge.openGeminiPlayStore(context) },
                        modifier = Modifier.testTag("install_gemini_play_btn")
                    ) {
                        Text(
                            text = "Aplikasi Gemini belum terinstal? Pasang via Google Play Store",
                            fontSize = 11.sp,
                            color = GoldenAmber
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Multimodal Quick Bar (General purpose)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    isRecordingAudio = !isRecordingAudio
                    if (isRecordingAudio) {
                        val simulatedWave = ByteArray(1024) { (it % 128).toByte() }
                        viewModel.processVoiceCommand(simulatedWave)
                        isRecordingAudio = false
                    }
                },
                modifier = Modifier.weight(1f).testTag("voice_command_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecordingAudio) GoldenAmber else SurfaceDark,
                    contentColor = if (isRecordingAudio) ObsidianBg else TextPrimary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Suara", modifier = Modifier.size(16.dp), tint = NeonCyan)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Suara", fontSize = 11.sp)
            }

            Button(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f).testTag("image_scan_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Foto", modifier = Modifier.size(16.dp), tint = NeonCyan)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Foto", fontSize = 11.sp)
            }

            Button(
                onClick = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                modifier = Modifier.weight(1f).testTag("video_scan_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = "Video", modifier = Modifier.size(16.dp), tint = NeonCyan)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Video", fontSize = 11.sp)
            }

            Button(
                onClick = { showInfographicDialog = true },
                modifier = Modifier.weight(1.1f).testTag("generate_infographic_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = "Buat Gambar", modifier = Modifier.size(16.dp), tint = ElectricIndigo)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gambar", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // AI Status Loading / Image banner
        when (val state = aiState) {
            is AiOperationState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonCyan, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = state.stage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            is AiOperationState.ImageSuccess -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Gambar Gemini (gemini-nano-banana-2.1)", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.dismissAiState() }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextMuted)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "Generated Visual",
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            is AiOperationState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BearRedContainer)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BearRed,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissAiState() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = BearRed)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            else -> {}
        }

        // Messages Thread
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    onForwardToGeminiApp = {
                        GeminiAppBridge.sendPromptToGeminiApp(context, message.text)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Field Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(14.dp))
                .border(1.dp, OutlineDark, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Tanya apa saja ke Gemini atau kirim ke app...", fontSize = 13.sp, color = TextMuted) },
                modifier = Modifier.weight(1f).testTag("chat_input_field"),
                maxLines = 3
            )

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendChatMessage(inputText, selectedModel)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("send_chat_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim Chat", tint = NeonCyan)
            }
        }
    }

    // Visual Image Generator Modal
    if (showInfographicDialog) {
        AlertDialog(
            onDismissRequest = { showInfographicDialog = false },
            title = { Text("Buat Gambar dengan Gemini AI", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Masukkan deskripsi gambar apa saja yang ingin dibuat menggunakan gemini-nano-banana-2.1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = infographicPrompt,
                        onValueChange = { infographicPrompt = it },
                        label = { Text("Deskripsi Gambar") },
                        modifier = Modifier.fillMaxWidth().testTag("infographic_prompt_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Rasio Aspek Gambar:", fontSize = 12.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceHighlight)
                            .clickable { aspectRatioMenuOpen = true }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedAspectRatio) {
                                AspectRatio.LANDSCAPE_16x9 -> "16:9 Landscape"
                                AspectRatio.SQUARE_1x1 -> "1:1 Square"
                                AspectRatio.PORTRAIT_9x16 -> "9:16 Portrait"
                                AspectRatio.LANDSCAPE_4x3 -> "4:3 Landscape"
                                AspectRatio.LANDSCAPE_21x9 -> "21:9 Ultrawide"
                                else -> "16:9 Landscape"
                            },
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.ExpandMore, contentDescription = null, tint = NeonCyan)
                    }

                    DropdownMenu(
                        expanded = aspectRatioMenuOpen,
                        onDismissRequest = { aspectRatioMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("16:9 (Landscape)") },
                            onClick = { selectedAspectRatio = AspectRatio.LANDSCAPE_16x9; aspectRatioMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("1:1 (Square)") },
                            onClick = { selectedAspectRatio = AspectRatio.SQUARE_1x1; aspectRatioMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("9:16 (Portrait Story)") },
                            onClick = { selectedAspectRatio = AspectRatio.PORTRAIT_9x16; aspectRatioMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("4:3 (Classic)") },
                            onClick = { selectedAspectRatio = AspectRatio.LANDSCAPE_4x3; aspectRatioMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("21:9 (Ultrawide Banner)") },
                            onClick = { selectedAspectRatio = AspectRatio.LANDSCAPE_21x9; aspectRatioMenuOpen = false }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.generateInfographicCard(infographicPrompt, selectedAspectRatio)
                        showInfographicDialog = false
                    },
                    modifier = Modifier.testTag("confirm_infographic_gen_btn")
                ) {
                    Text("Buat Gambar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInfographicDialog = false }) {
                    Text("Batal")
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onForwardToGeminiApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    var showThinkingProcess by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                Text(
                    text = "Gemini Chat (${message.modelUsed})",
                    fontSize = 10.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 14.dp
                    )
                )
                .background(if (isUser) NeonCyan.copy(alpha = 0.2f) else SurfaceDark)
                .border(
                    width = 1.dp,
                    color = if (isUser) NeonCyan.copy(alpha = 0.4f) else OutlineDark,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onForwardToGeminiApp() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Buka di App Gemini",
                            tint = NeonCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Lanjut di App Gemini",
                            fontSize = 10.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!message.thinkingProcess.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceElevated)
                            .clickable { showThinkingProcess = !showThinkingProcess }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = GoldenAmber, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Proses Pemikiran (High Thinking)", fontSize = 11.sp, color = GoldenAmber, fontWeight = FontWeight.Bold)
                        }
                        Icon(
                            imageVector = if (showThinkingProcess) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GoldenAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    AnimatedVisibility(visible = showThinkingProcess) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .background(SurfaceHighlight, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = message.thinkingProcess,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
