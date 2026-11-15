package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.AppContainer
import com.example.MainActivity
import com.example.R
import com.example.data.model.BotStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val TAG = "BackgroundTradingSvc"
private const val CHANNEL_ID = "cryptoquant_24h_sync"
private const val NOTIFICATION_ID = 202611

class BackgroundTradingService : Service() {

    companion object {
        const val ACTION_START = "com.example.action.START_24H_SYNC"
        const val ACTION_STOP = "com.example.action.STOP_24H_SYNC"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopServiceGracefully("Dihentikan oleh pengguna")
            return START_NOT_STICKY
        }

        val notification = buildNotification(
            isOnline = checkCurrentInternetConnectivity(),
            syncCount = AppContainer.backgroundState.value.syncCount
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val onlineNow = checkCurrentInternetConnectivity()
        AppContainer.updateBackgroundState {
            it.copy(
                isRunning = true,
                isInternetConnected = onlineNow,
                startedAtMillis = it.startedAtMillis ?: System.currentTimeMillis(),
                statusMessage = if (onlineNow) {
                    "Aktif 24 Jam • Terhubung internet"
                } else {
                    "Aktif • Menunggu koneksi internet..."
                }
            )
        }

        startBackgroundLoop()
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Required on Android 15+ (targetSdk 35/36) for dataSync foreground services
        Log.w(TAG, "Foreground service dataSync reached OS time limit; stopping cleanly.")
        stopServiceGracefully("Sesi 6 jam Android 15+ selesai • Ketuk Mulai lagi saat membuka aplikasi")
    }

    private fun startBackgroundLoop() {
        if (syncJob?.isActive == true) return
        syncJob = serviceScope.launch {
            var tickCount = 0
            try {
                AppContainer.marketEngine.streamLiveTicks().collect { updatedMap ->
                    val isOnline = AppContainer.backgroundState.value.isInternetConnected
                    if (isOnline) {
                        val shouldSyncPc = tickCount % 4 == 0
                        AppContainer.processTickAndAutoForward(updatedMap, syncRemote = shouldSyncPc)
                        val newCount = AppContainer.backgroundState.value.syncCount + 1
                        AppContainer.updateBackgroundState {
                            it.copy(
                                isRunning = true,
                                lastSyncMillis = System.currentTimeMillis(),
                                syncCount = newCount,
                                statusMessage = "Aktif 24 Jam (Online) • Sinkron #$newCount"
                            )
                        }
                        if (tickCount % 5 == 0) {
                            updateNotification(isOnline = true, syncCount = newCount)
                        }
                    } else {
                        AppContainer.updateBackgroundState {
                            it.copy(
                                isRunning = true,
                                statusMessage = "Menunggu Internet • Otomatis lanjut saat online"
                            )
                        }
                        if (tickCount % 5 == 0) {
                            updateNotification(
                                isOnline = false,
                                syncCount = AppContainer.backgroundState.value.syncCount
                            )
                        }
                    }
                    tickCount++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in 24h background loop", e)
            }
        }
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val validated = checkCurrentInternetConnectivity()
                AppContainer.updateBackgroundState {
                    it.copy(
                        isInternetConnected = validated,
                        statusMessage = if (it.isRunning && validated) {
                            "Internet Tersedia • Sinkron 24 jam berjalan"
                        } else {
                            it.statusMessage
                        }
                    )
                }
                if (AppContainer.backgroundState.value.isRunning) {
                    updateNotification(
                        isOnline = validated,
                        syncCount = AppContainer.backgroundState.value.syncCount
                    )
                }
            }

            override fun onLost(network: Network) {
                val stillOnline = checkCurrentInternetConnectivity()
                AppContainer.updateBackgroundState {
                    it.copy(
                        isInternetConnected = stillOnline,
                        statusMessage = if (!stillOnline && it.isRunning) {
                            "Internet Terputus • Menunggu koneksi kembali..."
                        } else {
                            it.statusMessage
                        }
                    )
                }
                if (AppContainer.backgroundState.value.isRunning) {
                    updateNotification(
                        isOnline = stillOnline,
                        syncCount = AppContainer.backgroundState.value.syncCount
                    )
                }
            }
        }
        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun checkCurrentInternetConnectivity(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun stopServiceGracefully(reason: String) {
        syncJob?.cancel()
        syncJob = null
        AppContainer.updateBackgroundState {
            it.copy(
                isRunning = false,
                startedAtMillis = null,
                statusMessage = reason
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CryptoQuant Mode 24 Jam",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga bot trading & jembatan Exness MT5 tetap aktif di latar belakang selama terhubung internet."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(isOnline: Boolean, syncCount: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(isOnline, syncCount))
    }

    private fun buildNotification(isOnline: Boolean, syncCount: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BackgroundTradingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bots = AppContainer.botManager.bots.value
        val activeBots = bots.count { it.status == BotStatus.RUNNING }
        val totalProfit = bots.sumOf { it.totalProfitUsdt }
        val mt5 = AppContainer.mt5BridgeConfig.value
        val exnessStatus = if (mt5.isConnected) "Exness ${mt5.accountMode} #${mt5.accountLogin}" else "Exness Siaga"

        val title = if (isOnline) {
            "Mode 24 Jam Online • $activeBots Bot Aktif"
        } else {
            "Mode 24 Jam Menunggu Internet"
        }
        val content = if (isOnline) {
            "$exnessStatus | PnL: ${if (totalProfit >= 0) "+" else ""}${"%.2f".format(totalProfit)} USD | Sync #$syncCount"
        } else {
            "Koneksi terputus. Sinkron otomatis dilanjut begitu internet aktif."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Hentikan", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        syncJob?.cancel()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback?.let { cb ->
            try {
                cm?.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        if (AppContainer.backgroundState.value.isRunning) {
            AppContainer.updateBackgroundState {
                it.copy(isRunning = false, startedAtMillis = null)
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
