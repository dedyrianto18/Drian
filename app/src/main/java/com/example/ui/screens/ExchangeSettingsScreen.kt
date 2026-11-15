package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExchangeSettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mt5Config by viewModel.mt5BridgeConfig.collectAsState()
    val mt5Testing by viewModel.mt5Testing.collectAsState()
    val mt5Logs by viewModel.mt5Logs.collectAsState()
    val backgroundState by viewModel.backgroundState.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startBackground24h(context)
        } else {
            // Foreground service still runs, notify user that notification permission helps monitor 24h status
            viewModel.startBackground24h(context)
            Toast.makeText(
                context,
                "Izin notifikasi ditolak; aktifkan notifikasi agar status 24 jam tampil di bar status.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val start24hWithPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.startBackground24h(context)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.startBackground24h(context)
        }
    }

    val demoServers = remember {
        listOf("Exness-MT5Trial", "Exness-MT5Trial2", "Exness-MT5Trial5", "Exness-MT5Trial6", "Exness-MT5Trial7")
    }
    val realServers = remember {
        listOf("Exness-MT5Real", "Exness-MT5Real2", "Exness-MT5Real5", "Exness-MT5Real6", "Exness-MT5Real7", "Exness-MT5Real9")
    }

    var accountMode by remember { mutableStateOf(mt5Config.accountMode) } // "DEMO" or "REAL"
    var exnessLoginInput by remember { mutableStateOf(mt5Config.accountLogin) }
    var exnessPasswordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedServer by remember { mutableStateOf(mt5Config.brokerServer.ifBlank { "Exness-MT5Trial" }) }
    var serverMenuOpen by remember { mutableStateOf(false) }

    var bridgeUrlInput by remember { mutableStateOf(mt5Config.bridgeUrl) }
    var bridgeTokenInput by remember { mutableStateOf(mt5Config.bridgeToken) }
    var tokenVisible by remember { mutableStateOf(false) }

    var symbolInput by remember { mutableStateOf(mt5Config.defaultSymbol) }
    var lotInput by remember { mutableStateOf(mt5Config.lotSize.toString()) }
    var autoExecuteBots by remember { mutableStateOf(mt5Config.autoExecuteBots) }

    val availableServers = if (accountMode == "REAL") realServers else demoServers

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .border(1.dp, OutlineDark, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Login Akun Exness (Demo & Riil)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Terhubung langsung dengan aplikasi PC Exness MT5 di Windows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account Type Selector Card: Demo vs Real
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (accountMode == "REAL") BullGreen.copy(alpha = 0.55f) else NeonCyan.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. Pilih Tipe Akun Exness",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = accountMode == "DEMO",
                        onClick = {
                            accountMode = "DEMO"
                            if (selectedServer.contains("Real", ignoreCase = true)) {
                                selectedServer = "Exness-MT5Trial"
                            }
                        },
                        label = { Text("Akun Demo (Trial)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyanContainer,
                            selectedLabelColor = NeonCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("exness_mode_demo_chip")
                    )

                    FilterChip(
                        selected = accountMode == "REAL",
                        onClick = {
                            accountMode = "REAL"
                            if (selectedServer.contains("Trial", ignoreCase = true)) {
                                selectedServer = "Exness-MT5Real"
                            }
                        },
                        label = { Text("Akun Riil (Real)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BullGreen.copy(alpha = 0.2f),
                            selectedLabelColor = BullGreen
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("exness_mode_real_chip")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (accountMode == "REAL") {
                        "Perhatian: Mode Akun Riil mengeksekusi order dengan dana asli pada server Exness-MT5Real."
                    } else {
                        "Mode Akun Demo menggunakan server Exness-MT5Trial untuk latihan & pengujian strategi."
                    },
                    fontSize = 11.sp,
                    color = if (accountMode == "REAL") GoldenAmber else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Login Credentials & PC Bridge Form
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (mt5Config.isConnected) BullGreen.copy(alpha = 0.6f) else OutlineDark,
                    shape = RoundedCornerShape(14.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyanContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = "2. Kredensial MT5 Exness & PC Bridge",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (mt5Config.isConnected) {
                                "Terhubung ${mt5Config.accountMode} #${mt5Config.accountLogin} (${mt5Config.brokerServer})"
                            } else {
                                "Masukkan login MT5 Exness + URL dari Aplikasi PC"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mt5Config.isConnected) BullGreen else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = exnessLoginInput,
                    onValueChange = { exnessLoginInput = it },
                    label = { Text("Nomor Login MT5 Exness") },
                    placeholder = { Text("Contoh: 184920312 (kosongkan jika sudah login di PC)") },
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = NeonCyan) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("exness_login_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = exnessPasswordInput,
                    onValueChange = { exnessPasswordInput = it },
                    label = { Text("Password Trading Exness") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = NeonCyan) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Password",
                                tint = TextMuted
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("exness_password_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Pilih Server Exness (${accountMode}):", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Box {
                    OutlinedButton(
                        onClick = { serverMenuOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("exness_server_dropdown"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedServer, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = serverMenuOpen,
                        onDismissRequest = { serverMenuOpen = false }
                    ) {
                        availableServers.forEach { srv ->
                            DropdownMenuItem(
                                text = { Text(srv) },
                                onClick = {
                                    selectedServer = srv
                                    serverMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bridgeUrlInput,
                    onValueChange = { bridgeUrlInput = it },
                    label = { Text("URL Aplikasi PC Exness Bridge") },
                    placeholder = { Text("http://192.168.1.10:8000") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = NeonCyan) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mt5_url_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = bridgeTokenInput,
                    onValueChange = { bridgeTokenInput = it },
                    label = { Text("Token Keamanan Bridge (default: exness-ai-2026)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = NeonCyan) },
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Token",
                                tint = TextMuted
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("mt5_token_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = symbolInput,
                        onValueChange = { symbolInput = it.trim() },
                        label = { Text("Simbol Default") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f).testTag("mt5_symbol_input")
                    )
                    OutlinedTextField(
                        value = lotInput,
                        onValueChange = { lotInput = it },
                        label = { Text("Lot") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f).testTag("mt5_lot_input")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Preset Pasar Exness:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("BTCUSDm", "XAUUSDm", "EURUSDm", "GBPUSDm", "USOILm", "USTECm").forEach { exnessSym ->
                        FilterChip(
                            selected = symbolInput == exnessSym,
                            onClick = { symbolInput = exnessSym },
                            label = { Text(exnessSym, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyanContainer,
                                selectedLabelColor = NeonCyan
                            ),
                            modifier = Modifier.testTag("exness_preset_$exnessSym")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Teruskan Order AI Bot ke Exness MT5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Otomatis kirim order ke akun Exness saat sinyal bot tereksekusi", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = autoExecuteBots,
                        onCheckedChange = { auto ->
                            autoExecuteBots = auto
                            viewModel.updateMt5BridgeSettings(
                                isEnabled = true,
                                bridgeUrl = bridgeUrlInput,
                                bridgeToken = bridgeTokenInput,
                                defaultSymbol = symbolInput,
                                lotSize = lotInput.toDoubleOrNull() ?: 0.01,
                                autoExecuteBots = auto,
                                accountMode = accountMode,
                                brokerServer = selectedServer,
                                accountLogin = exnessLoginInput
                            )
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = BullGreen),
                        modifier = Modifier.testTag("mt5_auto_exec_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.loginExnessAccount(
                            bridgeUrl = bridgeUrlInput,
                            bridgeToken = bridgeTokenInput,
                            login = exnessLoginInput,
                            password = exnessPasswordInput,
                            server = selectedServer,
                            accountMode = accountMode,
                            defaultSymbol = symbolInput,
                            lotSize = lotInput.toDoubleOrNull() ?: 0.01,
                            autoExecuteBots = autoExecuteBots
                        )
                    },
                    enabled = !mt5Testing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("exness_login_connect_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (accountMode == "REAL") BullGreen else NeonCyan,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (mt5Testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ObsidianBg)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menghubungkan ke Exness...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Text(
                            text = "Login & Hubungkan Akun Exness ($accountMode)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                if (mt5Config.isConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.syncExnessDataFromPc(silent = false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("sync_pc_exness_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sinkron Pasar & Histori AI dari PC", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (mt5Config.lastStatusMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceElevated)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (mt5Config.isConnected) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (mt5Config.isConnected) BullGreen else GoldenAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = mt5Config.lastStatusMessage,
                                fontSize = 11.sp,
                                color = if (mt5Config.isConnected) BullGreen else GoldenAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (mt5Config.isConnected) {
                                Text(
                                    text = "Balance: $${"%,.2f".format(mt5Config.mt5Balance)} • Equity: $${"%,.2f".format(mt5Config.mt5Equity)} • Free Margin: $${"%,.2f".format(mt5Config.mt5MarginFree)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                if (mt5Config.isConnected) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.executeMt5Order(
                                    symbol = symbolInput,
                                    action = "BUY",
                                    volume = lotInput.toDoubleOrNull() ?: 0.01,
                                    strategyName = "Manual Exness (${mt5Config.accountMode})",
                                    aiReasoning = "Order BUY manual dari aplikasi Android ke akun ${mt5Config.brokerServer}"
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("mt5_quick_buy_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = BullGreen, contentColor = ObsidianBg),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("BUY $symbolInput", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.executeMt5Order(
                                    symbol = symbolInput,
                                    action = "SELL",
                                    volume = lotInput.toDoubleOrNull() ?: 0.01,
                                    strategyName = "Manual Exness (${mt5Config.accountMode})",
                                    aiReasoning = "Order SELL manual dari aplikasi Android ke akun ${mt5Config.brokerServer}"
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("mt5_quick_sell_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = BearRed, contentColor = TextPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("SELL $symbolInput", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (mt5Logs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBg)
                            .padding(10.dp)
                    ) {
                        Text("Log Aktivitas Exness MT5:", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        mt5Logs.take(4).forEach { log ->
                            Text(
                                text = "• $log",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 24-Hour Continuous Background Operation Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (backgroundState.isRunning) BullGreen.copy(alpha = 0.6f) else OutlineDark,
                    shape = RoundedCornerShape(14.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (backgroundState.isRunning && backgroundState.isInternetConnected) BullGreen else NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Mode Latar Belakang 24 Jam (Online)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = backgroundState.statusMessage,
                                fontSize = 11.sp,
                                color = if (backgroundState.isRunning) BullGreen else TextMuted
                            )
                        }
                    }
                    Switch(
                        checked = backgroundState.isRunning,
                        onCheckedChange = { enable ->
                            if (enable) start24hWithPermission() else viewModel.stopBackground24h(context)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = BullGreen),
                        modifier = Modifier.testTag("background_24h_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Koneksi Internet", fontSize = 10.sp, color = TextMuted)
                        Text(
                            text = if (backgroundState.isInternetConnected) "ONLINE" else "OFFLINE (Menunggu)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (backgroundState.isInternetConnected) BullGreen else GoldenAmber
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Sinkron Latar Belakang", fontSize = 10.sp, color = TextMuted)
                        Text(
                            text = "${backgroundState.syncCount}x siklus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            Toast.makeText(context, "Buka Pengaturan > Aplikasi > Baterai > Tanpa Batas.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("battery_optimization_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = GoldenAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pengaturan Baterai (Pilih Tanpa Batas agar Stabil 24 Jam)", fontSize = 11.sp, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Risk Guardrails & Kill Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pengaman Risiko & Emergency Kill Switch", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Jalankan pc_exness_mt5/START_WINDOWS.bat di PC Windows terlebih dahulu, lalu masukkan URL Bridge yang tampil di PC ke kolom di atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.botManager.bots.value.forEach { bot ->
                            viewModel.botManager.stopBot(bot.id)
                        }
                        viewModel.closeAllMt5Positions()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("emergency_kill_switch_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BearRed),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(BearRed))
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Emergency Kill Switch (Stop Bot & Tutup Exness MT5)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
