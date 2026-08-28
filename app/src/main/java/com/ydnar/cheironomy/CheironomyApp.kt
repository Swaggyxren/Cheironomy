package com.ydnar.cheironomy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CheironomyApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "cheironomy_foreground_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.service_notification_channel_name)
            val descriptionText = getString(R.string.service_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
