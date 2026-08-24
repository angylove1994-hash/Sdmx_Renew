package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.data.LogManager
import com.example.data.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver triggered by action: $action")
        LogManager.addLog(context, "📱 [Inicio Sistema] Dispositivo reiniciado / Evento ($action). Reactivando servicio 24/7...")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "sdmx:boot_receiver_wakelock_${System.currentTimeMillis()}"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes

        try {
            val hours = PreferencesManager.getSyncIntervalHours(context)
            
            // 1. Reschedule exact alarms & backup WorkManager
            SdmxAlarmScheduler.scheduleNextExactAlarm(context, hours)

            // 2. Start Foreground Service if aggressive mode is active
            if (PreferencesManager.isSyncAggressiveMode(context)) {
                SdmxForegroundService.startService(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in BootReceiver: ${e.message}", e)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
