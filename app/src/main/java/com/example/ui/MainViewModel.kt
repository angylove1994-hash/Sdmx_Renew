package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocalDatabase
import com.example.data.LogManager
import com.example.data.PreferencesManager
import com.example.data.UserModel
import com.example.network.SdmxApiService
import com.example.notifications.NotificationHelper
import com.example.notifications.NtfyManager
import com.example.worker.SdmxAlarmScheduler
import com.example.worker.SdmxExecutionEngine
import com.example.worker.SdmxForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val db = LocalDatabase(application)
    private val api = SdmxApiService()

    val userSdmx = prefs.userSdmx.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val passSdmx = prefs.passSdmx.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val intervalHours = prefs.intervalHours.stateIn(viewModelScope, SharingStarted.Eagerly, "24")
    val isAggressiveMode = prefs.isAggressiveMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val lastExecutionTime = prefs.lastExecutionTime.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val nextExecutionTime = prefs.nextExecutionTime.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val ntfyTopic = prefs.ntfyTopic.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_NTFY_TOPIC)
    
    val users = db.users

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        _logs.value = LogManager.getLogs(application)
        
        // Start foreground service & schedule exact alarms automatically
        viewModelScope.launch {
            val hours = intervalHours.value.toIntOrNull() ?: 24
            SdmxAlarmScheduler.scheduleNextExactAlarm(application, hours)
            if (isAggressiveMode.value) {
                SdmxForegroundService.startService(application)
            }
        }
    }

    fun loadData() {
        db.loadData()
        _logs.value = LogManager.getLogs(getApplication())
    }

    fun addLog(msg: String) {
        LogManager.addLog(getApplication(), msg)
        _logs.value = LogManager.getLogs(getApplication())
    }

    fun clearLogs() {
        LogManager.clearLogs(getApplication())
        _logs.value = emptyList()
    }

    fun saveCredentials(user: String, pass: String) = viewModelScope.launch {
        prefs.saveCredentials(user, pass)
        addLog("Credenciales guardadas para: $user")
        val hours = intervalHours.value.toIntOrNull() ?: 24
        SdmxAlarmScheduler.scheduleNextExactAlarm(getApplication(), hours)
        if (isAggressiveMode.value) {
            SdmxForegroundService.startService(getApplication())
        }
    }

    fun saveInterval(hours: String) = viewModelScope.launch {
        prefs.saveInterval(hours)
        addLog("Intervalo configurado a: $hours horas")
        val h = hours.toIntOrNull() ?: 24
        SdmxAlarmScheduler.scheduleNextExactAlarm(getApplication(), h)
    }

    fun setAggressiveMode(enabled: Boolean) = viewModelScope.launch {
        prefs.setAggressiveMode(enabled)
        if (enabled) {
            SdmxForegroundService.startService(getApplication())
            val h = intervalHours.value.toIntOrNull() ?: 24
            SdmxAlarmScheduler.scheduleNextExactAlarm(getApplication(), h)
            addLog("⚡ Modo Agresivo 24/7 Activado (Servicio en primer plano continuo)")
        } else {
            SdmxForegroundService.stopService(getApplication())
            addLog("Modo Agresivo desactivado.")
        }
    }

    fun runManualCycle() = viewModelScope.launch {
        if (_isLoading.value) return@launch
        _isLoading.value = true
        addLog("🚀 [Manual] Iniciando ciclo de renovación...")

        try {
            val result = SdmxExecutionEngine.executeRenewalCycle(getApplication(), "Manual UI")
            loadData()
        } catch (e: Exception) {
            addLog("❌ Error en ejecución manual: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    fun addUser(username: String, pass: String, meses: Int, adultos: Boolean) = viewModelScope.launch {
        _isLoading.value = true
        addLog("Agregando nuevo usuario: $username...")
        try {
            val sdUser = userSdmx.value
            val sdPass = passSdmx.value
            if (sdUser.isNullOrEmpty() || sdPass.isNullOrEmpty()) {
                addLog("Error: Credenciales no configuradas.")
                return@launch
            }

            if (!api.login(getApplication(), sdUser, sdPass)) {
                addLog("Error: Login fallido.")
                return@launch
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val targetDate = Calendar.getInstance().apply {
                add(Calendar.MONTH, meses)
            }.time
            val expDateStr = sdf.format(targetDate)

            val createOk = api.createLine(getApplication(), username, pass, expDateStr, adultos)
            if (createOk.isFailure) {
                val err = createOk.exceptionOrNull()?.message ?: "Desconocido"
                addLog("❌ Error al crear: $err")
                return@launch
            }
            
            delay(1500) // The server might take a moment to reflect the new user in the table

            val ids = api.getTableIds(getApplication())
            val newId = ids[username] ?: ""
            
            val newUser = UserModel(
                id = newId,
                usuario = username,
                password = pass,
                vencimiento = expDateStr,
                adultos = adultos
            )
            db.addUser(newUser)
            addLog("✅ Creado: $username | id: $newId | vence: $expDateStr | adultos: $adultos")
            
            // Refresh service notification
            val hours = intervalHours.value.toIntOrNull() ?: 24
            SdmxAlarmScheduler.scheduleNextExactAlarm(getApplication(), hours)
        } catch (e: Exception) {
            addLog("Error inesperado al agregar usuario: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }
    
    fun replaceUsers(newUsers: List<UserModel>) = viewModelScope.launch {
        _isLoading.value = true
        val context = getApplication<Application>()
        val notifHelper = NotificationHelper(context)

        addLog("📥 [Importación JSON] Iniciando verificación y carga de ${newUsers.size} usuarios...")
        try {
            var sdUser = userSdmx.value?.trim() ?: ""
            var sdPass = passSdmx.value?.trim() ?: ""
            if (sdUser.isEmpty() || sdPass.isEmpty()) {
                val (syncUser, syncPass) = PreferencesManager.getSyncCredentials(context)
                sdUser = syncUser.trim()
                sdPass = syncPass.trim()
            }

            if (sdUser.isEmpty() || sdPass.isEmpty()) {
                val errorMsg = "Credenciales de administrador SDMX no configuradas."
                addLog("❌ $errorMsg")
                addLog("⚠️ En este momento no se puede cargar la base de datos.")
                notifHelper.showError("En este momento no se puede cargar la base de datos (Faltan credenciales de admin SDMX).", "🚨 SDMX: Carga Cancelada")
                NtfyManager.sendPushNotification(
                    context = context,
                    title = "🚨 SDMX: Carga de BD Cancelada",
                    body = "❌ En este momento no se puede cargar la base de datos.\n\n🔍 Motivo: Credenciales de administrador SDMX no configuradas.",
                    tags = "warning,x",
                    priority = "urgent"
                )
                return@launch
            }

            addLog("🔬 [Importación JSON] Ejecutando validación de control (Login -> Creación de prueba -> Borrado de prueba)...")
            val controlResult = api.performControlValidation(context, sdUser, sdPass)

            if (controlResult.isFailure) {
                val failReason = controlResult.exceptionOrNull()?.message ?: "El panel no respondió correctamente"
                addLog("❌ [Importación JSON] Validación de control fallida: $failReason")
                addLog("⛔ En este momento no se puede cargar la base de datos.")
                notifHelper.showError("En este momento no se puede cargar la base de datos (Panel SDMX no superó la prueba de control).", "🚨 SDMX: Carga Cancelada")
                NtfyManager.sendPushNotification(
                    context = context,
                    title = "🚨 SDMX: Carga de BD Cancelada",
                    body = "❌ En este momento no se puede cargar la base de datos.\n\n🔍 Causa: La validación de control del panel SDMX falló ($failReason).\n\n📌 La base de datos no fue modificada.",
                    tags = "warning,x",
                    priority = "urgent"
                )
                return@launch
            }

            addLog("✅ [Importación JSON] Validación de control superada con éxito.")
            addLog("🔎 [Importación JSON] Consultando IDs de líneas en la tabla SDMX para vincular usuarios...")
            val livePanelIds = api.getTableIds(context)

            var matchedIdsCount = 0
            val syncedUsers = newUsers.map { importedUser ->
                val usernameClean = importedUser.usuario.trim()
                val liveId = livePanelIds[usernameClean]
                if (!liveId.isNullOrEmpty()) {
                    matchedIdsCount++
                    addLog("🔗 Usuario '$usernameClean' vinculado a ID de panel #$liveId")
                    importedUser.copy(id = liveId)
                } else {
                    importedUser
                }
            }

            db.saveUsers(syncedUsers)
            addLog("📥 [Importación JSON] Base de datos guardada exitosamente (${syncedUsers.size} usuarios cargados, $matchedIdsCount con ID sincronizado).")

            val hours = intervalHours.value.toIntOrNull() ?: 24
            SdmxAlarmScheduler.scheduleNextExactAlarm(context, hours)

            addLog("🚀 [Importación JSON] Iniciando renovación inmediata de todos los usuarios importados...")
            notifHelper.showSuccess("Base de datos cargada (${syncedUsers.size} usuarios). Ejecutando renovación de todos los usuarios...", "📥 SDMX: BD Importada")

            // Realizar en este momento la renovación de todos los usuarios importados
            val renewalResult = SdmxExecutionEngine.executeRenewalCycle(context, "Importación JSON")
            addLog("🏁 [Importación JSON] Resultado del ciclo de renovación: ${renewalResult.message}")

        } catch (e: Exception) {
            val exMsg = e.message ?: "Excepción desconocida"
            addLog("❌ Error durante la importación de JSON: $exMsg")
            addLog("⚠️ En este momento no se puede cargar la base de datos.")
            notifHelper.showError("En este momento no se puede cargar la base de datos: $exMsg", "🚨 SDMX: Error de Importación")
            NtfyManager.sendPushNotification(
                context = context,
                title = "🚨 SDMX: Error de Importación",
                body = "❌ En este momento no se puede cargar la base de datos.\n\nExcepción: $exMsg",
                tags = "warning,x",
                priority = "urgent"
            )
        } finally {
            _isLoading.value = false
        }
    }

    fun deleteUser(user: UserModel) = viewModelScope.launch {
        _isLoading.value = true
        val context = getApplication<Application>()
        val notifHelper = NotificationHelper(context)
        addLog("🗑️ Solicitud de eliminación para: ${user.usuario}...")
        try {
            val sdUser = userSdmx.value?.trim() ?: ""
            val sdPass = passSdmx.value?.trim() ?: ""
            
            if (sdUser.isEmpty() || sdPass.isEmpty()) {
                val errorMsg = "Credenciales de administrador SDMX vacías. No se puede eliminar del panel."
                addLog("❌ $errorMsg")
                addLog("⚠️ El usuario '${user.usuario}' NO se eliminó de la app. Configura admin primero.")
                notifHelper.showError("No se pudo eliminar a '${user.usuario}'. Faltan credenciales de administrador SDMX.", "🚨 SDMX: Fallo al Eliminar")
                NtfyManager.sendPushNotification(
                    context = context,
                    title = "🚨 SDMX: Fallo al eliminar ${user.usuario}",
                    body = "❌ No se puede eliminar de SDMX: credenciales de administrador no configuradas.\n\n⚠️ El usuario '${user.usuario}' NO fue borrado de la app para que puedas reintentarlo.",
                    tags = "warning,x",
                    priority = "urgent"
                )
                return@launch
            }

            val deleteResult = api.deleteUserFromPanel(
                context = context,
                adminUser = sdUser,
                adminPass = sdPass,
                targetUsername = user.usuario,
                explicitId = user.id
            )

            if (deleteResult.isSuccess) {
                // Remove from local database ONLY after panel deletion succeeds
                val current = users.value.toMutableList()
                current.removeAll { it.usuario.equals(user.usuario, ignoreCase = true) || (it.id.isNotEmpty() && it.id == user.id) }
                db.saveUsers(current)

                val detail = if (deleteResult.getOrNull() == "NOT_FOUND_OR_ALREADY_DELETED") {
                    "El usuario '${user.usuario}' ya no figuraba en el panel SDMX. Se eliminó de la app con éxito."
                } else {
                    "El usuario '${user.usuario}' fue eliminado exitosamente del panel SDMX y de la app."
                }

                addLog("✅ $detail")
                notifHelper.showSuccess("Usuario '${user.usuario}' eliminado correctamente del panel y de la app.", "🗑️ SDMX: Usuario Eliminado")
                NtfyManager.sendPushNotification(
                    context = context,
                    title = "🗑️ SDMX: Usuario Eliminado",
                    body = "✅ $detail",
                    tags = "wastebasket,white_check_mark",
                    priority = "default"
                )
            } else {
                val errorReason = deleteResult.exceptionOrNull()?.message ?: "Error desconocido"
                addLog("❌ Error al eliminar en panel SDMX: $errorReason")
                addLog("⚠️ El usuario '${user.usuario}' NO se borró de la app para permitir reintentar.")
                
                notifHelper.showError("No se pudo borrar '${user.usuario}' del panel: $errorReason. Se mantuvo en la app.", "🚨 SDMX: Error al Borrar")
                NtfyManager.sendPushNotification(
                    context = context,
                    title = "🚨 SDMX: Error al eliminar ${user.usuario}",
                    body = "❌ No se pudo eliminar a '${user.usuario}' del panel SDMX.\n\n🔍 Causa: $errorReason\n\n📌 El usuario se conservó en la app para que puedas reintentar.",
                    tags = "warning,rotating_light",
                    priority = "urgent"
                )
            }
        } catch (e: Exception) {
            val exMsg = e.message ?: "Excepción desconocida"
            addLog("❌ Excepción al intentar eliminar usuario: $exMsg")
            addLog("⚠️ El usuario '${user.usuario}' se mantiene en la app.")
            notifHelper.showError("Excepción al borrar '${user.usuario}': $exMsg", "🚨 SDMX: Error Inesperado")
            NtfyManager.sendPushNotification(
                context = context,
                title = "🚨 SDMX: Error Inesperado",
                body = "❌ Excepción al intentar eliminar a '${user.usuario}': $exMsg.\nEl usuario no se borró de la app para reintento.",
                tags = "warning,x",
                priority = "urgent"
            )
        } finally {
            _isLoading.value = false
        }
    }

    fun updateUser(oldUser: UserModel, newUser: UserModel) = viewModelScope.launch {
        _isLoading.value = true
        addLog("Modificando usuario: ${oldUser.usuario} -> ${newUser.usuario}...")
        try {
            val sdUser = userSdmx.value
            val sdPass = passSdmx.value
            if (!sdUser.isNullOrEmpty() && !sdPass.isNullOrEmpty()) {
                if (api.login(getApplication(), sdUser, sdPass)) {
                    if (oldUser.id.isNotEmpty()) {
                        api.deleteLine(getApplication(), oldUser.id)
                        addLog("🗑️ Eliminada línea anterior en panel: ${oldUser.usuario}")
                    }
                    val createOk = api.createLine(getApplication(), newUser.usuario, newUser.password, newUser.vencimiento, newUser.adultos)
                    if (createOk.isSuccess) {
                        delay(1500)
                        val ids = api.getTableIds(getApplication())
                        val newId = ids[newUser.usuario] ?: ""
                        val finalUser = newUser.copy(id = newId)
                        
                        val current = users.value.toMutableList()
                        val idx = current.indexOfFirst { it.usuario == oldUser.usuario }
                        if (idx != -1) {
                            current[idx] = finalUser
                        } else {
                            current.add(finalUser)
                        }
                        db.saveUsers(current)
                        addLog("✅ Modificado: ${newUser.usuario} | nuevo id: $newId | vence: ${newUser.vencimiento} | adultos: ${newUser.adultos}")
                    } else {
                        val err = createOk.exceptionOrNull()?.message ?: "Error desconocido"
                        addLog("❌ Error al crear nueva línea en panel: $err")
                        val current = users.value.toMutableList()
                        val idx = current.indexOfFirst { it.usuario == oldUser.usuario }
                        if (idx != -1) {
                            current[idx] = newUser
                        }
                        db.saveUsers(current)
                    }
                } else {
                    addLog("⚠️ Login fallido. Guardado solo localmente.")
                    val current = users.value.toMutableList()
                    val idx = current.indexOfFirst { it.usuario == oldUser.usuario }
                    if (idx != -1) {
                        current[idx] = newUser
                    }
                    db.saveUsers(current)
                }
            } else {
                addLog("⚠️ Credenciales no configuradas. Guardado solo localmente.")
                val current = users.value.toMutableList()
                val idx = current.indexOfFirst { it.usuario == oldUser.usuario }
                if (idx != -1) {
                    current[idx] = newUser
                }
                db.saveUsers(current)
            }
        } catch (e: Exception) {
            addLog("Error al modificar usuario: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    fun purgeTestUsers() = viewModelScope.launch {
        if (_isLoading.value) return@launch
        _isLoading.value = true
        addLog("🧹 [Depurar] Buscando cuentas que comiencen con 'Test'...")
        try {
            val user = userSdmx.value
            val pass = passSdmx.value
            if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
                addLog("❌ [Depurar] Error: Credenciales SDMX no configuradas.")
                return@launch
            }

            if (!api.login(getApplication(), user, pass)) {
                addLog("❌ [Depurar] Error: No se pudo iniciar sesión en el panel SDMX.")
                return@launch
            }

            addLog("🔎 [Depurar] Obteniendo lista de cuentas del panel...")
            val allRows = api.getTableRows(getApplication())

            val testUsers = allRows.filter { (testId, testUsername) ->
                testUsername.trim().startsWith("Test", ignoreCase = true)
            }

            if (testUsers.isEmpty()) {
                addLog("ℹ️ [Depurar] No se encontraron cuentas que comiencen con 'Test'.")
            } else {
                val namesList = testUsers.map { it.second }.distinct().joinToString(", ")
                addLog("⚠️ [Depurar] Cuentas 'Test' detectadas (${testUsers.size}): $namesList")

                var deletedCount = 0
                for ((testId, testUsername) in testUsers) {
                    if (testId.isNotEmpty()) {
                        addLog("🗑️ [Depurar] Eliminando '$testUsername' (ID: $testId)...")
                        val ok = api.deleteLine(getApplication(), testId)
                        if (ok) {
                            addLog("✅ [Depurar] Cuenta '$testUsername' eliminada con éxito.")
                            deletedCount++
                        } else {
                            addLog("❌ [Depurar] Error al eliminar '$testUsername' (ID: $testId).")
                        }
                        delay(500)
                    }
                }
                addLog("🎉 [Depurar] Proceso finalizado. $deletedCount de ${testUsers.size} cuentas 'Test' eliminadas.")

                val testNamesSet = testUsers.map { it.second.trim().lowercase() }.toSet()
                val currentLocalUsers = users.value.toMutableList()
                val removed = currentLocalUsers.removeAll { it.usuario.trim().lowercase() in testNamesSet }
                if (removed) {
                    db.saveUsers(currentLocalUsers)
                    addLog("🧹 [Depurar] Cuentas removidas también de la base de datos local.")
                }
            }
        } catch (e: Exception) {
            addLog("❌ [Depurar] Error inesperado: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    fun saveNtfyTopic(topic: String) {
        viewModelScope.launch {
            prefs.saveNtfyTopic(topic)
            addLog("⚙️ [Ntfy.sh] Canal de notificaciones actualizado a: '${topic.ifEmpty { PreferencesManager.DEFAULT_NTFY_TOPIC }}'")
        }
    }

    fun sendTestNtfyPush() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                addLog("📲 [Ntfy.sh] Enviando notificación de prueba a https://ntfy.sh/${ntfyTopic.value}...")
                val success = com.example.notifications.NtfyManager.sendExecutionReport(
                    context = getApplication(),
                    isSuccess = true,
                    summaryTitle = "Prueba de Conexión Exitosa",
                    summaryDetails = "Este es un mensaje de prueba desde tu TV Box / Dispositivo Android para verificar que las alertas push funcionan correctamente.",
                    recentLogs = LogManager.getLogs(getApplication())
                )
                if (success) {
                    addLog("✅ [Ntfy.sh] Notificación de prueba enviada con éxito. Revisa https://ntfy.sh/${ntfyTopic.value}")
                } else {
                    addLog("❌ [Ntfy.sh] No se pudo enviar la notificación de prueba.")
                }
            } catch (e: Exception) {
                addLog("❌ [Ntfy.sh] Error en prueba: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
