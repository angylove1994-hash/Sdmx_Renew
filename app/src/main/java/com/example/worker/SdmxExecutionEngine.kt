package com.example.worker

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.data.LocalDatabase
import com.example.data.LogManager
import com.example.data.PreferencesManager
import com.example.network.SdmxApiService
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class RenewalExecutionResult(
    val success: Boolean,
    val renewedCount: Int,
    val totalVigentes: Int,
    val message: String
)

object SdmxExecutionEngine {
    private const val TAG = "SdmxExecutionEngine"
    private val executionMutex = Mutex()

    suspend fun executeRenewalCycle(context: Context, triggerSource: String = "Automático"): RenewalExecutionResult = withContext(Dispatchers.IO) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "sdmx:renewal_wakelock_${System.currentTimeMillis()}"
        )

        try {
            wakeLock?.acquire(10 * 60 * 1000L) // Max 10 minutes wake lock
            
            if (executionMutex.isLocked) {
                LogManager.addLog(context, "⏳ [$triggerSource] Ya hay un ciclo de renovación en ejecución. Omitiendo duplicado.")
                return@withContext RenewalExecutionResult(
                    success = false,
                    renewedCount = 0,
                    totalVigentes = 0,
                    message = "Ejecución en curso"
                )
            }

            executionMutex.withLock {
                return@withLock performCycleInternal(context, triggerSource)
            }
        } catch (e: Exception) {
            val err = "❌ [$triggerSource] Error crítico en motor de renovación: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, err, e)
            LogManager.addLog(context, err)
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showError("Fallo crítico en ciclo: ${e.message}")
            return@withContext RenewalExecutionResult(
                success = false,
                renewedCount = 0,
                totalVigentes = 0,
                message = e.message ?: "Error desconocido"
            )
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wakelock: ${e.message}")
            }
        }
    }

    private suspend fun performCycleInternal(context: Context, triggerSource: String): RenewalExecutionResult {
        val notificationHelper = NotificationHelper(context)
        val prefs = PreferencesManager(context)
        val api = SdmxApiService()
        val db = LocalDatabase(context)

        // Read credentials (with fallback to fast sync prefs)
        var user = try { prefs.userSdmx.first() } catch (e: Exception) { "" }
        var pass = try { prefs.passSdmx.first() } catch (e: Exception) { "" }
        
        if (user.isEmpty() || pass.isEmpty()) {
            val (syncUser, syncPass) = PreferencesManager.getSyncCredentials(context)
            user = syncUser
            pass = syncPass
        }

        if (user.isEmpty() || pass.isEmpty()) {
            val msg = "❌ [$triggerSource] Error: Credenciales SDMX no configuradas."
            LogManager.addLog(context, msg)
            notificationHelper.showError("Credenciales no configuradas.")
            return RenewalExecutionResult(false, 0, 0, msg)
        }

        LogManager.addLog(context, "🚀 [$triggerSource] Iniciando ciclo de renovación agresivo para: $user...")

        // Step 1: Health check
        val healthCheckOk = api.verifyHealthCheck(context, user, pass)
        if (!healthCheckOk) {
            val msg = "❌ [$triggerSource] Verificación previa fallida. No se pudo validar acceso al panel."
            LogManager.addLog(context, msg)
            notificationHelper.showError("Verificación previa fallida. Revisa el log.")
            return RenewalExecutionResult(false, 0, 0, msg)
        }

        // Step 2: Load local users
        db.loadData()
        val users = db.users.value

        if (users.isEmpty()) {
            val msg = "⚠️ [$triggerSource] Base de datos local vacía. No hay usuarios registrados para renovar."
            LogManager.addLog(context, msg)
            notificationHelper.showError("Base de datos local vacía.")
            return RenewalExecutionResult(false, 0, 0, msg)
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

        LogManager.addLog(context, "📋 [$triggerSource] Total usuarios: ${users.size} | Vigentes para renovar: ${vigentes.size} | No vigentes: ${users.size - vigentes.size}")

        if (vigentes.isEmpty()) {
            val msg = "ℹ️ [$triggerSource] No hay cuentas vigentes para renovar en este ciclo."
            LogManager.addLog(context, msg)
            return RenewalExecutionResult(true, 0, 0, msg)
        }

        var procesados = 0
        for (userToRenew in vigentes) {
            // Delete old line
            if (!userToRenew.id.isNullOrEmpty()) {
                val delOk = api.deleteLine(context, userToRenew.id)
                LogManager.addLog(context, "🗑️ [$triggerSource] Eliminado del panel: ${userToRenew.usuario} (id: ${userToRenew.id}) - OK: $delOk")
                delay(300)
            }

            // Create new line
            val createOk = api.createLine(
                context = context,
                username = userToRenew.usuario,
                pass = userToRenew.password,
                expDate = userToRenew.vencimiento,
                adultos = userToRenew.adultos
            )

            if (createOk.isSuccess) {
                LogManager.addLog(context, "✅ [$triggerSource] Renovado con éxito en panel: ${userToRenew.usuario}")
                procesados++
            } else {
                val err = createOk.exceptionOrNull()?.message ?: "Error desconocido"
                LogManager.addLog(context, "❌ [$triggerSource] Error al renovar ${userToRenew.usuario}: $err")
            }
            delay(400)
        }

        delay(1500)

        // Step 3: Fetch updated IDs from the panel table
        LogManager.addLog(context, "🔎 [$triggerSource] Obteniendo nuevos IDs de líneas desde la tabla SDMX...")
        val newTableIds = api.getTableIds(context)
        var updatedIdsCount = 0

        val updatedUsers = users.map { oldUser ->
            if (vigentes.contains(oldUser)) {
                val newId = newTableIds[oldUser.usuario]
                if (newId != null) {
                    updatedIdsCount++
                    LogManager.addLog(context, "📝 [$triggerSource] ID actualizado: ${oldUser.usuario} → $newId")
                    oldUser.copy(id = newId)
                } else {
                    oldUser
                }
            } else {
                oldUser
            }
        }

        db.saveUsers(updatedUsers)

        // Step 4: Record execution times and calculate next run
        val now = System.currentTimeMillis()
        var hoursConfig = try { prefs.intervalHours.first().toIntOrNull() ?: 24 } catch (e: Exception) { 24 }
        if (hoursConfig < 1) hoursConfig = 1
        
        val nextRunTime = now + (hoursConfig * 60 * 60 * 1000L)
        prefs.recordExecutionTimes(now, nextRunTime)

        // Step 5: Reschedule alarms & foreground notification to keep continuous loop
        SdmxAlarmScheduler.scheduleNextExactAlarm(context, hoursConfig)
        SdmxForegroundService.updateNotificationStatus(context, nextRunTime, vigentes.size)

        val successMsg = "🎉 [$triggerSource] Ciclo completado: $procesados de ${vigentes.size} cuentas renovadas exitosamente. Próxima ejecución en $hoursConfig horas."
        LogManager.addLog(context, successMsg)
        notificationHelper.showSuccess("Ciclo completado. $procesados/${vigentes.size} cuentas renovadas. Próx: $hoursConfig hrs.")

        return RenewalExecutionResult(
            success = true,
            renewedCount = procesados,
            totalVigentes = vigentes.size,
            message = successMsg
        )
    }
}
