package com.example.data.model

data class CandleStick(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class TechnicalIndicators(
    val rsi: Double,
    val macd: Double,
    val macdSignal: Double,
    val macdHist: Double,
    val bollingerUpper: Double,
    val bollingerMiddle: Double,
    val bollingerLower: Double,
    val atr: Double,
    val ema20: Double,
    val ema50: Double
)

data class CryptoPair(
    val symbol: String,
    val name: String,
    val lastPrice: Double,
    val priceChange24h: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24h: Double,
    val candles: List<CandleStick> = emptyList(),
    val indicators: TechnicalIndicators? = null,
    val category: String = "Crypto",
    val bidPrice: Double = lastPrice,
    val askPrice: Double = lastPrice,
    val digits: Int = 2
)

enum class StrategyType(val displayName: String, val description: String) {
    GRID("Grid Trading", "Menjaring profit dari volatilitas harga dengan memasang jaring order beli rendah & jual tinggi secara bertingkat."),
    VOLATILITY_BREAKOUT("Volatility Breakout", "Mendeteksi konsolidasi Bollinger squeeze dan lonjakan volume untuk mengeksekusi momentum breakout secara otomatis.")
}

enum class BotStatus {
    RUNNING,
    PAUSED,
    STOPPED
}

enum class OrderType {
    BUY,
    SELL
}

enum class OrderStatus {
    PENDING,
    FILLED,
    CANCELLED
}

data class GridConfig(
    val upperBound: Double,
    val lowerBound: Double,
    val gridCount: Int,
    val investmentUsdt: Double,
    val profitPerGridPercent: Double = 0.65
)

data class BreakoutConfig(
    val squeezeThreshold: Double,
    val breakoutLevelHigh: Double,
    val breakoutLevelLow: Double,
    val stopLossPercent: Double,
    val takeProfitPercent: Double,
    val trailingStop: Boolean = true,
    val investmentUsdt: Double
)

data class BotOrder(
    val id: String,
    val botId: String,
    val pairSymbol: String,
    val orderType: OrderType,
    val price: Double,
    val quantity: Double,
    val status: OrderStatus,
    val isSimulated: Boolean,
    val timestamp: Long,
    val filledAt: Long? = null
)

data class TradingBot(
    val id: String,
    val name: String,
    val pairSymbol: String,
    val strategyType: StrategyType,
    val status: BotStatus,
    val isPaperTrading: Boolean = false,
    val investmentAmountUsdt: Double,
    val totalProfitUsdt: Double,
    val profitPercentage: Double,
    val executedTradesCount: Int,
    val createdAt: Long,
    val gridConfig: GridConfig? = null,
    val breakoutConfig: BreakoutConfig? = null,
    val openOrders: List<BotOrder> = emptyList()
)

data class AiTradeSignal(
    val id: String,
    val pairSymbol: String,
    val action: String, // "STRONG_BUY", "BREAKOUT_LONG", "GRID_BUY", "TAKE_PROFIT"
    val confidenceScore: Int,
    val strategyName: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val targetPrice: Double,
    val reasoning: String,
    val thinkingSummary: String,
    val timestamp: Long
)

data class StrategyKnowledgeRule(
    val id: String,
    val title: String,
    val instructionPrompt: String,
    val ruleCategory: String, // "Risk Management", "Grid Setting", "Breakout Timing", "Execution Gate"
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val role: String, // "user", "model", "system"
    val text: String,
    val thinkingProcess: String? = null,
    val modelUsed: String = "gemini-4-argon",
    val timestamp: Long = System.currentTimeMillis(),
    val mediaUri: String? = null,
    val isVoiceTranscription: Boolean = false
)

data class ExchangeAccount(
    val exchangeName: String = "Exness MT5",
    val apiKey: String = "",
    val apiSecret: String = "",
    val isConnected: Boolean = false,
    val isPaperTrading: Boolean = false,
    val paperBalanceUsdt: Double = 0.0,
    val realBalanceUsdt: Double = 0.0,
    val maxDrawdownLimitPercent: Double = 5.0,
    val dailyLossLimitUsdt: Double = 500.0
)

data class Mt5BridgeConfig(
    val isEnabled: Boolean = true,
    val bridgeUrl: String = "",
    val bridgeToken: String = "exness-ai-2026",
    val defaultSymbol: String = "BTCUSDm",
    val lotSize: Double = 0.01,
    val autoExecuteBots: Boolean = false,
    val isConnected: Boolean = false,
    val accountLogin: String = "",
    val brokerServer: String = "Exness-MT5Trial",
    val accountMode: String = "DEMO", // "DEMO" or "REAL"
    val mt5Balance: Double = 0.0,
    val mt5Equity: Double = 0.0,
    val mt5MarginFree: Double = 0.0,
    val lastStatusMessage: String = ""
)

data class Mt5Quote(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val last: Double
)

data class Mt5OrderResult(
    val ticket: Long,
    val symbol: String,
    val action: String,
    val volume: Double,
    val price: Double,
    val message: String
)

data class AiExecutedTradeRecord(
    val id: String,
    val ticketOrOrderId: String,
    val pairSymbol: String,
    val brokerOrExchange: String,
    val strategyName: String,
    val action: String,
    val entryPrice: Double,
    val exitPrice: Double? = null,
    val volumeOrQty: Double,
    val pnlUsdt: Double,
    val status: String,
    val aiReasoning: String,
    val timestamp: Long
)
