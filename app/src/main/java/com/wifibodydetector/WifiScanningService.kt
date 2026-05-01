package com.wifibodydetector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

class WifiScanningService : Service() {

    private lateinit var wifiManager: WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private val scanIntervalMs = 1500L

    private val networkStates = mutableMapOf<String, MutableList<Int>>()
    private val maxHistorySize = 50

    var onScanComplete: ((List<ScanResult>, Map<String, List<Int>>) -> Unit)? = null

    companion object {
        const val CHANNEL_ID = "wifi_scanning_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startScanning()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Scanning Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Body Detector")
            .setContentText("Scanning for movement...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startScanning() {
        handler.post(object : Runnable {
            override fun run() {
                if (wifiManager.isWifiEnabled) {
                    wifiManager.startScan()
                }
                handler.postDelayed(this, scanIntervalMs)
            }
        })
    }

    fun onScanResultsReceived(results: List<ScanResult>) {
        val currentRSSI = results.filter { it.SSID.isNotBlank() }.associateBy { it.SSID }

        for ((ssid, scanResult) in currentRSSI) {
            val rssi = scanResult.level
            if (!networkStates.containsKey(ssid)) {
                networkStates[ssid] = mutableListOf()
            }
            networkStates[ssid]!!.add(rssi)
            if (networkStates[ssid]!!.size > maxHistorySize) {
                networkStates[ssid]!!.removeAt(0)
            }
        }

        onScanComplete?.invoke(results, networkStates.toMap())
    }
}
