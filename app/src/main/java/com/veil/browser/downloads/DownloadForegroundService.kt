package com.veil.browser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class DownloadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "veil_downloads"
        const val NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Veil Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Veil Segmented Download Active")
                .setContentText("Downloading file segments in parallel")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Veil Segmented Download Active")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .build()
        }
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
