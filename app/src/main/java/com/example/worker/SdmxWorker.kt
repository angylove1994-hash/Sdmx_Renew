package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.LocalDatabase
import com.example.data.PreferencesManager
import com.example.data.LogManager
import com.example.network.SdmxApiService
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SdmxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val notificationHelper = NotificationHelper(applicationContext)
            val prefs = PreferencesManager(applicationContext)
            val api = SdmxApiService()
            val db = LocalDatabase(applicationContext)

            val user = prefs.userSdmx.first()
            val pass = prefs.passSdmx.first()

            if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
                LogManager.addLog(applicationContext, "❌ Error en Background Worker: Credenciales SDMX no configuradas.")
                notificationHelper.showError("Credenciales no configuradas.")
                return Result.failure()
            }

            LogManager.addLog(applicationContext, "⚙️ Worker iniciado en segundo plano. Iniciando verificación previa...")

            val healthCheckOk = api.verifyHealthCheck(applicationContext, user, pass)
            if (!healthCheckOk) {
                LogManager.addLog(applicationContext, "❌ Worker: Verificación previa fallida. Reintentando en 1 minuto.")
                notificationHelper.showError("Verificación previa fallida para $user. Reintentando en 1 minuto.")
                scheduleRetry(applicationContext)
                return Result.failure()
            }

            // On successful health check, cancel any scheduled retry
            try {
                WorkManager.getInstance(applicationContext).cancelUniqueWork("SdmxRetryWork")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            db.loadData()
            val users = db.users.value
            
            if (users.isEmpty()) {
                LogManager.addLog(applicationContext, "⚠️ Worker: Base de datos local vacía. No hay usuarios para renovar.")
                notificationHelper.showError("Base de datos local vacía.")
                return Result.failure()
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val hoy = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val vigentes = users.filter { 
                try {
                    val venc = it.vencimiento ?: ""
                    val fechaLimpia = venc.trim().substringBefore("T")
                    val fecha = sdf.parse(fechaLimpia)
                    fecha != null && !fecha.before(hoy)
                } catch (e: Exception) {
                    false
                }
            }
            
            LogManager.addLog(applicationContext, "📋 Worker: Total usuarios: ${users.size} | Vigentes: ${vigentes.size} | No vigentes: ${users.size - vigentes.size}")

            var procesados = 0
            for (userToRenew in vigentes) {
                // Delete
                if (!userToRenew.id.isNullOrEmpty()) {
                    val delOk = api.deleteLine(userToRenew.id)
                    LogManager.addLog(applicationContext, "🗑️ Worker: Eliminado del panel: ${userToRenew.usuario} (id: ${userToRenew.id}) - Result: $delOk")
                }
                
                // Create
                val createOk = api.createLine(
                    username = userToRenew.usuario,
                    pass = userToRenew.password,
                    expDate = userToRenew.vencimiento,
                    adultos = userToRenew.adultos
                )
                if (createOk.isSuccess) {
                    LogManager.addLog(applicationContext, "✅ Worker: Creado/Renovado en panel: ${userToRenew.usuario}")
                    procesados++
                } else {
                    val err = createOk.exceptionOrNull()?.message ?: "Error desconocido"
                    LogManager.addLog(applicationContext, "❌ Worker: Error al crear ${userToRenew.usuario}: $err")
                }
            }
            
            kotlinx.coroutines.delay(1500)

            // Get new IDs
            val newTableIds = api.getTableIds()
            var updatedIdsCount = 0
            
            val updatedUsers = users.map { oldUser ->
                if (vigentes.contains(oldUser)) {
                    val newId = newTableIds[oldUser.usuario]
                    if (newId != null) {
                        updatedIdsCount++
                        LogManager.addLog(applicationContext, "📝 Worker: ID actualizado: ${oldUser.usuario} → $newId")
                        oldUser.copy(id = newId)
                    } else {
                        oldUser
                    }
                } else {
                    oldUser
                }
            }

            db.saveUsers(updatedUsers)
            
            val nextHours = prefs.intervalHours.first().toIntOrNull() ?: 24
            
            val successMsg = "🎉 Worker: Ciclo en segundo plano completado. $procesados usuarios renovados exitosamente. Próxima ejecución en: $nextHours horas."
            LogManager.addLog(applicationContext, successMsg)
            notificationHelper.showSuccess("Ciclo completado. $procesados usuarios renovados. Próxima ejecución en $nextHours horas.")
            
            // update last run time
            prefs.saveInterval(nextHours.toString())

            Result.success()
        } catch (t: Throwable) {
            LogManager.addLog(applicationContext, "❌ Error crítico inesperado en Worker: ${t.message ?: t.javaClass.simpleName}")
            t.printStackTrace()
            Result.failure()
        }
    }
    
    companion object {
        fun schedule(context: Context, hours: Int) {
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
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun scheduleRetry(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<SdmxWorker>()
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "SdmxRetryWork",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
