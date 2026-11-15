package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

object NotificationHelper {
    const val CHANNEL_TRANSACTIONS = "fintrack_transactions"
    const val CHANNEL_ALERTS = "fintrack_alerts"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val txChannel = NotificationChannel(
                CHANNEL_TRANSACTIONS,
                "Detected Transactions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for newly captured UPI, card, and bank transactions"
            }
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Budget & Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for budget limits, large transactions, and sync status"
            }
            nm.createNotificationChannel(txChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    fun showLocalNotification(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
