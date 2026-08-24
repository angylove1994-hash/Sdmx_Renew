package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.data.LogManager
import com.example.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SdmxAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIGGER_SDMX_RENEWAL = "com.example.sdmx.ACTION_TRIGGER_RENEWAL"
        const val EXTRA_HOURS = "extra_hours"
        private const val TAG = "SdmxAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "SdmxAlarmReceiver received intent: ${intent?.action}")
        LogManager.addLog(context, "⚡ [Alarma Exacta] Disparo de alarma programada recibido.")

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "sdmx:alarm_receiver_wakelock_${System.currentTimeMillis()}"
        )

        wakeLock?.acquire(10 * 60 * 1000L) // 10 min max

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Execute renewal cycle via the unified engine
                SdmxExecutionEngine.executeRenewalCycle(context, "Alarma Exacta (24/7)")
            } catch (e: Exception) {
                Log.e(TAG, "Error in alarm receiver execution: ${e.message}", e)
                LogManager.addLog(context, "❌ Error en receptor de alarma: ${e.message}")
            } finally {
                try {
                    // Reschedule next cycle
                    val hours = PreferencesManager.getSyncIntervalHours(context)
                    SdmxAlarmScheduler.scheduleNextExactAlarm(context, hours)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                pendingResult.finish()
            }
        }
    }
}
