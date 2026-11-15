package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiExecutedTradeRecord
import com.example.data.model.BotStatus
import com.example.ui.MainViewModel
import com.example.ui.NavigationScreen
import com.example.ui.components.BotCard
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedContainer
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenContainer
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BotsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val bots by viewModel.botManager.bots.collectAsState()
    val aiTradeHistory by viewModel.aiTradeHistory.collectAsState()

    var activeSectionIndex by remember { mutableIntStateOf(0) } // 0=Bots, 1=Histori Trading AI
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0=All, 1=Running, 2=Paused/Stopped
    var historyFilterIndex by remember { mutableIntStateOf(0) } // 0=All, 1=Grid, 2=Breakout, 3=MT5/Exness

    val filteredBots = remember(bots, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> bots.filter { it.status == BotStatus.RUNNING }
            2 -> bots.filter { it.status != BotStatus.RUNNING }
            else -> bots
        }
    }

    val filteredHistory = remember(aiTradeHistory, historyFilterIndex) {
        when (historyFilterIndex) {
            1 -> aiTradeHistory.filter { it.strategyName.contains("Grid", ignoreCase = true) }
            2 -> aiTradeHistory.filter { it.strategyName.contains("Breakout", ignoreCase = true) }
            3 -> aiTradeHistory.filter {
                it.brokerOrExchange.contains("MT5", ignoreCase = true) ||
                    it.brokerOrExchange.contains("Exness", ignoreCase = true)
            }
            else -> aiTradeHistory
        }
    }

    val totalProfit = bots.sumOf { it.totalProfitUsdt }
    val totalTrades = bots.sumOf { it.executedTradesCount }

    Scaffold(
        floatingActionButton = {
            if (activeSectionIndex == 0) {
                FloatingActionButton(
                    onClick = { viewModel.navigateTo(NavigationScreen.CREATE_BOT) },
                    containerColor = NeonCyan,
                    contentColor = ObsidianBg,
                    modifier = Modifier.testTag("fab_create_bot")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Bot")
                }
            }
        },
        containerColor = ObsidianBg
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Bot AI & Histori Trading",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kelola bot aktif dan pantau histori transaksi yang dieksekusi oleh AI.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Top Section Tabs: Bot Algoritma vs Histori Trading AI
            TabRow(
                selectedTabIndex = activeSectionIndex,
                containerColor = SurfaceElevated,
                contentColor = NeonCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeSectionIndex]),
                        color = NeonCyan
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = activeSectionIndex == 0,
                    onClick = { activeSectionIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bot Aktif (${bots.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_bot_algorithms")
                )
                Tab(
                    selected = activeSectionIndex == 1,
                    onClick = { activeSectionIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Histori AI (${aiTradeHistory.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_ai_trade_history")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (activeSectionIndex == 0) {
                // Summary Card for Bots
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Total Bot", fontSize = 11.sp, color = TextMuted)
                            Text(text = "${bots.size} Unit", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Total Arbitrase Fill", fontSize = 11.sp, color = TextMuted)
                            Text(text = "$totalTrades Transaksi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Akumulasi PnL", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = "${if (totalProfit >= 0) "+" else ""}${"%.2f".format(totalProfit)} USDT",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalProfit >= 0) BullGreen else BearRed,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Tabs for Bots
                TabRow(
                    selectedTabIndex = selectedFilterIndex,
                    containerColor = SurfaceDark,
                    contentColor = NeonCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedFilterIndex]),
                            color = NeonCyan
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedFilterIndex == 0,
                        onClick = { selectedFilterIndex = 0 },
                        text = { Text("Semua (${bots.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedFilterIndex == 1,
                        onClick = { selectedFilterIndex = 1 },
                        text = { Text("Berjalan (${bots.count { it.status == BotStatus.RUNNING }})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedFilterIndex == 2,
                        onClick = { selectedFilterIndex = 2 },
                        text = { Text("Non-Aktif (${bots.count { it.status != BotStatus.RUNNING }})", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredBots.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(SurfaceDark, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tidak ada bot dalam kategori ini.", color = TextMuted)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredBots, key = { it.id }) { bot ->
                            BotCard(
                                bot = bot,
                                onToggleStatus = { viewModel.botManager.toggleBotStatus(bot.id) },
                                onStopBot = { viewModel.botManager.stopBot(bot.id) },
                                onClick = { viewModel.selectBot(bot.id) }
                            )
                        }
                    }
                }
            } else {
                // Histori Trading AI View
                val closedRecords = aiTradeHistory.filter { it.status == "CLOSED" }
                val winRate = if (closedRecords.isNotEmpty()) {
                    (closedRecords.count { it.pnlUsdt > 0 }.toDouble() / closedRecords.size) * 100.0
                } else {
                    0.0
                }
                val historyPnl = aiTradeHistory.sumOf { it.pnlUsdt }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Total Trade AI", fontSize = 11.sp, color = TextMuted)
                            Text(text = "${aiTradeHistory.size} Order", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Win Rate", fontSize = 11.sp, color = TextMuted)
                            Text(text = "${"%.1f".format(winRate)}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "PnL Realisasi AI", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = "${if (historyPnl >= 0) "+" else ""}${"%.2f".format(historyPnl)} USD",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (historyPnl >= 0) BullGreen else BearRed,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (aiTradeHistory.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearAiTradeHistory() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_clear_ai_history")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Bersihkan Histori AI", tint = TextMuted)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf("Semua", "Grid", "Breakout", "MT5 / Exness")
                    chips.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = historyFilterIndex == idx,
                            onClick = { historyFilterIndex = idx },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyanContainer,
                                selectedLabelColor = NeonCyan
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("history_filter_chip_$idx")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(SurfaceDark, RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Belum ada histori transaksi AI di filter ini.", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredHistory, key = { it.id }) { record ->
                            AiTradeHistoryCard(record = record)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTradeHistoryCard(record: AiExecutedTradeRecord) {
    val isBuy = record.action.equals("BUY", ignoreCase = true)
    val timeStr = remember(record.timestamp) {
        SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineDark, RoundedCornerShape(14.dp))
            .testTag("ai_history_card_${record.ticketOrOrderId}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isBuy) BullGreenContainer else BearRedContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = record.action,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBuy) BullGreen else BearRed
                        )
                    }
                    Text(
                        text = record.pairSymbol,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = record.brokerOrExchange,
                            fontSize = 10.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "${if (record.pnlUsdt >= 0) "+" else ""}${"%.2f".format(record.pnlUsdt)} USD",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (record.pnlUsdt >= 0) BullGreen else BearRed,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${record.ticketOrOrderId} • ${record.strategyName}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = "$timeStr • ${record.status}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entry: ${"%.2f".format(record.entryPrice)}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Exit: ${record.exitPrice?.let { "%.2f".format(it) } ?: "Aktif"}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Vol: ${"%.3f".format(record.volumeOrQty)}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                )
                Text(
                    text = record.aiReasoning,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
