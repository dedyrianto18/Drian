package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CandleStick
import com.example.data.model.TechnicalIndicators
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.NeonCyan
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
fun CandleStickChart(
    candles: List<CandleStick>,
    indicators: TechnicalIndicators? = null,
    gridRungs: List<Double> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(SurfaceDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Memuat data candlestick...", color = TextSecondary)
        }
        return
    }

    var selectedCandleIndex by remember { mutableStateOf<Int?>(null) }
    val displayCandle = selectedCandleIndex?.let { index ->
        if (index in candles.indices) candles[index] else candles.last()
    } ?: candles.last()

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Candle details header
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${dateFormat.format(Date(displayCandle.timestamp))}  ",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "O: ${"%.2f".format(displayCandle.open)} ",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "H: ${"%.2f".format(displayCandle.high)} ",
                style = MaterialTheme.typography.labelSmall,
                color = BullGreen,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "L: ${"%.2f".format(displayCandle.low)} ",
                style = MaterialTheme.typography.labelSmall,
                color = BearRed,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "C: ${"%.2f".format(displayCandle.close)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (displayCandle.close >= displayCandle.open) BullGreen else BearRed,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Indicators badge bar
        if (indicators != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RSI: ${"%.1f".format(indicators.rsi)} ",
                    fontSize = 11.sp,
                    color = when {
                        indicators.rsi > 70 -> BearRed
                        indicators.rsi < 30 -> BullGreen
                        else -> NeonCyan
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "BB: [${"%.1f".format(indicators.bollingerLower)} - ${"%.1f".format(indicators.bollingerUpper)}] ",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = "MACD: ${"%.2f".format(indicators.macd)}",
                    fontSize = 11.sp,
                    color = if (indicators.macd >= 0) BullGreen else BearRed
                )
            }
        }

        // The Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(candles) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                val candleWidth = size.width / candles.size
                                val index = (change.position.x / candleWidth).toInt().coerceIn(0, candles.lastIndex)
                                selectedCandleIndex = index
                            },
                            onDragEnd = {
                                selectedCandleIndex = null
                            }
                        )
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val highestPrice = maxOf(
                    candles.maxOf { it.high },
                    indicators?.bollingerUpper ?: 0.0,
                    gridRungs.maxOrNull() ?: 0.0
                )
                val lowestPrice = minOf(
                    candles.minOf { it.low },
                    indicators?.bollingerLower ?: Double.MAX_VALUE,
                    gridRungs.minOrNull() ?: Double.MAX_VALUE
                )
                val priceRange = (highestPrice - lowestPrice).coerceAtLeast(1.0)
                val maxVolume = candles.maxOfOrNull { it.volume } ?: 1.0

                fun getY(price: Double): Float {
                    return (canvasHeight - ((price - lowestPrice) / priceRange * canvasHeight * 0.85f)).toFloat()
                }

                // Draw Horizontal Grid Guidelines
                val gridSteps = 4
                for (i in 0..gridSteps) {
                    val y = canvasHeight * (i.toFloat() / gridSteps)
                    drawLine(
                        color = OutlineDark,
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                // Draw Grid Rungs (if any active for Grid Bot)
                gridRungs.forEach { rungPrice ->
                    val rungY = getY(rungPrice)
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.5f),
                        start = Offset(0f, rungY),
                        end = Offset(canvasWidth, rungY),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }

                // Draw Bollinger Bands if present
                if (indicators != null && candles.size > 5) {
                    val upperPath = Path()
                    val lowerPath = Path()
                    val candleStep = canvasWidth / candles.size

                    candles.forEachIndexed { i, candle ->
                        val x = (i * candleStep) + (candleStep / 2)
                        val uY = getY(indicators.bollingerUpper)
                        val lY = getY(indicators.bollingerLower)
                        if (i == 0) {
                            upperPath.moveTo(x, uY)
                            lowerPath.moveTo(x, lY)
                        } else {
                            upperPath.lineTo(x, uY)
                            lowerPath.lineTo(x, lY)
                        }
                    }

                    drawPath(
                        path = upperPath,
                        color = NeonCyan.copy(alpha = 0.4f),
                        style = Stroke(width = 1.5f)
                    )
                    drawPath(
                        path = lowerPath,
                        color = NeonCyan.copy(alpha = 0.4f),
                        style = Stroke(width = 1.5f)
                    )
                }

                // Draw Candlesticks & Volume Bars
                val candleWidth = canvasWidth / candles.size
                val barWidth = (candleWidth * 0.7f).coerceAtLeast(2f)

                candles.forEachIndexed { index, candle ->
                    val x = index * candleWidth + (candleWidth / 2)
                    val isBullish = candle.close >= candle.open
                    val candleColor = if (isBullish) BullGreen else BearRed

                    // Volume Bar (bottom 25% height)
                    val volumeHeight = (candle.volume / maxVolume * canvasHeight * 0.22f).toFloat()
                    val volY = canvasHeight - volumeHeight
                    drawRect(
                        color = candleColor.copy(alpha = 0.25f),
                        topLeft = Offset(x - (barWidth / 2), volY),
                        size = Size(barWidth, volumeHeight)
                    )

                    // Wick
                    val highY = getY(candle.high)
                    val lowY = getY(candle.low)
                    drawLine(
                        color = candleColor,
                        start = Offset(x, highY),
                        end = Offset(x, lowY),
                        strokeWidth = 1.5f
                    )

                    // Body
                    val openY = getY(candle.open)
                    val closeY = getY(candle.close)
                    val topY = minOf(openY, closeY)
                    val bodyHeight = kotlin.math.abs(closeY - openY).coerceAtLeast(2f)

                    drawRect(
                        color = candleColor,
                        topLeft = Offset(x - (barWidth / 2), topY),
                        size = Size(barWidth, bodyHeight)
                    )
                }

                // Draw touch crosshair if user is scrubbing
                selectedCandleIndex?.let { idx ->
                    if (idx in candles.indices) {
                        val touchX = idx * candleWidth + (candleWidth / 2)
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(touchX, 0f),
                            end = Offset(touchX, canvasHeight),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )
                    }
                }
            }
        }
    }
}
