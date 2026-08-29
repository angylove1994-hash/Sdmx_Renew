package com.example.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.worker.SdmxForegroundService

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_PERSISTENT_ID = "sdmx_persistent_channel"
        const val CHANNEL_ALERTS_ID = "sdmx_alerts_channel"
        const val NOTIFICATION_PERSISTENT_ID = 1001
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Persistent 24/7 foreground service channel (silent, no annoying vibrations)
            val persistentChannel = NotificationChannel(
                CHANNEL_PERSISTENT_ID,
                "Servicio Activo 24/7 (SDMX)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la aplicación activa en segundo plano para renovar cuentas sin interrupciones"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(persistentChannel)

            // Alert channel for important renewal events / errors
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Notificaciones de Renovación (SDMX)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos de renovación exitosa y errores en el panel"
            }
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    fun buildPersistentNotification(statusText: String): Notification {
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            piFlags
        )

        val runNowIntent = Intent(context, SdmxForegroundService::class.java).apply {
            action = SdmxForegroundService.ACTION_RUN_NOW
        }
        val runNowPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, 101, runNowIntent, piFlags)
        } else {
            PendingIntent.getService(context, 101, runNowIntent, piFlags)
        }

        return NotificationCompat.Builder(context, CHANNEL_PERSISTENT_ID)
            .setContentTitle("⚡ SDMX Renovador Activo 24/7")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Ejecutar Ahora", runNowPendingIntent)
            .build()
    }

    fun showSuccess(message: String, title: String = "✅ SDMX Renovación Exitosa") {
        try {
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, piFlags)

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .build()
            notificationManager.notify((System.currentTimeMillis() % 10000).toInt() + 2000, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showError(message: String, title: String = "⚠️ SDMX Atención Requerida") {
        try {
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, piFlags)

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .build()
            notificationManager.notify((System.currentTimeMillis() % 10000).toInt() + 3000, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
