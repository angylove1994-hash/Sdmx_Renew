package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SdmxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = SdmxExecutionEngine.executeRenewalCycle(applicationContext, "WorkManager Backup")
            if (result.success) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context, hours: Int) {
            SdmxAlarmScheduler.scheduleNextExactAlarm(context, hours)
        }
    }
}
