package com.example.worker

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.LocalDatabase
import com.example.data.PreferencesManager
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class SdmxForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.sdmx.service.ACTION_START"
        const val ACTION_STOP = "com.example.sdmx.service.ACTION_STOP"
        const val ACTION_RUN_NOW = "com.example.sdmx.service.ACTION_RUN_NOW"
        private const val TAG = "SdmxForegroundService"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, SdmxForegroundService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, SdmxForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping foreground service: ${e.message}", e)
            }
        }

        fun triggerManualRun(context: Context) {
            try {
                val intent = Intent(context, SdmxForegroundService::class.java).apply {
                    action = ACTION_RUN_NOW
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering manual run: ${e.message}", e)
            }
        }

        fun updateNotificationStatus(context: Context, nextRunMillis: Long, vigentesCount: Int) {
            try {
                val notificationHelper = NotificationHelper(context)
                val sdf = SimpleDateFormat("HH:mm", Locale.US)
                val nextStr = if (nextRunMillis > 0) sdf.format(Date(nextRunMillis)) else "Calculando..."
                val text = "Próx. renovación: $nextStr | $vigentesCount cuentas activas"
                val notification = notificationHelper.buildPersistentNotification(text)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NotificationHelper.NOTIFICATION_PERSISTENT_ID, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        promoteToForeground("Iniciando servicio de fondo 24/7...")
        startMonitoringLoop()
    }

    private fun promoteToForeground(statusText: String) {
        val notification = notificationHelper.buildPersistentNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_PERSISTENT_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_PERSISTENT_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RUN_NOW -> {
                serviceScope.launch {
                    promoteToForeground("🔄 Ejecutando renovación en este momento...")
                    SdmxExecutionEngine.executeRenewalCycle(this@SdmxForegroundService, "Botón Notificación")
                    refreshStatusNotification()
                }
            }
            ACTION_START, null -> {
                refreshStatusNotification()
            }
        }
        return START_STICKY // Indestructible service: OS restarts it if killed
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val nextRun = PreferencesManager.getSyncNextRunTime(this@SdmxForegroundService)
                    val now = System.currentTimeMillis()

                    if (nextRun > 0 && now >= nextRun) {
                        promoteToForeground("🔄 Ejecutando renovación programada...")
                        SdmxExecutionEngine.executeRenewalCycle(this@SdmxForegroundService, "Servicio 24/7 (Loop)")
                    }

                    refreshStatusNotification()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop: ${e.message}")
                }
                delay(60_000L) // Check every minute
            }
        }
    }

    private fun refreshStatusNotification() {
        try {
            val nextRun = PreferencesManager.getSyncNextRunTime(this)
            val db = LocalDatabase(this)
            db.loadData()
            val users = db.users.value

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val hoy = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val vigentesCount = users.count {
                try {
                    val venc = it.vencimiento ?: ""
                    val fechaLimpia = venc.trim().substringBefore("T")
                    val fecha = sdf.parse(fechaLimpia)
                    fecha != null && !fecha.before(hoy)
                } catch (e: Exception) {
                    false
                }
            }

            val timeSdf = SimpleDateFormat("HH:mm", Locale.US)
            val nextStr = if (nextRun > 0) timeSdf.format(Date(nextRun)) else "Pendiente"
            val text = "Próx. renovación: $nextStr | $vigentesCount cuentas activas"
            promoteToForeground(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
