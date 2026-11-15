package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.BackgroundServiceState
import com.example.ai.AiAnalysisResult
import com.example.ai.CryptoAiService
import com.example.data.model.AiExecutedTradeRecord
import com.example.data.model.AiTradeSignal
import com.example.data.model.ChatMessage
import com.example.data.model.CryptoPair
import com.example.data.model.ExchangeAccount
import com.example.data.model.Mt5BridgeConfig
import com.example.data.model.StrategyKnowledgeRule
import com.example.engine.TechnicalAnalysis
import com.example.service.BackgroundTradingService
import com.google.firebase.ai.type.AspectRatio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "MainViewModel"

enum class NavigationScreen(val title: String) {
    DASHBOARD("Pasar Exness"),
    BOTS("Bot & Histori"),
    CREATE_BOT("New Bot"),
    BOT_DETAIL("Bot Telemetry"),
    AI_COPILOT("Gemini Chat"),
    KNOWLEDGE_BASE("Train AI"),
    EXCHANGE_SETTINGS("Akun Exness")
}

sealed interface AiOperationState {
    data object Idle : AiOperationState
    data class Loading(val stage: String) : AiOperationState
    data class Success(val result: AiAnalysisResult) : AiOperationState
    data class ImageSuccess(val bitmap: Bitmap) : AiOperationState
    data class Error(val message: String) : AiOperationState
}

class MainViewModel : ViewModel() {

    private val marketEngine = AppContainer.marketEngine
    val botManager = AppContainer.botManager
    val aiService = CryptoAiService()
    val mt5BridgeService = AppContainer.mt5BridgeService

    // Navigation
    private val _currentScreen = MutableStateFlow(NavigationScreen.DASHBOARD)
    val currentScreen: StateFlow<NavigationScreen> = _currentScreen.asStateFlow()

    private val _selectedBotId = MutableStateFlow<String?>(null)
    val selectedBotId: StateFlow<String?> = _selectedBotId.asStateFlow()

    // Shared Market, Exchange, MT5, Trade History, and 24h Background Service State
    val pairs: StateFlow<Map<String, CryptoPair>> = AppContainer.pairs

    private val _selectedPairSymbol = MutableStateFlow("BTCUSDm")
    val selectedPairSymbol: StateFlow<String> = _selectedPairSymbol.asStateFlow()

    val exchangeAccount: StateFlow<ExchangeAccount> = AppContainer.exchangeAccount
    val mt5BridgeConfig: StateFlow<Mt5BridgeConfig> = AppContainer.mt5BridgeConfig
    val mt5Logs: StateFlow<List<String>> = AppContainer.mt5Logs
    val aiTradeHistory: StateFlow<List<AiExecutedTradeRecord>> = AppContainer.aiTradeHistory
    val backgroundState: StateFlow<BackgroundServiceState> = AppContainer.backgroundState

    private val _mt5Testing = MutableStateFlow(false)
    val mt5Testing: StateFlow<Boolean> = _mt5Testing.asStateFlow()

    // AI Operation & General Gemini Chat State
    private val _aiState = MutableStateFlow<AiOperationState>(AiOperationState.Idle)
    val aiState: StateFlow<AiOperationState> = _aiState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Trainable / Adaptive Strategy Rules
    private val _knowledgeRules = MutableStateFlow<List<StrategyKnowledgeRule>>(emptyList())
    val knowledgeRules: StateFlow<List<StrategyKnowledgeRule>> = _knowledgeRules.asStateFlow()

    // Live AI Signals
    private val _liveSignals = MutableStateFlow<List<AiTradeSignal>>(emptyList())
    val liveSignals: StateFlow<List<AiTradeSignal>> = _liveSignals.asStateFlow()

    init {
        AppContainer.refreshPairs()

        // Initial knowledge rules
        _knowledgeRules.value = listOf(
            StrategyKnowledgeRule(
                id = UUID.randomUUID().toString(),
                title = "Aturan Batas Volatilitas Ekstrem",
                instructionPrompt = "Jangan mengeksekusi order buy jika RSI 14-periode di atas 78, tunggu konsolidasi atau koreksi minimum 1.5%.",
                ruleCategory = "Risk Management"
            ),
            StrategyKnowledgeRule(
                id = UUID.randomUUID().toString(),
                title = "Konfirmasi Breakout Volume",
                instructionPrompt = "Breakout hanya valid jika volume lilin 5-menit menembus 1.5x rata-rata 20 lilin sebelumnya.",
                ruleCategory = "Breakout Timing"
            ),
            StrategyKnowledgeRule(
                id = UUID.randomUUID().toString(),
                title = "Dynamic Grid Spread",
                instructionPrompt = "Saat ATR melebar lebih dari 2% harga aset, renggangkan jarak antar grid rungs sebesar 25% untuk meminimalkan paparan drawdown.",
                ruleCategory = "Grid Setting"
            )
        )

        // Seed initial welcoming message in General Gemini Chat
        _chatMessages.value = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "model",
                text = "Halo! Ini adalah Gemini Chat Umum. Anda bebas bertanya topik apa saja (coding, ide tulisan, terjemahan, pengetahuan umum, atau tanya pasar Exness). " +
                       "Anda juga bisa mengetuk tombol 'Buka App Gemini' atau 'Kirim ke App Gemini' untuk langsung tersambung dengan aplikasi Google Gemini yang terinstal di HP Android Anda.",
                modelUsed = "gemini-3.8-flash"
            )
        )

        // Foreground tick loop when 24h BackgroundService is not already driving the loop
        viewModelScope.launch {
            var tickCount = 0
            marketEngine.streamLiveTicks().collect { updatedMap ->
                if (!AppContainer.backgroundState.value.isRunning) {
                    AppContainer.processTickAndAutoForward(updatedMap, syncRemote = (tickCount % 3 == 0))
                    tickCount++
                }
            }
        }

        // Generate periodic AI signal
        generateInitialSignals()
    }

    fun navigateTo(screen: NavigationScreen) {
        _currentScreen.value = screen
    }

    fun selectPair(symbol: String) {
        _selectedPairSymbol.value = symbol
    }

    fun selectBot(botId: String) {
        _selectedBotId.value = botId
        _currentScreen.value = NavigationScreen.BOT_DETAIL
    }

    fun addCustomPair(symbol: String, price: Double) {
        marketEngine.addCustomPair(symbol, price)
        AppContainer.refreshPairs()
        _selectedPairSymbol.value = symbol.trim()
    }

    fun startBackground24h(context: Context) {
        val intent = Intent(context, BackgroundTradingService::class.java).apply {
            action = BackgroundTradingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopBackground24h(context: Context) {
        val intent = Intent(context, BackgroundTradingService::class.java).apply {
            action = BackgroundTradingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun updateExchangeSettings(
        apiKey: String,
        apiSecret: String,
        isPaperTrading: Boolean,
        exchangeName: String
    ) {
        AppContainer.updateExchangeAccount {
            it.copy(
                apiKey = apiKey,
                apiSecret = apiSecret,
                isPaperTrading = isPaperTrading,
                exchangeName = exchangeName,
                isConnected = apiKey.isNotBlank() || isPaperTrading
            )
        }
    }

    fun updateMt5BridgeSettings(
        isEnabled: Boolean,
        bridgeUrl: String,
        bridgeToken: String,
        defaultSymbol: String,
        lotSize: Double,
        autoExecuteBots: Boolean,
        accountMode: String = mt5BridgeConfig.value.accountMode,
        brokerServer: String = mt5BridgeConfig.value.brokerServer,
        accountLogin: String = mt5BridgeConfig.value.accountLogin
    ) {
        AppContainer.updateMt5Config {
            it.copy(
                isEnabled = isEnabled,
                bridgeUrl = bridgeUrl.trim(),
                bridgeToken = bridgeToken.trim(),
                defaultSymbol = defaultSymbol.trim().ifBlank { "BTCUSDm" },
                lotSize = lotSize.coerceAtLeast(0.01),
                autoExecuteBots = autoExecuteBots,
                accountMode = accountMode,
                brokerServer = brokerServer.trim(),
                accountLogin = accountLogin.trim()
            )
        }
    }

    fun loginExnessAccount(
        bridgeUrl: String,
        bridgeToken: String,
        login: String,
        password: String,
        server: String,
        accountMode: String,
        defaultSymbol: String,
        lotSize: Double,
        autoExecuteBots: Boolean
    ) {
        updateMt5BridgeSettings(
            isEnabled = true,
            bridgeUrl = bridgeUrl,
            bridgeToken = bridgeToken,
            defaultSymbol = defaultSymbol,
            lotSize = lotSize,
            autoExecuteBots = autoExecuteBots,
            accountMode = accountMode,
            brokerServer = server,
            accountLogin = login
        )

        val cfg = mt5BridgeConfig.value
        viewModelScope.launch {
            _mt5Testing.value = true
            val result = if (login.isNotBlank() && password.isNotBlank()) {
                mt5BridgeService.loginToExness(
                    bridgeUrl = cfg.bridgeUrl,
                    token = cfg.bridgeToken,
                    login = login,
                    password = password,
                    server = server
                )
            } else {
                mt5BridgeService.checkHealth(cfg.bridgeUrl, cfg.bridgeToken)
            }
            _mt5Testing.value = false

            result.onSuccess { info ->
                AppContainer.updateMt5Config {
                    it.copy(
                        isConnected = true,
                        accountLogin = info.login,
                        brokerServer = info.server,
                        accountMode = info.accountMode,
                        mt5Balance = info.balance,
                        mt5Equity = info.equity,
                        mt5MarginFree = info.marginFree,
                        lastStatusMessage = "Terhubung Exness ${info.accountMode} #${info.login} (${info.server}) • ${info.openPositions} posisi aktif"
                    )
                }
                AppContainer.updateExchangeAccount {
                    it.copy(
                        exchangeName = "Exness ${info.accountMode}",
                        isConnected = true,
                        isPaperTrading = false,
                        realBalanceUsdt = info.balance
                    )
                }
                AppContainer.appendMt5Log("LOGIN EXNESS ${info.accountMode} OK: #${info.login} @ ${info.server} | Balance: ${info.balance}")
                syncExnessDataFromPc(silent = false)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Gagal login ke Exness via PC Bridge"
                AppContainer.updateMt5Config {
                    it.copy(isConnected = false, lastStatusMessage = "Gagal: $msg")
                }
                AppContainer.appendMt5Log("LOGIN ERROR: $msg")
            }
        }
    }

    fun syncExnessDataFromPc(silent: Boolean = false) {
        viewModelScope.launch {
            AppContainer.syncExnessFromPcInternal(silent = silent)
        }
    }

    fun testMt5BridgeConnection() {
        val cfg = mt5BridgeConfig.value
        viewModelScope.launch {
            _mt5Testing.value = true
            val result = mt5BridgeService.checkHealth(cfg.bridgeUrl, cfg.bridgeToken)
            _mt5Testing.value = false
            result.onSuccess { info ->
                AppContainer.updateMt5Config {
                    it.copy(
                        isConnected = true,
                        accountLogin = info.login,
                        brokerServer = info.server,
                        accountMode = info.accountMode,
                        mt5Balance = info.balance,
                        mt5Equity = info.equity,
                        mt5MarginFree = info.marginFree,
                        lastStatusMessage = "Terhubung Exness ${info.accountMode} #${info.login} (${info.server}) • ${info.openPositions} posisi aktif"
                    )
                }
                AppContainer.updateExchangeAccount {
                    it.copy(
                        exchangeName = "Exness ${info.accountMode}",
                        isConnected = true,
                        isPaperTrading = false,
                        realBalanceUsdt = info.balance
                    )
                }
                AppContainer.appendMt5Log("HEALTH OK: #${info.login} | Balance: ${info.balance} | Equity: ${info.equity}")
                syncExnessDataFromPc(silent = false)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Gagal terhubung ke PC Bridge"
                AppContainer.updateMt5Config {
                    it.copy(isConnected = false, lastStatusMessage = "Gagal: $msg")
                }
                AppContainer.appendMt5Log("CONNECT ERROR: $msg")
            }
        }
    }

    fun executeMt5Order(
        symbol: String,
        action: String,
        volume: Double = mt5BridgeConfig.value.lotSize,
        stopLoss: Double? = null,
        takeProfit: Double? = null,
        strategyName: String = "MT5 / Exness AI",
        aiReasoning: String = "Eksekusi order langsung ke terminal MetaTrader 5."
    ) {
        viewModelScope.launch {
            AppContainer.executeMt5OrderInternal(
                symbol = symbol,
                action = action,
                volume = volume,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                strategyName = strategyName,
                aiReasoning = aiReasoning
            )
        }
    }

    fun sendSignalToMt5(signal: AiTradeSignal) {
        val cfg = mt5BridgeConfig.value
        val mt5Symbol = signal.pairSymbol.replace("/", "").replace("USDT", "USD")
        val action = if (signal.action.contains("SELL") || signal.action.contains("SHORT")) "SELL" else "BUY"
        executeMt5Order(
            symbol = mt5Symbol,
            action = action,
            volume = cfg.lotSize,
            stopLoss = signal.stopLoss,
            takeProfit = signal.targetPrice,
            strategyName = signal.strategyName,
            aiReasoning = signal.reasoning
        )
    }

    fun clearAiTradeHistory() {
        AppContainer.clearTradeHistory()
    }

    fun closeAllMt5Positions() {
        val cfg = mt5BridgeConfig.value
        if (!cfg.isEnabled || cfg.bridgeUrl.isBlank()) return
        viewModelScope.launch {
            val result = mt5BridgeService.closeAllPositions(cfg.bridgeUrl, cfg.bridgeToken)
            result.onSuccess { count ->
                AppContainer.appendMt5Log("KILL SWITCH MT5: $count posisi ditutup.")
                AppContainer.updateMt5Config {
                    it.copy(lastStatusMessage = "Kill Switch MT5 menutup $count posisi.")
                }
            }.onFailure { err ->
                AppContainer.appendMt5Log("KILL SWITCH MT5 ERROR: ${err.localizedMessage}")
            }
        }
    }

    fun addKnowledgeRule(title: String, prompt: String, category: String) {
        val newRule = StrategyKnowledgeRule(
            id = UUID.randomUUID().toString(),
            title = title,
            instructionPrompt = prompt,
            ruleCategory = category
        )
        _knowledgeRules.value = _knowledgeRules.value + newRule
    }

    fun toggleKnowledgeRule(id: String) {
        _knowledgeRules.value = _knowledgeRules.value.map {
            if (it.id == id) it.copy(isActive = !it.isActive) else it
        }
    }

    fun deleteKnowledgeRule(id: String) {
        _knowledgeRules.value = _knowledgeRules.value.filter { it.id != id }
    }

    /**
     * Conduct deep technical analysis with Gemini 4 Argon High Thinking
     */
    fun runHighThinkingAnalysis(pairSymbol: String) {
        viewModelScope.launch {
            _aiState.value = AiOperationState.Loading("Melakukan pemikiran teknikal mendalam dengan Gemini 4 Argon...")
            try {
                val pair = pairs.value[pairSymbol] ?: marketEngine.getPair(pairSymbol)
                val ind = pair.indicators ?: TechnicalAnalysis.calculateIndicators(pair.candles)
                val activeRules = _knowledgeRules.value.filter { it.isActive }.map { "${it.title}: ${it.instructionPrompt}" }

                val result = aiService.analyzeMarketWithHighThinking(
                    pairSymbol = pair.symbol,
                    currentPrice = pair.lastPrice,
                    rsi = ind.rsi,
                    bbUpper = ind.bollingerUpper,
                    bbMiddle = ind.bollingerMiddle,
                    bbLower = ind.bollingerLower,
                    macd = ind.macd,
                    learnedRules = activeRules
                )

                result.onSuccess { data ->
                    _aiState.value = AiOperationState.Success(data)
                    // Also append to copilot chat
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "model",
                        text = "Analisis Berpikir Tingkat Tinggi untuk ${pair.symbol}:\n\n${data.mainAnalysis}",
                        thinkingProcess = data.thinkingProcess,
                        modelUsed = "gemini-4-argon"
                    )
                }.onFailure { err ->
                    _aiState.value = AiOperationState.Error(err.localizedMessage ?: "Analisis gagal")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Analysis error", e)
                _aiState.value = AiOperationState.Error(e.localizedMessage ?: "Gagal")
            }
        }
    }

    /**
     * Send chat prompt to General Gemini Chat
     */
    fun sendChatMessage(promptText: String, modelType: String = "gemini-3.8-flash") {
        if (promptText.isBlank()) return
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            text = promptText,
            modelUsed = modelType
        )
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            _aiState.value = AiOperationState.Loading("Gemini sedang memproses jawaban...")
            val result = aiService.sendChatMessage(promptText, modelType, enableThinking = true)

            result.onSuccess { data ->
                _aiState.value = AiOperationState.Idle
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "model",
                    text = data.mainAnalysis,
                    thinkingProcess = data.thinkingProcess,
                    modelUsed = modelType
                )
            }.onFailure { err ->
                _aiState.value = AiOperationState.Error(err.localizedMessage ?: "Gagal membalas")
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "system",
                    text = "Gagal memproses pesan: ${err.localizedMessage}",
                    modelUsed = modelType
                )
            }
        }
    }

    /**
     * Transcribe audio using gemini-3.5-transcribe and process as prompt
     */
    fun processVoiceCommand(audioBytes: ByteArray) {
        viewModelScope.launch {
            _aiState.value = AiOperationState.Loading("Mentranskripsikan suara dengan gemini-3.5-transcribe...")
            val result = aiService.transcribeAudio(audioBytes)

            result.onSuccess { transcript ->
                _aiState.value = AiOperationState.Idle
                if (transcript.isNotBlank()) {
                    sendChatMessage(transcript)
                }
            }.onFailure { err ->
                _aiState.value = AiOperationState.Error("Transkripsi suara gagal: ${err.localizedMessage}")
            }
        }
    }

    /**
     * Analyze uploaded image using gemini-4-argon
     */
    fun processChartImage(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _aiState.value = AiOperationState.Loading("Gemini sedang menganalisis foto/gambar...")
            val result = aiService.analyzeChartImage(bitmap, prompt)

            result.onSuccess { data ->
                _aiState.value = AiOperationState.Success(data)
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "model",
                    text = "Analisis Foto Gemini:\n\n${data.mainAnalysis}",
                    thinkingProcess = data.thinkingProcess,
                    modelUsed = "gemini-4-argon"
                )
            }.onFailure { err ->
                _aiState.value = AiOperationState.Error("Analisis gambar gagal: ${err.localizedMessage}")
            }
        }
    }

    /**
     * Analyze uploaded video using gemini-4-argon
     */
    fun processVideo(videoBytes: ByteArray, prompt: String) {
        viewModelScope.launch {
            _aiState.value = AiOperationState.Loading("Gemini sedang menganalisis video...")
            val result = aiService.analyzeVideo(videoBytes, userPrompt = prompt)

            result.onSuccess { data ->
                _aiState.value = AiOperationState.Success(data)
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "model",
                    text = "Analisis Video Gemini:\n\n${data.mainAnalysis}",
                    thinkingProcess = data.thinkingProcess,
                    modelUsed = "gemini-4-argon"
                )
            }.onFailure { err ->
                _aiState.value = AiOperationState.Error("Analisis video gagal: ${err.localizedMessage}")
            }
        }
    }

    /**
     * Generate visual image with aspect ratio selection
     */
    fun generateInfographicCard(prompt: String, aspectRatio: AspectRatio) {
        viewModelScope.launch {
            _aiState.value = AiOperationState.Loading("Membuat gambar dengan Gemini...")
            val result = aiService.generateStrategyInfographic(prompt, aspectRatio)

            result.onSuccess { bitmap ->
                _aiState.value = AiOperationState.ImageSuccess(bitmap)
            }.onFailure { err ->
                _aiState.value = AiOperationState.Error("Pembuatan gambar gagal: ${err.localizedMessage}")
            }
        }
    }

    fun dismissAiState() {
        _aiState.value = AiOperationState.Idle
    }

    private fun generateInitialSignals() {
        _liveSignals.value = listOf(
            AiTradeSignal(
                id = UUID.randomUUID().toString(),
                pairSymbol = "XAUUSDm",
                action = "BREAKOUT_LONG",
                confidenceScore = 91,
                strategyName = "Volatility Corridor Grid",
                entryPrice = 2648.40,
                stopLoss = 2635.0,
                targetPrice = 2672.0,
                reasoning = "Emas (XAUUSDm) berkonsolidasi di atas EMA20 dengan RSI 56.4 netral-bullish. Cocok untuk eksekusi Exness MT5.",
                thinkingSummary = "1. Menganalisis range harian [2621.5 - 2662.0]. 2. Support kuat di 2635.0. 3. Momentum mendukung breakout bertahap.",
                timestamp = System.currentTimeMillis() - 600000
            ),
            AiTradeSignal(
                id = UUID.randomUUID().toString(),
                pairSymbol = "BTCUSDm",
                action = "BREAKOUT_LONG",
                confidenceScore = 88,
                strategyName = "ATR Momentum Breakout",
                entryPrice = 88450.0,
                stopLoss = 86800.0,
                targetPrice = 92000.0,
                reasoning = "Harga mendekati batas atas konsolidasi $88,600 dengan peningkatan momentum bull pada pasar kripto Exness.",
                thinkingSummary = "1. Candle menunjukkan Higher Lows berturut-turut. 2. MACD golden cross. 3. Stop loss di bawah swing low.",
                timestamp = System.currentTimeMillis() - 1800000
            )
        )
    }
}
