package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocalDatabase
import com.example.data.LogManager
import com.example.data.PreferencesManager
import com.example.data.UserModel
import com.example.network.SdmxApiService
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
        db.saveUsers(newUsers)
        addLog("📥 BD importada: ${newUsers.size} usuarios cargados.")
        val hours = intervalHours.value.toIntOrNull() ?: 24
        SdmxAlarmScheduler.scheduleNextExactAlarm(getApplication(), hours)
    }

    fun deleteUser(user: UserModel) = viewModelScope.launch {
        _isLoading.value = true
        addLog("Eliminando usuario: ${user.usuario}...")
        try {
            if (user.id.isNotEmpty()) {
                val sdUser = userSdmx.value
                val sdPass = passSdmx.value
                if (!sdUser.isNullOrEmpty() && !sdPass.isNullOrEmpty()) {
                    if (api.login(getApplication(), sdUser, sdPass)) {
                        val delOk = api.deleteLine(getApplication(), user.id)
                        addLog("🗑️ Eliminado del panel: ${user.usuario} (Result: $delOk)")
                    } else {
                        addLog("⚠️ No se pudo iniciar sesión para borrar del panel.")
                    }
                }
            }
            
            val current = users.value.toMutableList()
            current.removeAll { it.usuario == user.usuario }
            db.saveUsers(current)
            addLog("✅ Usuario ${user.usuario} eliminado.")
        } catch (e: Exception) {
            addLog("Error al eliminar usuario: ${e.message}")
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

            val testUsers = allRows.filter { (username, _) ->
                username.trim().startsWith("Test", ignoreCase = true)
            }

            if (testUsers.isEmpty()) {
                addLog("ℹ️ [Depurar] No se encontraron cuentas que comiencen con 'Test'.")
            } else {
                val namesList = testUsers.map { it.first }.distinct().joinToString(", ")
                addLog("⚠️ [Depurar] Cuentas 'Test' detectadas (${testUsers.size}): $namesList")

                var deletedCount = 0
                for ((testUsername, testId) in testUsers) {
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

                val testNamesSet = testUsers.map { it.first.trim().lowercase() }.toSet()
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
}
