package com.example

import com.example.data.model.AiExecutedTradeRecord
import com.example.data.model.CandleStick
import com.example.data.model.CryptoPair
import com.example.data.model.Mt5Quote
import com.example.engine.MarketDataEngine
import com.example.engine.TechnicalAnalysis
import com.example.engine.TradingBotManager
import com.example.network.Mt5BridgeService
import com.example.util.GeminiAppBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `test app technical analysis and grid generation`() {
        val lower = 100.0
        val upper = 200.0
        val count = 5
        val rungs = TechnicalAnalysis.generateGridRungs(lower, upper, count)

        assertEquals(5, rungs.size)
        assertEquals(100.0, rungs.first(), 0.001)
        assertEquals(200.0, rungs.last(), 0.001)
        assertEquals(125.0, rungs[1], 0.001)
    }

    @Test
    fun `test indicator computation`() {
        val candles = (1..30).map { i ->
            CandleStick(
                timestamp = System.currentTimeMillis() + (i * 60000L),
                open = 100.0 + i,
                high = 105.0 + i,
                low = 95.0 + i,
                close = 102.0 + i,
                volume = 500.0
            )
        }

        val indicators = TechnicalAnalysis.calculateIndicators(candles)
        assertNotNull(indicators)
        assertTrue(indicators.rsi in 0.0..100.0)
        assertTrue(indicators.bollingerUpper >= indicators.bollingerLower)
    }

    @Test
    fun `test grid bot tick fills buy and sell orders safely`() {
        val manager = TradingBotManager()
        val initialBot = manager.bots.value.first { it.pairSymbol == "SOLUSDm" }
        val initialTrades = initialBot.executedTradesCount

        val lowTick = mapOf(
            "SOLUSDm" to CryptoPair("SOLUSDm", "Solana / USD", 190.0, 0.0, 220.0, 190.0, 1000.0)
        )
        manager.onPriceTick(lowTick)

        val highTick = mapOf(
            "SOLUSDm" to CryptoPair("SOLUSDm", "Solana / USD", 240.0, 0.0, 240.0, 190.0, 1000.0)
        )
        manager.onPriceTick(highTick)

        val updatedBot = manager.bots.value.first { it.id == initialBot.id }
        assertTrue(updatedBot.executedTradesCount > initialTrades)
        assertTrue(manager.orderHistory.value.isNotEmpty())
    }

    @Test
    fun `test exness markets coverage and live quote update`() {
        val engine = MarketDataEngine()
        val pairs = engine.getAllPairs()
        val categories = pairs.map { it.category }.toSet()

        assertTrue(categories.contains("Crypto"))
        assertTrue(categories.contains("Metals"))
        assertTrue(categories.contains("Forex"))
        assertTrue(categories.contains("Energies"))
        assertTrue(categories.contains("Indices"))

        engine.updateFromMt5Quotes(listOf(Mt5Quote("XAUUSDm", 2655.10, 2655.50, 2655.30)))
        val gold = engine.getPair("XAUUSDm")
        assertEquals(2655.30, gold.lastPrice, 0.001)
        assertEquals(2655.10, gold.bidPrice, 0.001)
    }

    @Test
    fun `test mt5 bridge rejects invalid bridge url`() = runBlocking {
        val service = Mt5BridgeService()
        val result = service.checkHealth("invalid-url", "secret")
        assertTrue(result.isFailure)
    }

    @Test
    fun `test ai trade history records filter and pnl aggregation`() {
        val history = listOf(
            AiExecutedTradeRecord(
                id = "1",
                ticketOrOrderId = "MT5-101",
                pairSymbol = "BTCUSDm",
                brokerOrExchange = "Exness-MT5Real",
                strategyName = "Volatility Breakout",
                action = "BUY",
                entryPrice = 87000.0,
                exitPrice = 88000.0,
                volumeOrQty = 0.01,
                pnlUsdt = 10.0,
                status = "CLOSED",
                aiReasoning = "Breakout BB Upper",
                timestamp = 1000L
            ),
            AiExecutedTradeRecord(
                id = "2",
                ticketOrOrderId = "MT5-202",
                pairSymbol = "EURUSDm",
                brokerOrExchange = "Exness-MT5Trial",
                strategyName = "Grid Trading",
                action = "SELL",
                entryPrice = 1.0840,
                exitPrice = 1.0820,
                volumeOrQty = 0.10,
                pnlUsdt = 20.0,
                status = "CLOSED",
                aiReasoning = "Grid Take Profit",
                timestamp = 2000L
            )
        )

        val exnessRealTrades = history.filter { it.brokerOrExchange.contains("Real", ignoreCase = true) }
        assertEquals(1, exnessRealTrades.size)
        assertEquals(30.0, history.sumOf { it.pnlUsdt }, 0.001)
    }

    @Test
    fun `test background service state and gemini package constant`() {
        assertEquals("com.google.android.apps.bard", GeminiAppBridge.GEMINI_PACKAGE_NAME)

        AppContainer.updateBackgroundState {
            it.copy(isRunning = true, isInternetConnected = true, syncCount = 5)
        }
        assertTrue(AppContainer.backgroundState.value.isRunning)
        assertEquals(5, AppContainer.backgroundState.value.syncCount)

        AppContainer.updateBackgroundState {
            it.copy(isRunning = false, syncCount = 0)
        }
        assertFalse(AppContainer.backgroundState.value.isRunning)
    }
}
