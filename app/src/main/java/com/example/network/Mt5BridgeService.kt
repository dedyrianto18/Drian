package com.example.network

import android.util.Log
import com.example.data.model.AiExecutedTradeRecord
import com.example.data.model.Mt5OrderResult
import com.example.data.model.Mt5Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "Mt5BridgeService"

data class Mt5AccountInfo(
    val login: String,
    val server: String,
    val accountMode: String = "DEMO",
    val balance: Double,
    val equity: Double,
    val marginFree: Double = 0.0,
    val openPositions: Int
)

class Mt5BridgeService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun normalizeBaseUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return null
        }
        return trimmed
    }

    suspend fun checkHealth(bridgeUrl: String, token: String): Result<Mt5AccountInfo> =
        withContext(Dispatchers.IO) {
            val baseUrl = normalizeBaseUrl(bridgeUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("URL Bridge PC harus diawali http:// atau https://")
                )

            try {
                val requestBuilder = Request.Builder()
                    .url("$baseUrl/health")
                    .get()

                if (token.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("HTTP ${response.code}: ${bodyStr.ifBlank { response.message }}")
                        )
                    }

                    val json = JSONObject(bodyStr)
                    val serverStr = json.optString("server", "Exness-MT5Trial")
                    val mode = json.optString(
                        "account_mode",
                        if (serverStr.contains("Real", ignoreCase = true)) "REAL" else "DEMO"
                    )
                    val info = Mt5AccountInfo(
                        login = json.optString("login", "Unknown"),
                        server = serverStr,
                        accountMode = mode,
                        balance = json.optDouble("balance", 0.0),
                        equity = json.optDouble("equity", 0.0),
                        marginFree = json.optDouble("margin_free", 0.0),
                        openPositions = json.optInt("open_positions", 0)
                    )
                    Result.success(info)
                }
            } catch (e: Exception) {
                Log.e(TAG, "MT5 health check failed", e)
                Result.failure(e)
            }
        }

    suspend fun loginToExness(
        bridgeUrl: String,
        token: String,
        login: String,
        password: String,
        server: String
    ): Result<Mt5AccountInfo> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(bridgeUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Masukkan URL PC Bridge (contoh: http://192.168.1.10:8000)")
            )

        try {
            val payload = JSONObject().apply {
                put("login", login.trim())
                put("password", password)
                put("server", server.trim())
            }

            val requestBuilder = Request.Builder()
                .url("$baseUrl/connect")
                .post(payload.toString().toRequestBody(jsonMediaType))

            if (token.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Login Exness gagal (HTTP ${response.code}): ${bodyStr.ifBlank { response.message }}")
                    )
                }

                val json = JSONObject(bodyStr)
                val serverStr = json.optString("server", server)
                val mode = json.optString(
                    "account_mode",
                    if (serverStr.contains("Real", ignoreCase = true)) "REAL" else "DEMO"
                )
                Result.success(
                    Mt5AccountInfo(
                        login = json.optString("login", login),
                        server = serverStr,
                        accountMode = mode,
                        balance = json.optDouble("balance", 0.0),
                        equity = json.optDouble("equity", 0.0),
                        marginFree = json.optDouble("margin_free", 0.0),
                        openPositions = json.optInt("open_positions", 0)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exness login failed", e)
            Result.failure(e)
        }
    }

    suspend fun fetchQuotes(bridgeUrl: String, token: String): Result<List<Mt5Quote>> =
        withContext(Dispatchers.IO) {
            val baseUrl = normalizeBaseUrl(bridgeUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("URL Bridge invalid"))

            try {
                val requestBuilder = Request.Builder()
                    .url("$baseUrl/quotes")
                    .get()
                if (token.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                    }
                    val json = JSONObject(bodyStr)
                    val arr = json.optJSONArray("quotes") ?: return@withContext Result.success(emptyList())
                    val list = mutableListOf<Mt5Quote>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            Mt5Quote(
                                symbol = obj.optString("symbol"),
                                bid = obj.optDouble("bid", 0.0),
                                ask = obj.optDouble("ask", 0.0),
                                last = obj.optDouble("last", obj.optDouble("bid", 0.0))
                            )
                        )
                    }
                    Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchAiHistory(bridgeUrl: String, token: String): Result<List<AiExecutedTradeRecord>> =
        withContext(Dispatchers.IO) {
            val baseUrl = normalizeBaseUrl(bridgeUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("URL Bridge invalid"))

            try {
                val requestBuilder = Request.Builder()
                    .url("$baseUrl/history")
                    .get()
                if (token.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                    }
                    val json = JSONObject(bodyStr)
                    val arr = json.optJSONArray("history") ?: return@withContext Result.success(emptyList())
                    val list = mutableListOf<AiExecutedTradeRecord>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val exitVal = if (obj.isNull("exit_price")) null else obj.optDouble("exit_price")
                        list.add(
                            AiExecutedTradeRecord(
                                id = "pc_${obj.optInt("id", i)}",
                                ticketOrOrderId = "MT5-${obj.optLong("ticket", 0L)}",
                                pairSymbol = obj.optString("symbol", "BTCUSDm"),
                                brokerOrExchange = obj.optString("broker_server", "Exness-MT5"),
                                strategyName = obj.optString("strategy", "MT5 AI"),
                                action = obj.optString("action", "BUY"),
                                entryPrice = obj.optDouble("entry_price", 0.0),
                                exitPrice = exitVal,
                                volumeOrQty = obj.optDouble("volume", 0.01),
                                pnlUsdt = obj.optDouble("pnl", 0.0),
                                status = obj.optString("status", "OPEN"),
                                aiReasoning = obj.optString("ai_reason", ""),
                                timestamp = System.currentTimeMillis() - (i * 60_000L)
                            )
                        )
                    }
                    Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun sendOrder(
        bridgeUrl: String,
        token: String,
        symbol: String,
        action: String,
        volume: Double,
        stopLoss: Double? = null,
        takeProfit: Double? = null,
        comment: String = "CryptoQuant AI"
    ): Result<Mt5OrderResult> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(bridgeUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("URL Bridge MT5 belum dikonfigurasi dengan benar.")
            )

        try {
            val cleanSymbol = symbol.replace("/", "").trim()
            val payload = JSONObject().apply {
                put("symbol", cleanSymbol)
                put("action", action.uppercase())
                put("volume", volume)
                if (stopLoss != null && stopLoss > 0.0) put("sl", stopLoss)
                if (takeProfit != null && takeProfit > 0.0) put("tp", takeProfit)
                put("comment", comment)
            }

            val requestBuilder = Request.Builder()
                .url("$baseUrl/order")
                .post(payload.toString().toRequestBody(jsonMediaType))

            if (token.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Order MT5 gagal (HTTP ${response.code}): ${bodyStr.ifBlank { response.message }}")
                    )
                }

                val json = JSONObject(bodyStr)
                val ticket = json.optLong("ticket", 0L)
                val price = json.optDouble("price", 0.0)
                val msg = json.optString("message", "Order executed on MT5")
                Result.success(
                    Mt5OrderResult(
                        ticket = ticket,
                        symbol = cleanSymbol,
                        action = action.uppercase(),
                        volume = volume,
                        price = price,
                        message = msg
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "MT5 order failed", e)
            Result.failure(e)
        }
    }

    suspend fun closeAllPositions(bridgeUrl: String, token: String): Result<Int> =
        withContext(Dispatchers.IO) {
            val baseUrl = normalizeBaseUrl(bridgeUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("URL Bridge MT5 tidak valid.")
                )

            try {
                val requestBuilder = Request.Builder()
                    .url("$baseUrl/close_all")
                    .post("{}".toRequestBody(jsonMediaType))

                if (token.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${token.trim()}")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Close All MT5 gagal (HTTP ${response.code}): $bodyStr")
                        )
                    }
                    val json = JSONObject(bodyStr)
                    Result.success(json.optInt("closed_count", 0))
                }
            } catch (e: Exception) {
                Log.e(TAG, "MT5 close all failed", e)
                Result.failure(e)
            }
        }

    companion object {
        const val PYTHON_MT5_BRIDGE_SCRIPT = """# Simpan sebagai mt5_bridge.py & jalankan di PC/VPS Windows yang terpasang MT5 Desktop
# Install: pip install fastapi uvicorn MetaTrader5 pydantic
import os
import MetaTrader5 as mt5
from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel
from typing import Optional

API_TOKEN = os.getenv("MT5_BRIDGE_TOKEN", "GANTI_DENGAN_TOKEN_RAHASIA_ANDA")
app = FastAPI(title="CryptoQuant MT5 Bridge")

class OrderRequest(BaseModel):
    symbol: str
    action: str  # "BUY" atau "SELL"
    volume: float = 0.01
    sl: Optional[float] = None
    tp: Optional[float] = None
    comment: str = "CryptoQuant AI"

def verify_token(authorization: Optional[str]):
    if authorization != f"Bearer {API_TOKEN}":
        raise HTTPException(status_code=401, detail="Invalid bridge token")

@app.on_event("startup")
def init_mt5():
    if not mt5.initialize():
        raise RuntimeError(f"MT5 initialize() failed: {mt5.last_error()}")

@app.get("/health")
def health(authorization: Optional[str] = Header(None)):
    verify_token(authorization)
    acc = mt5.account_info()
    if acc is None:
        raise HTTPException(status_code=500, detail="MT5 account_info() unavailable")
    positions = mt5.positions_get() or []
    return {
        "login": str(acc.login),
        "server": acc.server,
        "balance": acc.balance,
        "equity": acc.equity,
        "open_positions": len(positions)
    }

@app.post("/order")
def place_order(req: OrderRequest, authorization: Optional[str] = Header(None)):
    verify_token(authorization)
    mt5.symbol_select(req.symbol, True)
    tick = mt5.symbol_info_tick(req.symbol)
    if tick is None:
        raise HTTPException(status_code=404, detail=f"Symbol {req.symbol} not found in MT5")

    is_buy = req.action.upper() == "BUY"
    order_type = mt5.ORDER_TYPE_BUY if is_buy else mt5.ORDER_TYPE_SELL
    price = tick.ask if is_buy else tick.bid

    request = {
        "action": mt5.TRADE_ACTION_DEAL,
        "symbol": req.symbol,
        "volume": req.volume,
        "type": order_type,
        "price": price,
        "deviation": 20,
        "magic": 202611,
        "comment": req.comment,
        "type_time": mt5.ORDER_TIME_GTC,
        "type_filling": mt5.ORDER_FILLING_IOC,
    }
    if req.sl: request["sl"] = req.sl
    if req.tp: request["tp"] = req.tp

    result = mt5.order_send(request)
    if result is None or result.retcode != mt5.TRADE_RETCODE_DONE:
        err = result.comment if result else mt5.last_error()
        raise HTTPException(status_code=400, detail=f"MT5 Order Error: {err}")

    return {"ticket": result.order, "price": result.price, "message": "Executed"}

@app.post("/close_all")
def close_all(authorization: Optional[str] = Header(None)):
    verify_token(authorization)
    positions = mt5.positions_get() or []
    closed = 0
    for pos in positions:
        tick = mt5.symbol_info_tick(pos.symbol)
        close_type = mt5.ORDER_TYPE_SELL if pos.type == mt5.POSITION_TYPE_BUY else mt5.ORDER_TYPE_BUY
        price = tick.bid if pos.type == mt5.POSITION_TYPE_BUY else tick.ask
        res = mt5.order_send({
            "action": mt5.TRADE_ACTION_DEAL,
            "symbol": pos.symbol,
            "volume": pos.volume,
            "type": close_type,
            "position": pos.ticket,
            "price": price,
            "deviation": 20,
            "magic": 202611,
            "comment": "KillSwitch"
        })
        if res and res.retcode == mt5.TRADE_RETCODE_DONE:
            closed += 1
    return {"closed_count": closed}

# Jalankan: uvicorn mt5_bridge:app --host 0.0.0.0 --port 8000"""
    }
}
