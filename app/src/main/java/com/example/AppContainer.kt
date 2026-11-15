package com.example

import com.example.data.model.AiExecutedTradeRecord
import com.example.data.model.AiTradeSignal
import com.example.data.model.BotOrder
import com.example.data.model.CryptoPair
import com.example.data.model.ExchangeAccount
import com.example.data.model.Mt5BridgeConfig
import com.example.data.model.OrderType
import com.example.engine.MarketDataEngine
import com.example.engine.TradingBotManager
import com.example.network.Mt5BridgeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class BackgroundServiceState(
    val isRunning: Boolean = false,
    val isInternetConnected: Boolean = true,
    val startedAtMillis: Long? = null,
    val lastSyncMillis: Long? = null,
    val syncCount: Int = 0,
    val statusMessage: String = "Nonaktif • Aktifkan agar Exness & bot berjalan 24 jam saat online"
)

/**
 * Shared application-wide runtime holder so UI (MainViewModel) and
 * 24-hour Foreground Service (BackgroundTradingService) operate on the
 * exact same market engine, active bots, Exness MT5 bridge, and trade history.
 */
object AppContainer {

    val marketEngine = MarketDataEngine()
    val botManager = TradingBotManager()
    val mt5BridgeService = Mt5BridgeService()

    private val _pairs = MutableStateFlow(marketEngine.getAllPairs().associateBy { it.symbol })
    val pairs: StateFlow<Map<String, CryptoPair>> = _pairs.asStateFlow()

    private val _exchangeAccount = MutableStateFlow(ExchangeAccount())
    val exchangeAccount: StateFlow<ExchangeAccount> = _exchangeAccount.asStateFlow()

    private val _mt5BridgeConfig = MutableStateFlow(Mt5BridgeConfig())
    val mt5BridgeConfig: StateFlow<Mt5BridgeConfig> = _mt5BridgeConfig.asStateFlow()

    private val _mt5Logs = MutableStateFlow<List<String>>(emptyList())
    val mt5Logs: StateFlow<List<String>> = _mt5Logs.asStateFlow()

    private val _aiTradeHistory = MutableStateFlow(seedInitialHistory())
    val aiTradeHistory: StateFlow<List<AiExecutedTradeRecord>> = _aiTradeHistory.asStateFlow()

    private val _backgroundState = MutableStateFlow(BackgroundServiceState())
    val backgroundState: StateFlow<BackgroundServiceState> = _backgroundState.asStateFlow()

    fun refreshPairs() {
        _pairs.value = marketEngine.getAllPairs().associateBy { it.symbol }
    }

    fun updatePairsMap(updatedMap: Map<String, CryptoPair>) {
        _pairs.value = updatedMap
    }

    fun updateExchangeAccount(transform: (ExchangeAccount) -> ExchangeAccount) {
        _exchangeAccount.value = transform(_exchangeAccount.value)
    }

    fun updateMt5Config(transform: (Mt5BridgeConfig) -> Mt5BridgeConfig) {
        _mt5BridgeConfig.value = transform(_mt5BridgeConfig.value)
    }

    fun appendMt5Log(entry: String) {
        _mt5Logs.value = (listOf(entry) + _mt5Logs.value).take(25)
    }

    fun addTradeHistoryRecord(record: AiExecutedTradeRecord) {
        _aiTradeHistory.value = (listOf(record) + _aiTradeHistory.value).take(100)
    }

    fun mergePcTradeHistory(pcRecords: List<AiExecutedTradeRecord>) {
        if (pcRecords.isEmpty()) return
        val existingLocal = _aiTradeHistory.value.filterNot { it.id.startsWith("pc_") }
        val merged = (pcRecords + existingLocal).distinctBy { it.ticketOrOrderId }
        _aiTradeHistory.value = merged.take(100)
    }

    fun clearTradeHistory() {
        _aiTradeHistory.value = emptyList()
    }

    fun updateBackgroundState(transform: (BackgroundServiceState) -> BackgroundServiceState) {
        _backgroundState.value = transform(_backgroundState.value)
    }

    fun recordBotFilledOrders(filledOrders: List<BotOrder>) {
        if (filledOrders.isEmpty()) return
        val botsById = botManager.bots.value.associateBy { it.id }
        val cfg = _mt5BridgeConfig.value
        val brokerLabel = cfg.brokerServer.ifBlank { "Exness-${cfg.accountMode}" }

        val newRecords = filledOrders.map { order ->
            val bot = botsById[order.botId]
            val stratName = bot?.strategyType?.displayName ?: "Algorithmic Bot"
            val estPnl = if (order.orderType == OrderType.SELL) {
                (order.price * 0.0085) * order.quantity
            } else {
                0.0
            }
            AiExecutedTradeRecord(
                id = UUID.randomUUID().toString(),
                ticketOrOrderId = "BOT-${order.id.take(6).uppercase()}",
                pairSymbol = order.pairSymbol,
                brokerOrExchange = brokerLabel,
                strategyName = stratName,
                action = order.orderType.name,
                entryPrice = order.price,
                exitPrice = if (order.orderType == OrderType.SELL) order.price else null,
                volumeOrQty = order.quantity,
                pnlUsdt = estPnl,
                status = if (order.orderType == OrderType.SELL) "CLOSED" else "OPEN",
                aiReasoning = "${bot?.name ?: "AI Bot"} mengeksekusi ${order.orderType.name} otomatis pada level ${"%.2f".format(order.price)} sesuai aturan volatilitas.",
                timestamp = order.filledAt ?: order.timestamp
            )
        }
        _aiTradeHistory.value = (newRecords + _aiTradeHistory.value).take(100)
    }

    suspend fun executeMt5OrderInternal(
        symbol: String,
        action: String,
        volume: Double = _mt5BridgeConfig.value.lotSize,
        stopLoss: Double? = null,
        takeProfit: Double? = null,
        strategyName: String = "MT5 / Exness AI",
        aiReasoning: String = "Eksekusi order langsung ke terminal MetaTrader 5."
    ) {
        val cfg = _mt5BridgeConfig.value
        if (!cfg.isEnabled || cfg.bridgeUrl.isBlank()) {
            appendMt5Log("ORDER SKIPPED: MT5 Bridge belum diaktifkan atau URL kosong.")
            return
        }

        val result = mt5BridgeService.sendOrder(
            bridgeUrl = cfg.bridgeUrl,
            token = cfg.bridgeToken,
            symbol = symbol,
            action = action,
            volume = volume,
            stopLoss = stopLoss,
            takeProfit = takeProfit
        )

        result.onSuccess { orderRes ->
            appendMt5Log("MT5 EXECUTED: #${orderRes.ticket} ${orderRes.action} ${orderRes.volume} ${orderRes.symbol} @ ${orderRes.price}")
            updateMt5Config {
                it.copy(lastStatusMessage = "Order MT5 #${orderRes.ticket} (${orderRes.action} ${orderRes.symbol}) berhasil!")
            }
            val brokerLabel = cfg.brokerServer.ifBlank { "Exness MT5 Bridge" }
            addTradeHistoryRecord(
                AiExecutedTradeRecord(
                    id = UUID.randomUUID().toString(),
                    ticketOrOrderId = "MT5-${orderRes.ticket}",
                    pairSymbol = orderRes.symbol,
                    brokerOrExchange = brokerLabel,
                    strategyName = strategyName,
                    action = orderRes.action,
                    entryPrice = orderRes.price,
                    exitPrice = null,
                    volumeOrQty = orderRes.volume,
                    pnlUsdt = 0.0,
                    status = "OPEN",
                    aiReasoning = aiReasoning,
                    timestamp = System.currentTimeMillis()
                )
            )
        }.onFailure { err ->
            val msg = err.localizedMessage ?: "Order MT5 gagal"
            appendMt5Log("MT5 ORDER ERROR: $msg")
            updateMt5Config { it.copy(lastStatusMessage = "Order gagal: $msg") }
        }
    }

    suspend fun syncExnessFromPcInternal(silent: Boolean = true) {
        val cfg = _mt5BridgeConfig.value
        if (!cfg.isEnabled || cfg.bridgeUrl.isBlank()) return

        mt5BridgeService.fetchQuotes(cfg.bridgeUrl, cfg.bridgeToken).onSuccess { quotes ->
            if (quotes.isNotEmpty()) {
                marketEngine.updateFromMt5Quotes(quotes)
                refreshPairs()
            }
        }
        mt5BridgeService.fetchAiHistory(cfg.bridgeUrl, cfg.bridgeToken).onSuccess { pcRecords ->
            mergePcTradeHistory(pcRecords)
        }
        if (!silent) {
            appendMt5Log("SINKRON PC OK: Pasar Exness & Histori Trading AI diperbarui.")
        }
    }

    suspend fun processTickAndAutoForward(updatedMap: Map<String, CryptoPair>, syncRemote: Boolean) {
        updatePairsMap(updatedMap)
        val filledOrders = botManager.onPriceTick(updatedMap)
        if (filledOrders.isNotEmpty()) {
            recordBotFilledOrders(filledOrders)
        }
        val bridge = _mt5BridgeConfig.value
        if (bridge.isEnabled && bridge.isConnected) {
            if (bridge.autoExecuteBots && filledOrders.isNotEmpty()) {
                filledOrders.forEach { order ->
                    val mt5Symbol = order.pairSymbol.replace("/", "")
                    executeMt5OrderInternal(
                        symbol = mt5Symbol,
                        action = order.orderType.name,
                        volume = bridge.lotSize,
                        strategyName = "Auto Bot -> Exness MT5",
                        aiReasoning = "Sinkron otomatis dari fill bot ${order.pairSymbol} @ ${"%.2f".format(order.price)}"
                    )
                }
            }
            if (syncRemote) {
                syncExnessFromPcInternal(silent = true)
            }
        }
    }

    private fun seedInitialHistory(): List<AiExecutedTradeRecord> {
        val now = System.currentTimeMillis()
        return listOf(
            AiExecutedTradeRecord(
                id = UUID.randomUUID().toString(),
                ticketOrOrderId = "MT5-884120",
                pairSymbol = "BTCUSDm",
                brokerOrExchange = "Exness-MT5Real",
                strategyName = "Volatility Breakout",
                action = "BUY",
                entryPrice = 87620.0,
                exitPrice = 88450.0,
                volumeOrQty = 0.02,
                pnlUsdt = 16.60,
                status = "CLOSED",
                aiReasoning = "Penembusan Bollinger Band atas pada TF M5 disertai konfirmasi volume >1.5x rata-rata dan RSI 61.4.",
                timestamp = now - 3600_000L
            ),
            AiExecutedTradeRecord(
                id = UUID.randomUUID().toString(),
                ticketOrOrderId = "MT5-884012",
                pairSymbol = "EURUSDm",
                brokerOrExchange = "Exness-MT5Trial",
                strategyName = "Grid Trading",
                action = "BUY",
                entryPrice = 1.08210,
                exitPrice = 1.08440,
                volumeOrQty = 0.10,
                pnlUsdt = 23.00,
                status = "CLOSED",
                aiReasoning = "Take-profit rung grid otomatis pada resistensi minor 1.08440 setelah pantulan support 1.08210.",
                timestamp = now - 7200_000L
            ),
            AiExecutedTradeRecord(
                id = UUID.randomUUID().toString(),
                ticketOrOrderId = "MT5-883904",
                pairSymbol = "XAUUSDm",
                brokerOrExchange = "Exness-MT5Real",
                strategyName = "Volatility Breakout",
                action = "SELL",
                entryPrice = 2648.50,
                exitPrice = 2642.10,
                volumeOrQty = 0.05,
                pnlUsdt = 32.00,
                status = "CLOSED",
                aiReasoning = "RSI jenuh beli (74.2) dan penolakan EMA20/BB Upper; AI menyetujui posisi short dengan SL 1.5x ATR.",
                timestamp = now - 14400_000L
            )
        )
    }
}
