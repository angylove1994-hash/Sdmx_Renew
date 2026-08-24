package com.example.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.example.MainActivity
import com.example.data.BatteryOptimizationHelper
import com.example.data.PreferencesManager
import java.util.concurrent.TimeUnit

object SdmxAlarmScheduler {
    private const val TAG = "SdmxAlarmScheduler"
    private const val ALARM_REQUEST_CODE = 9001

    fun scheduleNextExactAlarm(context: Context, hours: Int) {
        try {
            val validHours = if (hours < 1) 1 else hours
            val intervalMillis = validHours * 60 * 60 * 1000L
            val triggerAtMillis = System.currentTimeMillis() + intervalMillis

            PreferencesManager.setSyncExecutionTimes(
                context,
                lastRun = PreferencesManager.getSyncLastRunTime(context),
                nextRun = triggerAtMillis
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                val intent = Intent(context, SdmxAlarmReceiver::class.java).apply {
                    action = SdmxAlarmReceiver.ACTION_TRIGGER_SDMX_RENEWAL
                    putExtra(SdmxAlarmReceiver.EXTRA_HOURS, validHours)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    flags
                )

                val showIntent = Intent(context, MainActivity::class.java)
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    showIntent,
                    flags
                )

                // High-priority exact alarm clock (immune to deep doze mode)
                try {
                    val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                    alarmManager.setAlarmClock(clockInfo, pendingIntent)
                    Log.d(TAG, "Exact AlarmClock scheduled for $triggerAtMillis ($validHours hours from now)")
                } catch (e: Exception) {
                    Log.w(TAG, "setAlarmClock failed, trying setExactAndAllowWhileIdle: ${e.message}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerAtMillis,
                                pendingIntent
                            )
                        } catch (ex: Exception) {
                            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        }
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                }
            }

            // Schedule WorkManager as a backup safety net
            scheduleBackupWork(context, validHours)

            // Ensure ForegroundService is running if in aggressive mode
            if (PreferencesManager.isSyncAggressiveMode(context)) {
                SdmxForegroundService.startService(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm: ${e.message}", e)
        }
    }

    fun cancelAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, SdmxAlarmReceiver::class.java).apply {
                action = SdmxAlarmReceiver.ACTION_TRIGGER_SDMX_RENEWAL
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags)
            alarmManager?.cancel(pendingIntent)

            WorkManager.getInstance(context).cancelUniqueWork("SdmxAutoRenewWork")
            WorkManager.getInstance(context).cancelUniqueWork("SdmxRetryWork")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleBackupWork(context: Context, hours: Int) {
        try {
            val validHours = if (hours < 1) 1 else hours
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<SdmxWorker>(validHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "SdmxAutoRenewWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
