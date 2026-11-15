package com.example.engine

import com.example.data.model.CandleStick
import com.example.data.model.TechnicalIndicators
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object TechnicalAnalysis {

    fun calculateIndicators(candles: List<CandleStick>): TechnicalIndicators {
        if (candles.size < 14) {
            val lastClose = candles.lastOrNull()?.close ?: 0.0
            return TechnicalIndicators(
                rsi = 50.0,
                macd = 0.0,
                macdSignal = 0.0,
                macdHist = 0.0,
                bollingerUpper = lastClose * 1.02,
                bollingerMiddle = lastClose,
                bollingerLower = lastClose * 0.98,
                atr = lastClose * 0.015,
                ema20 = lastClose,
                ema50 = lastClose
            )
        }

        val closes = candles.map { it.close }
        val rsi = calculateRsi(closes, 14)
        val (upper, middle, lower) = calculateBollingerBands(closes, 20, 2.0)
        val (macd, signal, hist) = calculateMacd(closes)
        val atr = calculateAtr(candles, 14)
        val ema20 = calculateEma(closes, 20)
        val ema50 = calculateEma(closes, min(50, closes.size))

        return TechnicalIndicators(
            rsi = rsi,
            macd = macd,
            macdSignal = signal,
            macdHist = hist,
            bollingerUpper = upper,
            bollingerMiddle = middle,
            bollingerLower = lower,
            atr = atr,
            ema20 = ema20,
            ema50 = ema50
        )
    }

    private fun calculateRsi(prices: List<Double>, period: Int): Double {
        if (prices.size <= period) return 50.0
        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {
            val diff = prices[i] - prices[i - 1]
            if (diff >= 0) gains += diff else losses += -diff
        }

        var avgGain = gains / period
        var avgLoss = losses / period

        for (i in (period + 1) until prices.size) {
            val diff = prices[i] - prices[i - 1]
            val gain = if (diff >= 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0

            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun calculateBollingerBands(
        prices: List<Double>,
        period: Int,
        multiplier: Double
    ): Triple<Double, Double, Double> {
        val count = min(prices.size, period)
        if (count == 0) return Triple(0.0, 0.0, 0.0)

        val window = prices.takeLast(count)
        val sma = window.average()
        val variance = window.sumOf { (it - sma).pow(2) } / count
        val stdDev = sqrt(variance)

        val upper = sma + (multiplier * stdDev)
        val lower = sma - (multiplier * stdDev)
        return Triple(upper, sma, lower)
    }

    private fun calculateMacd(prices: List<Double>): Triple<Double, Double, Double> {
        val ema12 = calculateEma(prices, 12)
        val ema26 = calculateEma(prices, 26)
        val macd = ema12 - ema26
        val signal = macd * 0.2 // Simplified signal line approximation
        val hist = macd - signal
        return Triple(macd, signal, hist)
    }

    private fun calculateEma(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        if (prices.size == 1) return prices.first()

        val k = 2.0 / (period + 1)
        var ema = prices.first()

        for (i in 1 until prices.size) {
            ema = (prices[i] * k) + (ema * (1 - k))
        }
        return ema
    }

    private fun calculateAtr(candles: List<CandleStick>, period: Int): Double {
        if (candles.size < 2) return 0.0
        val count = min(candles.size - 1, period)
        var trSum = 0.0

        for (i in (candles.size - count) until candles.size) {
            val current = candles[i]
            val prev = candles[i - 1]
            val tr1 = current.high - current.low
            val tr2 = kotlin.math.abs(current.high - prev.close)
            val tr3 = kotlin.math.abs(current.low - prev.close)
            trSum += max(tr1, max(tr2, tr3))
        }

        return trSum / count
    }

    fun generateGridRungs(lowerBound: Double, upperBound: Double, gridCount: Int): List<Double> {
        if (gridCount <= 1 || upperBound <= lowerBound) return listOf(lowerBound, upperBound)
        val step = (upperBound - lowerBound) / (gridCount - 1)
        return (0 until gridCount).map { lowerBound + (it * step) }
    }
}
