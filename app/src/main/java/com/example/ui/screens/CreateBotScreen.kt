package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.model.BotOrder
import com.example.data.model.BotStatus
import com.example.data.model.BreakoutConfig
import com.example.data.model.GridConfig
import com.example.data.model.OrderStatus
import com.example.data.model.OrderType
import com.example.data.model.StrategyType
import com.example.data.model.TradingBot
import com.example.engine.TechnicalAnalysis
import com.example.ui.MainViewModel
import com.example.ui.NavigationScreen
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.UUID

@Composable
fun CreateBotScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val pairs by viewModel.pairs.collectAsState()
    val selectedSymbol by viewModel.selectedPairSymbol.collectAsState()
    val currentPair = pairs[selectedSymbol] ?: pairs.values.firstOrNull()
    val currentPrice = currentPair?.lastPrice ?: 100.0
    val mt5Config by viewModel.mt5BridgeConfig.collectAsState()

    var selectedStrategyTab by remember { mutableIntStateOf(0) } // 0 = Grid, 1 = Volatility Breakout
    var botName by remember { mutableStateOf("") }
    var investmentText by remember { mutableStateOf("1000") }

    // Grid Parameters
    var lowerBoundText by remember(selectedSymbol) { mutableStateOf("${"%.2f".format(currentPrice * 0.90)}") }
    var upperBoundText by remember(selectedSymbol) { mutableStateOf("${"%.2f".format(currentPrice * 1.10)}") }
    var gridCount by remember { mutableIntStateOf(8) }

    // Breakout Parameters
    var breakoutHighText by remember(selectedSymbol) { mutableStateOf("${"%.2f".format(currentPrice * 1.025)}") }
    var breakoutLowText by remember(selectedSymbol) { mutableStateOf("${"%.2f".format(currentPrice * 0.975)}") }
    var stopLossPercent by remember { mutableFloatStateOf(1.5f) }
    var takeProfitPercent by remember { mutableFloatStateOf(4.0f) }
    var trailingStop by remember { mutableStateOf(true) }

    val lowerBound = lowerBoundText.toDoubleOrNull() ?: (currentPrice * 0.9)
    val upperBound = upperBoundText.toDoubleOrNull() ?: (currentPrice * 1.1)
    val calculatedRungs = remember(lowerBound, upperBound, gridCount) {
        TechnicalAnalysis.generateGridRungs(lowerBound, upperBound, gridCount)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Buat Bot Trading Otomatis",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Pilih strategi Grid Trading atau Volatility Breakout dengan eksekusi algoritma 24/7.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Strategy Selector Tab
        TabRow(
            selectedTabIndex = selectedStrategyTab,
            containerColor = SurfaceDark,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedStrategyTab]),
                    color = NeonCyan
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, OutlineDark, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedStrategyTab == 0,
                onClick = { selectedStrategyTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grid Trading", fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("tab_grid_trading")
            )
            Tab(
                selected = selectedStrategyTab == 1,
                onClick = { selectedStrategyTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Volatility Breakout", fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("tab_volatility_breakout")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Basic Bot Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Pengaturan Dasar", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = if (botName.isBlank()) "${currentPair?.symbol} ${if (selectedStrategyTab == 0) "Grid" else "Breakout"} Bot" else botName,
                    onValueChange = { botName = it },
                    label = { Text("Nama Bot") },
                    modifier = Modifier.fillMaxWidth().testTag("bot_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = investmentText,
                        onValueChange = { investmentText = it },
                        label = { Text("Modal (USD)") },
                        modifier = Modifier.weight(1f).testTag("bot_investment_input")
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(SurfaceElevated, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Harga Pasar Exness", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "%.${currentPair?.digits ?: 2}f".format(currentPrice),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Exness Execution Target Info (No Sandbox/Paper)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Akun Eksekusi: Exness ${mt5Config.accountMode}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (mt5Config.accountMode == "REAL") BullGreen else NeonCyan
                        )
                        Text(
                            text = "Server: ${mt5Config.brokerServer.ifBlank { "Exness-MT5Trial" }} • Lot Bridge: ${mt5Config.lotSize}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Strategy-Specific Configurations
        if (selectedStrategyTab == 0) {
            // Grid Strategy Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Parameter Koridor Grid", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                // AI Auto-tune parameters based on current price
                                lowerBoundText = "${"%.2f".format(currentPrice * 0.92)}"
                                upperBoundText = "${"%.2f".format(currentPrice * 1.08)}"
                                gridCount = 10
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f), contentColor = NeonCyan),
                            modifier = Modifier.testTag("ai_autotune_grid_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Auto-Tune", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = lowerBoundText,
                            onValueChange = { lowerBoundText = it },
                            label = { Text("Batas Bawah (USDT)") },
                            modifier = Modifier.weight(1f).testTag("grid_lower_bound_input")
                        )
                        OutlinedTextField(
                            value = upperBoundText,
                            onValueChange = { upperBoundText = it },
                            label = { Text("Batas Atas (USDT)") },
                            modifier = Modifier.weight(1f).testTag("grid_upper_bound_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Jumlah Jaring Grid: $gridCount Tingkat",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = gridCount.toFloat(),
                        onValueChange = { gridCount = it.toInt() },
                        valueRange = 4f..20f,
                        steps = 15,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
                        modifier = Modifier.testTag("grid_count_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Estimated profit per grid
                    val spread = if (gridCount > 1) ((upperBound - lowerBound) / (gridCount - 1) / currentPrice) * 100 else 0.0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Estimasi Profit per Grid", fontSize = 12.sp, color = TextMuted)
                        Text(
                            text = "${"%.2f".format(spread)}% (dikurangi fee)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BullGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Rungs preview
                    Text("Pratinjau Level Order Beli / Jual:", fontSize = 12.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    calculatedRungs.take(6).forEach { rung ->
                        val isBuy = rung < currentPrice
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBuy) "Level Beli (Buy Limit)" else "Level Jual (Sell Limit)",
                                fontSize = 11.sp,
                                color = if (isBuy) BullGreen else BearRed
                            )
                            Text(
                                text = "${"%.2f".format(rung)} USDT",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        } else {
            // Volatility Breakout Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Parameter Momentum Breakout", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = breakoutHighText,
                        onValueChange = { breakoutHighText = it },
                        label = { Text("Trigger Breakout Atas (USDT)") },
                        modifier = Modifier.fillMaxWidth().testTag("breakout_high_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = breakoutLowText,
                        onValueChange = { breakoutLowText = it },
                        label = { Text("Trigger Konsolidasi Bawah (USDT)") },
                        modifier = Modifier.fillMaxWidth().testTag("breakout_low_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Take Profit Otomatis: ${"%.1f".format(takeProfitPercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = BullGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = takeProfitPercent,
                        onValueChange = { takeProfitPercent = it },
                        valueRange = 1.0f..15.0f,
                        colors = SliderDefaults.colors(thumbColor = BullGreen, activeTrackColor = BullGreen),
                        modifier = Modifier.testTag("take_profit_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Stop Loss Pengaman: ${"%.1f".format(stopLossPercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = BearRed,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = stopLossPercent,
                        onValueChange = { stopLossPercent = it },
                        valueRange = 0.5f..5.0f,
                        colors = SliderDefaults.colors(thumbColor = BearRed, activeTrackColor = BearRed),
                        modifier = Modifier.testTag("stop_loss_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gunakan Trailing Stop Dinamis", fontSize = 12.sp, color = TextPrimary)
                        Switch(
                            checked = trailingStop,
                            onCheckedChange = { trailingStop = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Launch Bot Button
        Button(
            onClick = {
                val investment = investmentText.toDoubleOrNull() ?: 1000.0
                val pair = currentPair?.symbol ?: "SOL/USDT"
                val name = if (botName.isNotBlank()) botName else "$pair ${if (selectedStrategyTab == 0) "Grid" else "Breakout"} Bot"
                val botId = UUID.randomUUID().toString()

                val newBot = if (selectedStrategyTab == 0) {
                    val orders = mutableListOf<BotOrder>()
                    calculatedRungs.forEach { rungPrice ->
                        val isBuy = rungPrice < currentPrice
                        orders.add(
                            BotOrder(
                                id = UUID.randomUUID().toString(),
                                botId = botId,
                                pairSymbol = pair,
                                orderType = if (isBuy) OrderType.BUY else OrderType.SELL,
                                price = rungPrice,
                                quantity = (investment / gridCount) / rungPrice,
                                status = OrderStatus.PENDING,
                                isSimulated = false,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }

                    TradingBot(
                        id = botId,
                        name = name,
                        pairSymbol = pair,
                        strategyType = StrategyType.GRID,
                        status = BotStatus.RUNNING,
                        isPaperTrading = false,
                        investmentAmountUsdt = investment,
                        totalProfitUsdt = 0.0,
                        profitPercentage = 0.0,
                        executedTradesCount = 0,
                        createdAt = System.currentTimeMillis(),
                        gridConfig = GridConfig(
                            upperBound = upperBound,
                            lowerBound = lowerBound,
                            gridCount = gridCount,
                            investmentUsdt = investment,
                            profitPerGridPercent = 0.8
                        ),
                        openOrders = orders
                    )
                } else {
                    TradingBot(
                        id = botId,
                        name = name,
                        pairSymbol = pair,
                        strategyType = StrategyType.VOLATILITY_BREAKOUT,
                        status = BotStatus.RUNNING,
                        isPaperTrading = false,
                        investmentAmountUsdt = investment,
                        totalProfitUsdt = 0.0,
                        profitPercentage = 0.0,
                        executedTradesCount = 0,
                        createdAt = System.currentTimeMillis(),
                        breakoutConfig = BreakoutConfig(
                            squeezeThreshold = 1.5,
                            breakoutLevelHigh = breakoutHighText.toDoubleOrNull() ?: (currentPrice * 1.02),
                            breakoutLevelLow = breakoutLowText.toDoubleOrNull() ?: (currentPrice * 0.98),
                            stopLossPercent = stopLossPercent.toDouble(),
                            takeProfitPercent = takeProfitPercent.toDouble(),
                            trailingStop = trailingStop,
                            investmentUsdt = investment
                        ),
                        openOrders = emptyList()
                    )
                }

                viewModel.botManager.startNewBot(newBot)
                viewModel.navigateTo(NavigationScreen.BOTS)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("launch_bot_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = BullGreen, contentColor = ObsidianBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Luncurkan & Jalankan Bot Otomatis", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
