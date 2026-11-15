package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BotOrder
import com.example.data.model.BotStatus
import com.example.data.model.CryptoPair
import com.example.data.model.OrderType
import com.example.data.model.StrategyType
import com.example.data.model.TradingBot
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedContainer
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenContainer
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanContainer
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PulsingStatusDot(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(TextMuted)
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(BullGreen.copy(alpha = alpha))
            .border(1.5.dp, BullGreen, CircleShape)
    )
}

@Composable
fun PairTickerChip(
    pair: CryptoPair,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPositive = pair.priceChange24h >= 0

    Box(
        modifier = modifier
            .testTag("pair_chip_${pair.symbol}")
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) SurfaceHighlight else SurfaceDark)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) NeonCyan else OutlineDark,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = pair.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${if (isPositive) "+" else ""}${"%.2f".format(pair.priceChange24h)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPositive) BullGreen else BearRed,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "%.${pair.digits}f".format(pair.lastPrice),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun BotCard(
    bot: TradingBot,
    onToggleStatus: () -> Unit,
    onStopBot: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isProfit = bot.totalProfitUsdt >= 0

    Card(
        modifier = modifier
            .testTag("bot_card_${bot.id}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OutlineDark, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (bot.strategyType == StrategyType.GRID) NeonCyanContainer else BullGreenContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (bot.strategyType == StrategyType.GRID) Icons.Default.GridOn else Icons.Default.AutoGraph,
                            contentDescription = null,
                            tint = if (bot.strategyType == StrategyType.GRID) NeonCyan else BullGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = bot.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulsingStatusDot(isActive = bot.status == BotStatus.RUNNING)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${bot.pairSymbol} • Exness MT5 AI",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleStatus,
                        modifier = Modifier.testTag("bot_toggle_${bot.id}")
                    ) {
                        Icon(
                            imageVector = if (bot.status == BotStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Status",
                            tint = if (bot.status == BotStatus.RUNNING) GoldenAmber else BullGreen
                        )
                    }

                    IconButton(
                        onClick = onStopBot,
                        modifier = Modifier.testTag("bot_stop_${bot.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Bot",
                            tint = BearRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metrics Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Total Profit", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "${if (isProfit) "+" else ""}${"%.2f".format(bot.totalProfitUsdt)} USDT",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isProfit) BullGreen else BearRed,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Return", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "${if (isProfit) "+" else ""}${"%.2f".format(bot.profitPercentage)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isProfit) BullGreen else BearRed,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Arbitrage Trades", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "${bot.executedTradesCount} Fills",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun OrderLadderView(
    orders: List<BotOrder>,
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .border(1.dp, OutlineDark, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Active Order Ladder (${orders.size} Rungs)",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (orders.isEmpty()) {
            Text(
                text = "Tidak ada antrean order aktif saat ini.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            orders.sortedByDescending { it.price }.forEach { order ->
                val isBuy = order.orderType == OrderType.BUY
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(
                            if (isBuy) BullGreenContainer else BearRedContainer,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBuy) "BUY LIMIT" else "SELL LIMIT",
                        fontSize = 11.sp,
                        color = if (isBuy) BullGreen else BearRed,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${"%.2f".format(order.price)} USDT",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Qty: ${"%.4f".format(order.quantity)}",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
