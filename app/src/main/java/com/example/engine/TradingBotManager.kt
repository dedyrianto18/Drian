package com.example.engine

import com.example.data.model.BotOrder
import com.example.data.model.BotStatus
import com.example.data.model.BreakoutConfig
import com.example.data.model.CryptoPair
import com.example.data.model.GridConfig
import com.example.data.model.OrderStatus
import com.example.data.model.OrderType
import com.example.data.model.StrategyType
import com.example.data.model.TradingBot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class TradingBotManager {

    private val _bots = MutableStateFlow<List<TradingBot>>(emptyList())
    val bots: StateFlow<List<TradingBot>> = _bots.asStateFlow()

    private val _orderHistory = MutableStateFlow<List<BotOrder>>(emptyList())
    val orderHistory: StateFlow<List<BotOrder>> = _orderHistory.asStateFlow()

    init {
        // Pre-populate with two sample bots (1 Grid, 1 Breakout) in running state
        val initialGridBot = createInitialGridBot()
        val initialBreakoutBot = createInitialBreakoutBot()
        _bots.value = listOf(initialGridBot, initialBreakoutBot)
    }

    private fun createInitialGridBot(): TradingBot {
        val botId = UUID.randomUUID().toString()
        val pair = "SOLUSDm"
        val lower = 195.0
        val upper = 235.0
        val gridCount = 8
        val investment = 1500.0
        val rungs = TechnicalAnalysis.generateGridRungs(lower, upper, gridCount)
        val currentPrice = 214.75

        val orders = mutableListOf<BotOrder>()
        rungs.forEach { rungPrice ->
            if (rungPrice < currentPrice) {
                orders.add(
                    BotOrder(
                        id = UUID.randomUUID().toString(),
                        botId = botId,
                        pairSymbol = pair,
                        orderType = OrderType.BUY,
                        price = rungPrice,
                        quantity = (investment / gridCount) / rungPrice,
                        status = OrderStatus.PENDING,
                        isSimulated = false,
                        timestamp = System.currentTimeMillis() - 7200000
                    )
                )
            } else if (rungPrice > currentPrice) {
                orders.add(
                    BotOrder(
                        id = UUID.randomUUID().toString(),
                        botId = botId,
                        pairSymbol = pair,
                        orderType = OrderType.SELL,
                        price = rungPrice,
                        quantity = (investment / gridCount) / rungPrice,
                        status = OrderStatus.PENDING,
                        isSimulated = false,
                        timestamp = System.currentTimeMillis() - 7200000
                    )
                )
            }
        }

        return TradingBot(
            id = botId,
            name = "SOL Alpha Grid 8x",
            pairSymbol = pair,
            strategyType = StrategyType.GRID,
            status = BotStatus.RUNNING,
            isPaperTrading = false,
            investmentAmountUsdt = investment,
            totalProfitUsdt = 48.75,
            profitPercentage = 3.25,
            executedTradesCount = 14,
            createdAt = System.currentTimeMillis() - 86400000,
            gridConfig = GridConfig(
                upperBound = upper,
                lowerBound = lower,
                gridCount = gridCount,
                investmentUsdt = investment,
                profitPerGridPercent = 0.85
            ),
            openOrders = orders
        )
    }

    private fun createInitialBreakoutBot(): TradingBot {
        val botId = UUID.randomUUID().toString()
        val pair = "BTCUSDm"
        val currentPrice = 88450.0
        val investment = 2500.0

        val order = BotOrder(
            id = UUID.randomUUID().toString(),
            botId = botId,
            pairSymbol = pair,
            orderType = OrderType.BUY,
            price = 87800.0,
            quantity = investment / 87800.0,
            status = OrderStatus.FILLED,
            isSimulated = false,
            timestamp = System.currentTimeMillis() - 14400000,
            filledAt = System.currentTimeMillis() - 14200000
        )

        return TradingBot(
            id = botId,
            name = "BTC Volatility Momentum",
            pairSymbol = pair,
            strategyType = StrategyType.VOLATILITY_BREAKOUT,
            status = BotStatus.RUNNING,
            isPaperTrading = false,
            investmentAmountUsdt = investment,
            totalProfitUsdt = 124.50,
            profitPercentage = 4.98,
            executedTradesCount = 4,
            createdAt = System.currentTimeMillis() - 172800000,
            breakoutConfig = BreakoutConfig(
                squeezeThreshold = 1.8,
                breakoutLevelHigh = 88000.0,
                breakoutLevelLow = 86200.0,
                stopLossPercent = 1.5,
                takeProfitPercent = 4.5,
                trailingStop = true,
                investmentUsdt = investment
            ),
            openOrders = listOf(order)
        )
    }

    fun startNewBot(bot: TradingBot) {
        _bots.value = _bots.value + bot
    }

    fun toggleBotStatus(botId: String) {
        _bots.value = _bots.value.map { bot ->
            if (bot.id == botId) {
                val nextStatus = when (bot.status) {
                    BotStatus.RUNNING -> BotStatus.PAUSED
                    BotStatus.PAUSED -> BotStatus.RUNNING
                    BotStatus.STOPPED -> BotStatus.RUNNING
                }
                bot.copy(status = nextStatus)
            } else {
                bot
            }
        }
    }

    fun stopBot(botId: String) {
        _bots.value = _bots.value.map { bot ->
            if (bot.id == botId) {
                bot.copy(
                    status = BotStatus.STOPPED,
                    openOrders = bot.openOrders.map { it.copy(status = OrderStatus.CANCELLED) }
                )
            } else {
                bot
            }
        }
    }

    fun deleteBot(botId: String) {
        _bots.value = _bots.value.filter { it.id != botId }
    }

    /**
     * Evaluates live prices against active bot rules, executes fills, and rebalances grid rungs
     */
    fun onPriceTick(prices: Map<String, CryptoPair>): List<BotOrder> {
        val currentBots = _bots.value
        var updated = false
        val tickFilledOrders = mutableListOf<BotOrder>()

        val newBotList = currentBots.map { bot ->
            if (bot.status != BotStatus.RUNNING) return@map bot
            val pair = prices[bot.pairSymbol] ?: return@map bot
            val currentPrice = pair.lastPrice

            val (updatedBot, filled) = when (bot.strategyType) {
                StrategyType.GRID -> processGridBotTick(bot, currentPrice)
                StrategyType.VOLATILITY_BREAKOUT -> processBreakoutBotTick(bot, currentPrice, pair)
            }
            updated = true
            tickFilledOrders.addAll(filled)
            updatedBot
        }

        if (updated) {
            _bots.value = newBotList
        }
        return tickFilledOrders
    }

    private fun processGridBotTick(bot: TradingBot, currentPrice: Double): Pair<TradingBot, List<BotOrder>> {
        val grid = bot.gridConfig ?: return bot to emptyList()
        val updatedOrders = bot.openOrders.toMutableList()
        val newOrders = mutableListOf<BotOrder>()
        var profitGained = 0.0
        var newTrades = 0

        val filledOrders = mutableListOf<BotOrder>()
        val rungs = TechnicalAnalysis.generateGridRungs(grid.lowerBound, grid.upperBound, grid.gridCount)
        val iterator = updatedOrders.iterator()

        while (iterator.hasNext()) {
            val order = iterator.next()
            if (order.status != OrderStatus.PENDING) continue

            // Buy order check
            if (order.orderType == OrderType.BUY && currentPrice <= order.price) {
                val filled = order.copy(status = OrderStatus.FILLED, filledAt = System.currentTimeMillis())
                filledOrders.add(filled)
                iterator.remove()
                newTrades++

                // Automatically generate paired sell order at next upper rung
                val nextRung = rungs.firstOrNull { it > order.price } ?: (order.price * (1 + (grid.profitPerGridPercent / 100)))
                val profitPerFill = (nextRung - order.price) * order.quantity

                profitGained += (profitPerFill * 0.5) // Realized partial profit

                newOrders.add(
                    BotOrder(
                        id = UUID.randomUUID().toString(),
                        botId = bot.id,
                        pairSymbol = bot.pairSymbol,
                        orderType = OrderType.SELL,
                        price = nextRung,
                        quantity = order.quantity,
                        status = OrderStatus.PENDING,
                        isSimulated = bot.isPaperTrading,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else if (order.orderType == OrderType.SELL && currentPrice >= order.price) {
                val filled = order.copy(status = OrderStatus.FILLED, filledAt = System.currentTimeMillis())
                filledOrders.add(filled)
                iterator.remove()
                newTrades++

                val nextLowerRung = rungs.lastOrNull { it < order.price } ?: (order.price * (1 - (grid.profitPerGridPercent / 100)))
                val profitPerFill = (order.price - nextLowerRung) * order.quantity

                profitGained += profitPerFill

                newOrders.add(
                    BotOrder(
                        id = UUID.randomUUID().toString(),
                        botId = bot.id,
                        pairSymbol = bot.pairSymbol,
                        orderType = OrderType.BUY,
                        price = nextLowerRung,
                        quantity = order.quantity,
                        status = OrderStatus.PENDING,
                        isSimulated = bot.isPaperTrading,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        if (newOrders.isNotEmpty()) {
            updatedOrders.addAll(newOrders)
        }

        if (filledOrders.isNotEmpty()) {
            _orderHistory.value = filledOrders + _orderHistory.value.take(100)
        }

        val totalProfit = bot.totalProfitUsdt + profitGained
        val profitPercent = if (bot.investmentAmountUsdt > 0) (totalProfit / bot.investmentAmountUsdt) * 100 else 0.0

        return bot.copy(
            openOrders = updatedOrders,
            totalProfitUsdt = totalProfit,
            profitPercentage = profitPercent,
            executedTradesCount = bot.executedTradesCount + newTrades
        ) to filledOrders
    }

    private fun processBreakoutBotTick(bot: TradingBot, currentPrice: Double, pair: CryptoPair): Pair<TradingBot, List<BotOrder>> {
        val config = bot.breakoutConfig ?: return bot to emptyList()
        var totalProfit = bot.totalProfitUsdt
        var tradesCount = bot.executedTradesCount
        val openOrders = bot.openOrders.toMutableList()
        val filledOrders = mutableListOf<BotOrder>()

        // If no open active position, check for breakout trigger
        if (openOrders.none { it.status == OrderStatus.FILLED }) {
            if (currentPrice >= config.breakoutLevelHigh) {
                // Trigger Bullish Breakout Entry!
                val entryOrder = BotOrder(
                    id = UUID.randomUUID().toString(),
                    botId = bot.id,
                    pairSymbol = bot.pairSymbol,
                    orderType = OrderType.BUY,
                    price = currentPrice,
                    quantity = config.investmentUsdt / currentPrice,
                    status = OrderStatus.FILLED,
                    isSimulated = bot.isPaperTrading,
                    timestamp = System.currentTimeMillis(),
                    filledAt = System.currentTimeMillis()
                )
                openOrders.add(entryOrder)
                filledOrders.add(entryOrder)
                tradesCount++
                _orderHistory.value = listOf(entryOrder) + _orderHistory.value
            }
        } else {
            // Active position check for Take-Profit or Stop-Loss
            val activeBuy = openOrders.firstOrNull { it.status == OrderStatus.FILLED && it.orderType == OrderType.BUY }
            if (activeBuy != null) {
                val pnlPercent = ((currentPrice - activeBuy.price) / activeBuy.price) * 100
                if (pnlPercent >= config.takeProfitPercent || pnlPercent <= -config.stopLossPercent) {
                    // Close position
                    val closeOrder = BotOrder(
                        id = UUID.randomUUID().toString(),
                        botId = bot.id,
                        pairSymbol = bot.pairSymbol,
                        orderType = OrderType.SELL,
                        price = currentPrice,
                        quantity = activeBuy.quantity,
                        status = OrderStatus.FILLED,
                        isSimulated = bot.isPaperTrading,
                        timestamp = System.currentTimeMillis(),
                        filledAt = System.currentTimeMillis()
                    )
                    openOrders.remove(activeBuy)
                    filledOrders.add(closeOrder)
                    tradesCount++
                    val realizedUsdt = (currentPrice - activeBuy.price) * activeBuy.quantity
                    totalProfit += realizedUsdt
                    _orderHistory.value = listOf(closeOrder) + _orderHistory.value
                }
            }
        }

        val profitPercent = if (bot.investmentAmountUsdt > 0) (totalProfit / bot.investmentAmountUsdt) * 100 else 0.0

        return bot.copy(
            openOrders = openOrders,
            totalProfitUsdt = totalProfit,
            profitPercentage = profitPercent,
            executedTradesCount = tradesCount
        ) to filledOrders
    }
}
