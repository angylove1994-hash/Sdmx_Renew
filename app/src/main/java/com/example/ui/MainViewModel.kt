package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppLocks
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
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val db = LocalDatabase.get(application)
    private val api = SdmxApiService()

    val userSdmx = prefs.userSdmx.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val passSdmx = prefs.passSdmx.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val intervalHours = prefs.intervalHours.stateIn(viewModelScope, SharingStarted.Eagerly, "24")
    val isAggressiveMode = prefs.isAggressiveMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val lastExecutionTime = prefs.lastExecutionTime.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val nextExecutionTime = prefs.nextExecutionTime.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val ntfyTopic = prefs.ntfyTopic.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_NTFY_TOPIC)
    val ntfyBaseUrl = prefs.ntfyBaseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_NTFY_BASE_URL)
    val linkEnabled = prefs.linkEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_LINK_ENABLED)
    val linkTopicCmd = prefs.linkTopicCmd.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_LINK_TOPIC_CMD)
    val linkTopicResp = prefs.linkTopicResp.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_LINK_TOPIC_RESP)
    val linkSecret = prefs.linkSecret.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val linkToken = prefs.linkToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    
    val users = db.users
    val hasDatabaseError = db.hasLoadError

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
            AppLocks.data.withLock {
                db.addUser(newUser)
            }
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
        if (_isLoading.value) return@launch
        _isLoading.value = true
        val context = getApplication<Application>()
        val notifHelper = NotificationHelper(context)

        addLog("📥 [Cargar Base] =========================================")
        addLog("📥 [Cargar Base] Iniciando importación y carga de ${newUsers.size} cuentas...")
        try {
            var sdUser = userSdmx.value?.trim() ?: ""
            var sdPass = passSdmx.value?.trim() ?: ""
            if (sdUser.isEmpty() || sdPass.isEmpty()) {
                val (syncUser, syncPass) = PreferencesManager.getSyncCredentials(context)
                sdUser = syncUser.trim()
                sdPass = syncPass.trim()
            }

            if (sdUser.isEmpty() || sdPass.isEmpty()) {
                val errorMsg = "Credenciales de administrador SDMX no configuradas en Ajustes."
                addLog("❌ [Cargar Base] $errorMsg")
                notifHelper.showError("Credenciales de administrador SDMX no configuradas.", "🚨 SDMX: Carga Cancelada")
                NtfyManager.sendPushNotification(
                    context = context,
                    title = "🚨 SDMX: Carga de BD Cancelada",
                    body = "❌ En este momento no se puede cargar la base de datos.\n\n🔍 Motivo: Credenciales de administrador SDMX no configuradas.",
                    tags = "warning,x",
                    priority = "urgent"
                )
                return@launch
            }

            // Paso 1: Autenticación en el panel SDMX (igual que en depurar y en agregar usuario)
            addLog("🔐 [Cargar Base] Paso 1/3: Autenticando en el panel SDMX con usuario '$sdUser'...")
            val loginOk = api.login(context, sdUser, sdPass)
            if (!loginOk) {
                val errorMsg = "Fallo de inicio de sesión en panel SDMX. Revisa usuario y contraseña."
                addLog("❌ [Cargar Base] $errorMsg")
                notifHelper.showError("No se pudo iniciar sesión en el panel SDMX.", "🚨 SDMX: Falló Login")
                NtfyManager.sendPushNotification(
                    context = context,
                    title = "🚨 SDMX: Error de Login",
                    body = "❌ Fallo de inicio de sesión en el panel SDMX para '$sdUser'. La base de datos no fue modificada.",
                    tags = "warning,x",
                    priority = "urgent"
                )
                return@launch
            }
            addLog("✅ [Cargar Base] Sesión iniciada con éxito en el panel SDMX.")

            // Paso 2: Consultar la tabla de cuentas activas en el panel para vincular IDs (igual que en depurar)
            addLog("🔎 [Cargar Base] Paso 2/3: Consultando lista de cuentas en el panel para vincular IDs...")
            val currentTableRows = api.getTableRows(context)
            addLog("📋 [Cargar Base] Se detectaron ${currentTableRows.size} cuentas en el panel SDMX.")
            val tableIdMap = currentTableRows.associate { it.second.trim().lowercase() to it.first }

            var matchedIdsCount = 0
            val syncedUsers = newUsers.map { importedUser ->
                val targetUsername = importedUser.usuario.trim()
                val userKey = targetUsername.lowercase()
                val liveId = tableIdMap[userKey] ?: (if (importedUser.id.isNotBlank()) importedUser.id.trim() else "")
                if (tableIdMap.containsKey(userKey)) {
                    matchedIdsCount++
                    addLog("🔗 [Cargar Base] '$targetUsername' vinculada con ID de panel #$liveId")
                    importedUser.copy(id = liveId)
                } else if (liveId.isNotEmpty()) {
                    addLog("ℹ️ [Cargar Base] '$targetUsername' conserva ID previo #$liveId (no encontrada en tabla)")
                    importedUser.copy(id = liveId)
                } else {
                    addLog("ℹ️ [Cargar Base] '$targetUsername' no existe actualmente en el panel (se creará de cero)")
                    importedUser.copy(id = "")
                }
            }

            // Guardar inmediatamente en la base de datos local protegida con AppLocks.data
            AppLocks.data.withLock {
                db.saveUsers(syncedUsers)
            }
            addLog("💾 [Cargar Base] Base de datos local guardada: ${syncedUsers.size} cuentas listas ($matchedIdsCount con ID de panel).")

            val hours = intervalHours.value.toIntOrNull() ?: 24
            SdmxAlarmScheduler.scheduleNextExactAlarm(context, hours)

            // Paso 3: Proceso de eliminación de versión previa y recreación con vencimiento
            addLog("🚀 [Cargar Base] Paso 3/3: Recreando y sincronizando las ${syncedUsers.size} cuentas en SDMX...")
            notifHelper.showSuccess("Base de datos cargada. Renovando ${syncedUsers.size} usuarios en SDMX...", "📥 SDMX: BD Importada")

            var renewedSuccessCount = 0
            var renewedFailCount = 0
            val updatedUserList = syncedUsers.toMutableList()

            for ((index, userToRenew) in syncedUsers.withIndex()) {
                val stepNum = index + 1
                val targetUsername = userToRenew.usuario.trim()
                val targetPass = userToRenew.password.ifEmpty { "1234" }
                val targetExp = userToRenew.vencimiento.ifEmpty {
                    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                }

                addLog("⚡ [Proceso $stepNum/${syncedUsers.size}] Gestionando '$targetUsername'...")

                // 3.1 Eliminar versión previa si tiene ID
                val lineId = if (userToRenew.id.isNotEmpty()) userToRenew.id else tableIdMap[targetUsername.lowercase()]
                if (!lineId.isNullOrEmpty()) {
                    addLog("🗑️ [$stepNum/${syncedUsers.size}] Eliminando versión anterior de '$targetUsername' (ID #$lineId)...")
                    val delOk = api.deleteLine(context, lineId)
                    if (delOk) {
                        addLog("   ↳ ✅ Línea previa #$lineId eliminada correctamente de SDMX.")
                    } else {
                        addLog("   ↳ ⚠️ No se pudo eliminar línea #$lineId (puede haber sido borrada o expirada).")
                    }
                    delay(800)
                } else {
                    addLog("ℹ️ [$stepNum/${syncedUsers.size}] '$targetUsername' no tenía ID previo. Procediendo a crear...")
                }

                // 3.2 Crear línea nueva
                addLog("✨ [$stepNum/${syncedUsers.size}] Creando '$targetUsername' en SDMX (vence: $targetExp, adultos: ${userToRenew.adultos})...")
                val createResult = api.createLine(
                    context = context,
                    username = targetUsername,
                    pass = targetPass,
                    expDate = targetExp,
                    adultos = userToRenew.adultos
                )

                if (createResult.isSuccess) {
                    renewedSuccessCount++
                    addLog("   ↳ ✅ '$targetUsername' creado/renovado con éxito en SDMX.")
                    
                    // Pausa para que el servidor indexe la línea
                    delay(1200)
                    val freshRows = api.getTableRows(context)
                    val newMatch = freshRows.find { it.second.trim().equals(targetUsername, ignoreCase = true) }
                    val newId = newMatch?.first ?: ""
                    if (newId.isNotEmpty()) {
                        addLog("   ↳ 🆔 Nuevo ID asignado: #$newId")
                        val idx = updatedUserList.indexOfFirst { it.usuario.equals(targetUsername, ignoreCase = true) }
                        if (idx != -1) {
                            updatedUserList[idx] = updatedUserList[idx].copy(id = newId)
                            AppLocks.data.withLock {
                                db.saveUsers(updatedUserList)
                            }
                        }
                    } else {
                        addLog("   ↳ ⚠️ Nuevo ID para '$targetUsername' aún no visible en tabla (se actualizará al final).")
                    }
                } else {
                    renewedFailCount++
                    val errorMsg = createResult.exceptionOrNull()?.message ?: "Error desconocido"
                    addLog("   ↳ ❌ Error al crear '$targetUsername': $errorMsg")
                }

                delay(1000)
            }

            // Verificación final de IDs en SDMX para asignar los que faltaron
            addLog("🔎 [Cargar Base] Verificación final de IDs en SDMX...")
            val finalIds = api.getTableIds(context)
            var finalAssignedCount = 0
            val finalizedUsers = updatedUserList.map { u ->
                val newId = finalIds[u.usuario.trim().lowercase()] ?: finalIds[u.usuario.trim()]
                if (!newId.isNullOrEmpty()) {
                    finalAssignedCount++
                    u.copy(id = newId)
                } else {
                    u
                }
            }

            AppLocks.data.withLock {
                db.saveUsers(finalizedUsers)
            }
            addLog("🎉 [Cargar Base] Proceso completado: $renewedSuccessCount cuentas creadas/renovadas con éxito, $renewedFailCount con error ($finalAssignedCount IDs asignados).")
            addLog("📥 [Cargar Base] =========================================")

            val summaryMsg = "Carga y renovación completada: $renewedSuccessCount de ${syncedUsers.size} cuentas listas en SDMX."
            notifHelper.showSuccess(summaryMsg, "✅ SDMX: Carga Completa")

            try {
                NtfyManager.sendExecutionReport(
                    context = context,
                    isSuccess = (renewedFailCount == 0),
                    summaryTitle = "Carga y Renovación JSON ($renewedSuccessCount/${syncedUsers.size})",
                    summaryDetails = summaryMsg,
                    recentLogs = LogManager.getLogs(context)
                )
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        } catch (e: Exception) {
            val exMsg = e.message ?: "Excepción desconocida"
            addLog("❌ [Cargar Base] Error durante la carga: $exMsg")
            notifHelper.showError("Error al cargar BD: $exMsg", "🚨 SDMX: Error de Carga")
            NtfyManager.sendPushNotification(
                context = context,
                title = "🚨 SDMX: Error de Importación",
                body = "❌ Excepción durante la carga JSON:\n\n$exMsg",
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
                AppLocks.data.withLock {
                    val current = users.value.toMutableList()
                    current.removeAll { it.usuario.equals(user.usuario, ignoreCase = true) || (it.id.isNotEmpty() && it.id == user.id) }
                    db.saveUsers(current)
                }

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
                        AppLocks.data.withLock {
                            db.saveUsers(current)
                        }
                        addLog("✅ Modificado: ${newUser.usuario} | nuevo id: $newId | vence: ${newUser.vencimiento} | adultos: ${newUser.adultos}")
                    } else {
                        val err = createOk.exceptionOrNull()?.message ?: "Error desconocido"
                        addLog("❌ Error al crear nueva línea en panel: $err")
                        val current = users.value.toMutableList()
                        val idx = current.indexOfFirst { it.usuario == oldUser.usuario }
                        if (idx != -1) {
                            current[idx] = newUser
                        }
                        AppLocks.data.withLock {
                            db.saveUsers(current)
                        }
                    }
                } else {
                    addLog("⚠️ Login fallido. Guardado solo localmente.")
                    val current = users.value.toMutableList()
                    val idx = current.indexOfFirst { it.usuario == oldUser.usuario }
                    if (idx != -1) {
                        current[idx] = newUser
                    }
                    AppLocks.data.withLock {
                        db.saveUsers(current)
                    }
                }
            } else {
                addLog("⚠️ Credenciales no configuradas. Guardado solo localmente.")
                val current = users.value.toMutableList()
                val idx = current.indexOfFirst { it.usuario == oldUser.usuario }
                if (idx != -1) {
                    current[idx] = newUser
                }
                AppLocks.data.withLock {
                    db.saveUsers(current)
                }
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
                    AppLocks.data.withLock {
                        db.saveUsers(currentLocalUsers)
                    }
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
            addLog("⚙️ [Ntfy] Canal de notificaciones actualizado a: '${topic.ifEmpty { PreferencesManager.DEFAULT_NTFY_TOPIC }}'")
        }
    }

    fun saveAllNtfyConfig(
        reportTopic: String,
        baseUrl: String,
        linkEnabled: Boolean,
        cmdTopic: String,
        respTopic: String,
        secret: String,
        token: String
    ) {
        viewModelScope.launch {
            prefs.saveNtfyTopic(reportTopic)
            prefs.saveLinkConfig(linkEnabled, baseUrl, cmdTopic, respTopic, secret, token)
            addLog("⚙️ [Ntfy] Configuración guardada. Servidor: $baseUrl | Enlace bot: ${if (linkEnabled) "🟢 Activado" else "🔴 Desactivado"}")
        }
    }

    suspend fun sendTestLinkPush(targetBaseUrl: String, respTopic: String, token: String): Boolean {
        addLog("📲 [Enlace Bot] Enviando push de prueba al canal RESP '$respTopic'...")
        val ok = com.example.notifications.NtfyManager.publish(
            context = getApplication(),
            topic = respTopic,
            message = "🔔 [SDMX RENEW] Mensaje de prueba del enlace app-bot en $respTopic",
            title = "Prueba de Enlace RESP",
            priority = 3
        )
        if (ok) {
            addLog("✅ [Enlace Bot] Push de prueba enviado exitosamente a '$respTopic'")
        } else {
            addLog("❌ [Enlace Bot] Falló el push de prueba a '$respTopic'")
        }
        return ok
    }

    fun sendTestNtfyPush() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val baseUrl = prefs.ntfyBaseUrl.stateIn(viewModelScope).value
                addLog("📲 [Ntfy] Enviando notificación de prueba a $baseUrl/${ntfyTopic.value}...")
                val success = com.example.notifications.NtfyManager.sendExecutionReport(
                    context = getApplication(),
                    isSuccess = true,
                    summaryTitle = "Prueba de Conexión Exitosa",
                    summaryDetails = "Este es un mensaje de prueba desde tu TV Box / Dispositivo Android para verificar que las alertas push funcionan correctamente.",
                    recentLogs = LogManager.getLogs(getApplication())
                )
                if (success) {
                    addLog("✅ [Ntfy] Notificación de prueba enviada con éxito a ${ntfyTopic.value}")
                } else {
                    addLog("❌ [Ntfy] No se pudo enviar la notificación de prueba.")
                }
            } catch (e: Exception) {
                addLog("❌ [Ntfy] Error en prueba: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
