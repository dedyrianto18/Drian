package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BotStatus
import com.example.data.model.StrategyType
import com.example.ui.MainViewModel
import com.example.ui.NavigationScreen
import com.example.ui.components.OrderLadderView
import com.example.ui.components.PulsingStatusDot
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.NeonCyan
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
fun BotDetailScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateTo(NavigationScreen.BOTS)
    }

    val selectedBotId by viewModel.selectedBotId.collectAsState()
    val bots by viewModel.botManager.bots.collectAsState()
    val bot = bots.firstOrNull { it.id == selectedBotId } ?: bots.firstOrNull()
    val pairs by viewModel.pairs.collectAsState()
    val currentPrice = bot?.let { pairs[it.pairSymbol]?.lastPrice } ?: 100.0

    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Back Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(NavigationScreen.BOTS) },
                modifier = Modifier.testTag("back_to_bots_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Telemetri & Eksekusi Bot",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (bot == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Bot tidak ditemukan.", color = TextMuted)
            }
            return
        }

        val isProfit = bot.totalProfitUsdt >= 0

        // Bot Header Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                        Text(
                            text = bot.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulsingStatusDot(isActive = bot.status == BotStatus.RUNNING)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${bot.strategyType.displayName} • ${bot.pairSymbol}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (bot.status) {
                                    BotStatus.RUNNING -> BullGreen.copy(alpha = 0.2f)
                                    BotStatus.PAUSED -> GoldenAmber.copy(alpha = 0.2f)
                                    BotStatus.STOPPED -> BearRed.copy(alpha = 0.2f)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = bot.status.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (bot.status) {
                                BotStatus.RUNNING -> BullGreen
                                BotStatus.PAUSED -> GoldenAmber
                                BotStatus.STOPPED -> BearRed
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Keuntungan", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "${if (isProfit) "+" else ""}${"%.2f".format(bot.totalProfitUsdt)} USDT",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfit) BullGreen else BearRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Return Modal", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "${if (isProfit) "+" else ""}${"%.2f".format(bot.profitPercentage)}%",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfit) BullGreen else BearRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Modal Terpasang", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "${"%,.0f".format(bot.investmentAmountUsdt)} USDT",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Dibuat: ${dateFormat.format(Date(bot.createdAt))} • Total Arbitrase Fills: ${bot.executedTradesCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Strategy Specific Telemetry Details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Parameter Algoritma Aktif", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                if (bot.strategyType == StrategyType.GRID) {
                    bot.gridConfig?.let { grid ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Batas Bawah Koridor:", fontSize = 12.sp, color = TextMuted)
                            Text("${"%.2f".format(grid.lowerBound)} USDT", fontSize = 12.sp, color = BullGreen, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Batas Atas Koridor:", fontSize = 12.sp, color = TextMuted)
                            Text("${"%.2f".format(grid.upperBound)} USDT", fontSize = 12.sp, color = BearRed, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Jumlah Tingkat Jaring:", fontSize = 12.sp, color = TextMuted)
                            Text("${grid.gridCount} Rungs", fontSize = 12.sp, color = NeonCyan)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estimasi Spread per Grid:", fontSize = 12.sp, color = TextMuted)
                            Text("${grid.profitPerGridPercent}%", fontSize = 12.sp, color = GoldenAmber)
                        }
                    }
                } else {
                    bot.breakoutConfig?.let { b ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Breakout Trigger Atas:", fontSize = 12.sp, color = TextMuted)
                            Text("${"%.2f".format(b.breakoutLevelHigh)} USDT", fontSize = 12.sp, color = BullGreen, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Stop Loss Pengaman:", fontSize = 12.sp, color = TextMuted)
                            Text("-${b.stopLossPercent}%", fontSize = 12.sp, color = BearRed)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Target Take Profit:", fontSize = 12.sp, color = TextMuted)
                            Text("+${b.takeProfitPercent}%", fontSize = 12.sp, color = BullGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Trailing Stop:", fontSize = 12.sp, color = TextMuted)
                            Text(if (b.trailingStop) "Aktif" else "Nonaktif", fontSize = 12.sp, color = NeonCyan)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Order Ladder View
        OrderLadderView(orders = bot.openOrders, currentPrice = currentPrice)

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons: Pause/Resume, Emergency Stop, Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.botManager.toggleBotStatus(bot.id) },
                modifier = Modifier.weight(1f).testTag("detail_toggle_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (bot.status == BotStatus.RUNNING) GoldenAmber else BullGreen,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (bot.status == BotStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (bot.status == BotStatus.RUNNING) "Pause Bot" else "Lanjutkan Bot", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { viewModel.botManager.stopBot(bot.id) },
                modifier = Modifier.weight(1f).testTag("detail_stop_btn"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BearRed),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(BearRed))
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop & Batalkan", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                viewModel.botManager.deleteBot(bot.id)
                viewModel.navigateTo(NavigationScreen.BOTS)
            },
            modifier = Modifier.fillMaxWidth().testTag("detail_delete_btn"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Hapus Bot Ini", fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
