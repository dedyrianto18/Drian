package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.AiTradeSignal
import com.example.data.model.BotStatus
import com.example.data.model.CryptoPair
import com.example.ui.MainViewModel
import com.example.ui.NavigationScreen
import com.example.ui.components.BotCard
import com.example.ui.components.PairTickerChip
import com.example.ui.components.PulsingStatusDot
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.ElectricIndigo
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
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pairs by viewModel.pairs.collectAsState()
    val selectedSymbol by viewModel.selectedPairSymbol.collectAsState()
    val selectedPair = pairs[selectedSymbol] ?: pairs.values.firstOrNull()
    val bots by viewModel.botManager.bots.collectAsState()
    val signals by viewModel.liveSignals.collectAsState()
    val mt5Config by viewModel.mt5BridgeConfig.collectAsState()
    val backgroundState by viewModel.backgroundState.collectAsState()

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startBackground24h(context)
    }

    val toggle24hMode = {
        if (backgroundState.isRunning) {
            viewModel.stopBackground24h(context)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startBackground24h(context)
        }
    }

    var selectedCategory by remember { mutableStateOf("Semua") }
    var showAddPairDialog by remember { mutableStateOf(false) }
    var customSymbolInput by remember { mutableStateOf("") }
    var customPriceInput by remember { mutableStateOf("") }

    val categories = remember { listOf("Semua", "Crypto", "Metals", "Forex", "Energies", "Indices") }
    val filteredPairs = remember(pairs, selectedCategory) {
        val list = pairs.values.toList()
        if (selectedCategory == "Semua") list else list.filter { it.category == selectedCategory }
    }

    val activeBotsCount = bots.count { it.status == BotStatus.RUNNING }
    val totalProfit = bots.sumOf { it.totalProfitUsdt }
    val isRealMode = mt5Config.accountMode.equals("REAL", ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Exness Account Banner (Demo / Real, no Sandbox/Paper)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            if (isRealMode) BullGreen.copy(alpha = 0.65f) else NeonCyan.copy(alpha = 0.65f),
                            ElectricIndigo.copy(alpha = 0.6f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isRealMode) BullGreen.copy(alpha = 0.2f) else NeonCyanContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (mt5Config.isConnected) {
                                    "EXNESS ${mt5Config.accountMode} #${mt5Config.accountLogin}"
                                } else {
                                    "EXNESS ${mt5Config.accountMode} (OFFLINE)"
                                },
                                fontSize = 11.sp,
                                color = if (isRealMode) BullGreen else NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = mt5Config.brokerServer.ifBlank { "Exness-MT5Trial" },
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PulsingStatusDot(isActive = activeBotsCount > 0)
                        Text(
                            text = "$activeBotsCount Bot",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (activeBotsCount > 0) BullGreen else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (mt5Config.isConnected) "Saldo Akun Exness MT5" else "Hubungkan Akun Exness Demo / Riil",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = if (mt5Config.isConnected) {
                        "$${"%,.2f".format(mt5Config.mt5Balance)} USD"
                    } else {
                        "Belum Terhubung ke PC MT5"
                    },
                    style = if (mt5Config.isConnected) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (mt5Config.isConnected) "Equity / Profit Bot" else "Profit Bot Tercatat",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = if (mt5Config.isConnected) {
                                "$${"%,.2f".format(mt5Config.mt5Equity)} • ${if (totalProfit >= 0) "+" else ""}${"%.2f".format(totalProfit)}"
                            } else {
                                "${if (totalProfit >= 0) "+" else ""}${"%.2f".format(totalProfit)} USD"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalProfit >= 0) BullGreen else BearRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            if (mt5Config.isConnected) {
                                viewModel.syncExnessDataFromPc(silent = false)
                            } else {
                                viewModel.navigateTo(NavigationScreen.EXCHANGE_SETTINGS)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mt5Config.isConnected) SurfaceDark else NeonCyan,
                            contentColor = if (mt5Config.isConnected) NeonCyan else ObsidianBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("banner_exness_action_btn")
                    ) {
                        Icon(
                            imageVector = if (mt5Config.isConnected) Icons.Default.Sync else Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (mt5Config.isConnected) "Sinkron PC" else "Login Exness",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick 24-Hour Background Execution Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(
                    width = 1.dp,
                    color = if (backgroundState.isRunning) BullGreen.copy(alpha = 0.55f) else OutlineDark,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    tint = if (backgroundState.isRunning && backgroundState.isInternetConnected) BullGreen else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = if (backgroundState.isRunning) "Latar Belakang 24 Jam: AKTIF" else "Latar Belakang 24 Jam: NONAKTIF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (backgroundState.isRunning) BullGreen else TextPrimary
                    )
                    Text(
                        text = backgroundState.statusMessage,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }
            TextButton(
                onClick = toggle24hMode,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dashboard_toggle_24h_btn")
            ) {
                Text(
                    text = if (backgroundState.isRunning) "Matikan" else "Aktifkan",
                    color = if (backgroundState.isRunning) GoldenAmber else NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Exness Market Selector Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Pasar Didukung Broker Exness",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Crypto, Emas/Metals, Forex Majors, Minyak & Indeks",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            TextButton(
                onClick = { showAddPairDialog = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_custom_pair_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tambah Simbol", color = NeonCyan, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyanContainer,
                        selectedLabelColor = NeonCyan
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("market_cat_$cat")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filteredPairs.forEach { pair ->
                PairTickerChip(
                    pair = pair,
                    isSelected = pair.symbol == selectedPair?.symbol,
                    onClick = { viewModel.selectPair(pair.symbol) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Market Quote & Technical Indicator Summary Card (Chart-free, lightweight)
        selectedPair?.let { pair ->
            ExnessMarketSummaryCard(pair = pair)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AI High Thinking & Bot Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.runHighThinkingAnalysis(selectedPair?.symbol ?: selectedSymbol) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("high_thinking_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = ObsidianBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Analisis AI Gemini", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = { viewModel.navigateTo(NavigationScreen.CREATE_BOT) },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("create_bot_shortcut_btn"),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = BullGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pasang Bot", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live AI Trade Signals
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sinyal Eksekusi AI Exness",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Gemini 4 Argon",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        signals.forEach { signal ->
            SignalCard(
                signal = signal,
                mt5Connected = mt5Config.isConnected,
                onExecuteBot = {
                    viewModel.selectPair(signal.pairSymbol)
                    viewModel.navigateTo(NavigationScreen.CREATE_BOT)
                },
                onSendToMt5 = {
                    viewModel.sendSignalToMt5(signal)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Bots Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bot AI Aktif (${bots.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { viewModel.navigateTo(NavigationScreen.BOTS) }) {
                Text("Bot & Histori AI", color = NeonCyan, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (bots.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada bot berjalan. Ketuk 'Pasang Bot' untuk memulai.", color = TextMuted)
            }
        } else {
            bots.forEach { bot ->
                BotCard(
                    bot = bot,
                    onToggleStatus = { viewModel.botManager.toggleBotStatus(bot.id) },
                    onStopBot = { viewModel.botManager.stopBot(bot.id) },
                    onClick = { viewModel.selectBot(bot.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // Add Custom Exness Symbol Dialog
    if (showAddPairDialog) {
        AlertDialog(
            onDismissRequest = { showAddPairDialog = false },
            title = { Text("Tambah Simbol Pasar Exness", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Masukkan simbol resmi Exness MT5 (contoh: USDCHFm, NZDUSDm, LTCUSDm, UKOILm).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customSymbolInput,
                        onValueChange = { customSymbolInput = it.trim() },
                        label = { Text("Simbol Exness (e.g. UKOILm)") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_symbol_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customPriceInput,
                        onValueChange = { customPriceInput = it },
                        label = { Text("Harga Estimasi (e.g. 74.20)") },
                        modifier = Modifier.fillMaxWidth().testTag("custom_price_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = customPriceInput.toDoubleOrNull() ?: 100.0
                        if (customSymbolInput.isNotBlank()) {
                            viewModel.addCustomPair(customSymbolInput, price)
                            showAddPairDialog = false
                            customSymbolInput = ""
                            customPriceInput = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_pair_btn")
                ) {
                    Text("Tambahkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPairDialog = false }) {
                    Text("Batal")
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

@Composable
private fun ExnessMarketSummaryCard(pair: CryptoPair) {
    val ind = pair.indicators
    val isPositive = pair.priceChange24h >= 0
    val fmt = "%.${pair.digits}f"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineDark, RoundedCornerShape(16.dp))
            .testTag("exness_market_summary_card"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = pair.symbol,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyanContainer)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(pair.category, fontSize = 10.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(pair.name, fontSize = 12.sp, color = TextMuted)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = fmt.format(pair.lastPrice),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${if (isPositive) "+" else ""}${"%.2f".format(pair.priceChange24h)}% (24j)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) BullGreen else BearRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Bid / Jual", fontSize = 10.sp, color = TextMuted)
                    Text(fmt.format(pair.bidPrice), fontSize = 12.sp, color = BearRed, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ask / Beli", fontSize = 10.sp, color = TextMuted)
                    Text(fmt.format(pair.askPrice), fontSize = 12.sp, color = BullGreen, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("High / Low", fontSize = 10.sp, color = TextMuted)
                    Text("${fmt.format(pair.high24h)} / ${fmt.format(pair.low24h)}", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }

            if (ind != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RSI-14: ${"%.1f".format(ind.rsi)}", fontSize = 11.sp, color = NeonCyan, fontFamily = FontFamily.Monospace)
                    Text("EMA20: ${fmt.format(ind.ema20)}", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    Text("ATR: ${"%.3f".format(ind.atr)}", fontSize = 11.sp, color = GoldenAmber, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun SignalCard(
    signal: AiTradeSignal,
    mt5Connected: Boolean = false,
    onExecuteBot: () -> Unit,
    onSendToMt5: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, OutlineDark, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (signal.action.contains("BUY") || signal.action.contains("LONG")) BullGreen.copy(alpha = 0.2f) else NeonCyanContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = signal.action.replace("_", " "),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (signal.action.contains("BUY") || signal.action.contains("LONG")) BullGreen else NeonCyan
                        )
                    }

                    Text(
                        text = signal.pairSymbol,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldenAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${signal.confidenceScore}% Akurasi",
                        fontSize = 12.sp,
                        color = GoldenAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = signal.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "Langkah Pemikiran Mendalam:",
                                fontSize = 11.sp,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = signal.thinkingSummary,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onExecuteBot,
                        modifier = Modifier.fillMaxWidth().testTag("execute_signal_bot_${signal.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = BullGreen, contentColor = ObsidianBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pasang Bot Berdasarkan Sinyal Ini", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (mt5Connected) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onSendToMt5,
                            modifier = Modifier.fillMaxWidth().testTag("send_signal_mt5_${signal.id}"),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Text("Eksekusi Sinyal ke Exness MT5", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
