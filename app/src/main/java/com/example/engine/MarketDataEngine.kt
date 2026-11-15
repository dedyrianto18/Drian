package com.example.engine

import com.example.data.model.CandleStick
import com.example.data.model.CryptoPair
import com.example.data.model.Mt5Quote
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class MarketDataEngine {

    private val basePairs = ConcurrentHashMap(
        mapOf(
            // Crypto Markets (Exness Standard/Pro/Raw)
            "BTCUSDm" to CryptoPair(
                symbol = "BTCUSDm",
                name = "Bitcoin / USD",
                lastPrice = 88450.0,
                priceChange24h = 3.42,
                high24h = 89600.0,
                low24h = 86120.0,
                volume24h = 1425890000.0,
                candles = generateHistoricalCandles(88450.0, 0.008, 40),
                category = "Crypto",
                bidPrice = 88435.0,
                askPrice = 88465.0,
                digits = 2
            ),
            "ETHUSDm" to CryptoPair(
                symbol = "ETHUSDm",
                name = "Ethereum / USD",
                lastPrice = 3380.50,
                priceChange24h = 2.15,
                high24h = 3450.0,
                low24h = 3290.0,
                volume24h = 894500000.0,
                candles = generateHistoricalCandles(3380.5, 0.012, 40),
                category = "Crypto",
                bidPrice = 3379.80,
                askPrice = 3381.20,
                digits = 2
            ),
            "SOLUSDm" to CryptoPair(
                symbol = "SOLUSDm",
                name = "Solana / USD",
                lastPrice = 214.75,
                priceChange24h = 6.84,
                high24h = 222.0,
                low24h = 201.5,
                volume24h = 625100000.0,
                candles = generateHistoricalCandles(214.75, 0.02, 40),
                category = "Crypto",
                bidPrice = 214.65,
                askPrice = 214.85,
                digits = 2
            ),
            "XRPUSDm" to CryptoPair(
                symbol = "XRPUSDm",
                name = "Ripple / USD",
                lastPrice = 1.4520,
                priceChange24h = 4.10,
                high24h = 1.5100,
                low24h = 1.3850,
                volume24h = 310200000.0,
                candles = generateHistoricalCandles(1.452, 0.015, 40),
                category = "Crypto",
                bidPrice = 1.4514,
                askPrice = 1.4526,
                digits = 4
            ),
            // Metals (Exness)
            "XAUUSDm" to CryptoPair(
                symbol = "XAUUSDm",
                name = "Gold / USD",
                lastPrice = 2648.40,
                priceChange24h = 1.18,
                high24h = 2662.0,
                low24h = 2621.5,
                volume24h = 980400000.0,
                candles = generateHistoricalCandles(2648.4, 0.004, 40),
                category = "Metals",
                bidPrice = 2648.20,
                askPrice = 2648.60,
                digits = 2
            ),
            "XAGUSDm" to CryptoPair(
                symbol = "XAGUSDm",
                name = "Silver / USD",
                lastPrice = 31.425,
                priceChange24h = 0.85,
                high24h = 31.950,
                low24h = 30.880,
                volume24h = 215000000.0,
                candles = generateHistoricalCandles(31.425, 0.006, 40),
                category = "Metals",
                bidPrice = 31.410,
                askPrice = 31.440,
                digits = 3
            ),
            // Forex Majors (Exness)
            "EURUSDm" to CryptoPair(
                symbol = "EURUSDm",
                name = "Euro / US Dollar",
                lastPrice = 1.08420,
                priceChange24h = 0.24,
                high24h = 1.08750,
                low24h = 1.08010,
                volume24h = 1950000000.0,
                candles = generateHistoricalCandles(1.0842, 0.0018, 40),
                category = "Forex",
                bidPrice = 1.08414,
                askPrice = 1.08426,
                digits = 5
            ),
            "GBPUSDm" to CryptoPair(
                symbol = "GBPUSDm",
                name = "Pound / US Dollar",
                lastPrice = 1.29340,
                priceChange24h = -0.18,
                high24h = 1.29810,
                low24h = 1.28990,
                volume24h = 1120000000.0,
                candles = generateHistoricalCandles(1.2934, 0.002, 40),
                category = "Forex",
                bidPrice = 1.29332,
                askPrice = 1.29348,
                digits = 5
            ),
            "USDJPYm" to CryptoPair(
                symbol = "USDJPYm",
                name = "US Dollar / Yen",
                lastPrice = 154.620,
                priceChange24h = 0.52,
                high24h = 155.100,
                low24h = 153.850,
                volume24h = 1480000000.0,
                candles = generateHistoricalCandles(154.62, 0.0022, 40),
                category = "Forex",
                bidPrice = 154.611,
                askPrice = 154.629,
                digits = 3
            ),
            "AUDUSDm" to CryptoPair(
                symbol = "AUDUSDm",
                name = "Aussie / US Dollar",
                lastPrice = 0.65840,
                priceChange24h = 0.14,
                high24h = 0.66120,
                low24h = 0.65480,
                volume24h = 640000000.0,
                candles = generateHistoricalCandles(0.6584, 0.002, 40),
                category = "Forex",
                bidPrice = 0.65833,
                askPrice = 0.65847,
                digits = 5
            ),
            // Energies (Exness)
            "USOILm" to CryptoPair(
                symbol = "USOILm",
                name = "WTI Crude Oil",
                lastPrice = 70.45,
                priceChange24h = -1.12,
                high24h = 71.80,
                low24h = 69.90,
                volume24h = 540000000.0,
                candles = generateHistoricalCandles(70.45, 0.007, 40),
                category = "Energies",
                bidPrice = 70.43,
                askPrice = 70.47,
                digits = 2
            ),
            // Indices (Exness)
            "USTECm" to CryptoPair(
                symbol = "USTECm",
                name = "Nasdaq 100 Index",
                lastPrice = 20945.50,
                priceChange24h = 0.94,
                high24h = 21080.0,
                low24h = 20710.0,
                volume24h = 820000000.0,
                candles = generateHistoricalCandles(20945.5, 0.004, 40),
                category = "Indices",
                bidPrice = 20944.50,
                askPrice = 20946.50,
                digits = 2
            ),
            "US30m" to CryptoPair(
                symbol = "US30m",
                name = "Dow Jones 30",
                lastPrice = 43820.00,
                priceChange24h = 0.41,
                high24h = 44010.0,
                low24h = 43590.0,
                volume24h = 610000000.0,
                candles = generateHistoricalCandles(43820.0, 0.003, 40),
                category = "Indices",
                bidPrice = 43818.50,
                askPrice = 43821.50,
                digits = 2
            )
        )
    )

    fun getAllPairs(): List<CryptoPair> {
        return basePairs.values.map { pair ->
            pair.copy(indicators = TechnicalAnalysis.calculateIndicators(pair.candles))
        }
    }

    fun getPair(symbol: String): CryptoPair {
        val existing = basePairs[symbol]
        if (existing != null) {
            return existing.copy(indicators = TechnicalAnalysis.calculateIndicators(existing.candles))
        }
        val initialPrice = when {
            symbol.startsWith("XAU") -> 2648.0
            symbol.startsWith("EUR") -> 1.084
            symbol.startsWith("XRP") -> 1.45
            else -> 100.0
        }
        val newPair = CryptoPair(
            symbol = symbol,
            name = symbol,
            lastPrice = initialPrice,
            priceChange24h = 0.0,
            high24h = initialPrice * 1.02,
            low24h = initialPrice * 0.98,
            volume24h = 50000000.0,
            candles = generateHistoricalCandles(initialPrice, 0.008, 40),
            category = "Exness",
            bidPrice = initialPrice,
            askPrice = initialPrice,
            digits = if (initialPrice < 10.0) 5 else 2
        )
        basePairs[symbol] = newPair
        return newPair.copy(indicators = TechnicalAnalysis.calculateIndicators(newPair.candles))
    }

    fun addCustomPair(symbol: String, initialPrice: Double, category: String = "Exness") {
        val cleanSymbol = symbol.trim()
        val digits = if (initialPrice < 10.0) 5 else 2
        val newPair = CryptoPair(
            symbol = cleanSymbol,
            name = cleanSymbol,
            lastPrice = initialPrice,
            priceChange24h = 0.0,
            high24h = initialPrice * 1.02,
            low24h = initialPrice * 0.98,
            volume24h = 1000000.0,
            candles = generateHistoricalCandles(initialPrice, 0.008, 40),
            category = category,
            bidPrice = initialPrice,
            askPrice = initialPrice,
            digits = digits
        )
        basePairs[cleanSymbol] = newPair
    }

    fun updateFromMt5Quotes(quotes: List<Mt5Quote>) {
        quotes.forEach { quote ->
            val current = basePairs[quote.symbol] ?: return@forEach
            val px = if (quote.last > 0.0) quote.last else quote.bid
            if (px <= 0.0) return@forEach

            val updatedCandles = current.candles.toMutableList()
            val lastCandle = updatedCandles.lastOrNull()
            if (lastCandle != null) {
                updatedCandles[updatedCandles.lastIndex] = lastCandle.copy(
                    high = maxOf(lastCandle.high, px),
                    low = minOf(lastCandle.low, px),
                    close = px
                )
            }
            basePairs[quote.symbol] = current.copy(
                lastPrice = px,
                bidPrice = quote.bid,
                askPrice = quote.ask,
                high24h = maxOf(current.high24h, px),
                low24h = minOf(current.low24h, px),
                candles = updatedCandles,
                indicators = TechnicalAnalysis.calculateIndicators(updatedCandles)
            )
        }
    }

    fun streamLiveTicks(): Flow<Map<String, CryptoPair>> = flow {
        while (true) {
            basePairs.keys.toList().forEach { symbol ->
                val current = basePairs[symbol] ?: return@forEach
                val volFactor = when (current.category) {
                    "Forex" -> 0.0004
                    "Metals", "Indices" -> 0.0012
                    else -> 0.0025
                }
                val deltaPercent = (Random.nextDouble() - 0.495) * volFactor
                val newPrice = (current.lastPrice * (1.0 + deltaPercent)).coerceAtLeast(0.0001)
                val spread = (current.askPrice - current.bidPrice).coerceAtLeast(newPrice * 0.0001)

                val updatedCandles = current.candles.toMutableList()
                val lastCandle = updatedCandles.lastOrNull()
                val now = System.currentTimeMillis()

                if (lastCandle == null || now - lastCandle.timestamp > 60_000) {
                    updatedCandles.add(
                        CandleStick(
                            timestamp = now,
                            open = newPrice,
                            high = newPrice,
                            low = newPrice,
                            close = newPrice,
                            volume = Random.nextDouble(5.0, 50.0)
                        )
                    )
                    if (updatedCandles.size > 50) updatedCandles.removeAt(0)
                } else {
                    updatedCandles[updatedCandles.lastIndex] = lastCandle.copy(
                        high = maxOf(lastCandle.high, newPrice),
                        low = minOf(lastCandle.low, newPrice),
                        close = newPrice,
                        volume = lastCandle.volume + Random.nextDouble(0.5, 3.0)
                    )
                }

                val updatedPair = current.copy(
                    lastPrice = newPrice,
                    bidPrice = newPrice - spread * 0.5,
                    askPrice = newPrice + spread * 0.5,
                    high24h = maxOf(current.high24h, newPrice),
                    low24h = minOf(current.low24h, newPrice),
                    candles = updatedCandles,
                    indicators = TechnicalAnalysis.calculateIndicators(updatedCandles)
                )
                basePairs[symbol] = updatedPair
            }

            emit(basePairs.toMap())
            delay(1500)
        }
    }

    companion object {
        fun generateHistoricalCandles(basePrice: Double, volatility: Double, count: Int): List<CandleStick> {
            val list = mutableListOf<CandleStick>()
            var currentClose = basePrice * (1.0 - (volatility * count * 0.15))
            val now = System.currentTimeMillis()
            val candleInterval = 5 * 60 * 1000L

            for (i in 0 until count) {
                val time = now - ((count - i) * candleInterval)
                val open = currentClose
                val delta = (Random.nextDouble() - 0.48) * volatility * open
                val close = open + delta
                val high = maxOf(open, close) + (Random.nextDouble() * volatility * open * 0.5)
                val low = minOf(open, close) - (Random.nextDouble() * volatility * open * 0.5)
                val volume = Random.nextDouble(100.0, 1500.0)
                list.add(CandleStick(time, open, high, low, close, volume))
                currentClose = close
            }
            return list
        }
    }
}
