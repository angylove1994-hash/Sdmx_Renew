package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocalDatabase
import com.example.data.PreferencesManager
import com.example.data.UserModel
import com.example.data.LogManager
import com.example.network.SdmxApiService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.work.WorkManager
import com.example.worker.SdmxWorker
import com.example.notifications.NotificationHelper
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val db = LocalDatabase(application)
    private val api = SdmxApiService()

    val userSdmx = prefs.userSdmx.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val passSdmx = prefs.passSdmx.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val intervalHours = prefs.intervalHours.stateIn(viewModelScope, SharingStarted.Eagerly, "24")
    
    val users = db.users

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        _logs.value = LogManager.getLogs(application)
    }

    fun loadData() {
        db.loadData()
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
    }

    fun saveInterval(hours: String) = viewModelScope.launch {
        prefs.saveInterval(hours)
        addLog("Intervalo configurado a: $hours horas")
    }

    fun runManualCycle() = viewModelScope.launch {
        if (_isLoading.value) return@launch
        _isLoading.value = true
        addLog("🚀 Iniciando ciclo de autorenovación...")

        try {
            val user = userSdmx.value
            val pass = passSdmx.value
            if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
                addLog("❌ Error: Credenciales de administrador SDMX no configuradas.")
                return@launch
            }

            // Paso Previo: Health Check con usuario de prueba Test00777
            val healthCheckOk = api.verifyHealthCheck(getApplication(), user, pass)
            if (!healthCheckOk) {
                val notificationHelper = NotificationHelper(getApplication())
                notificationHelper.showError("Verificación previa fallida para $user. Se reintentará en 1 minuto.")
                SdmxWorker.scheduleRetry(getApplication())
                return@launch
            }

            WorkManager.getInstance(getApplication()).cancelUniqueWork("SdmxRetryWork")

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val hoy = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time

            val allUsers = users.value
            val vigentes = allUsers.filter {
                try {
                    val fechaLimpia = it.vencimiento.trim().substringBefore("T")
                    val fecha = sdf.parse(fechaLimpia)
                    fecha != null && !fecha.before(hoy)
                } catch (e: Exception) { false }
            }

            addLog("📋 Usuarios a procesar: Total: ${allUsers.size} | Vigentes: ${vigentes.size}")

            var procesados = 0
            for (u in vigentes) {
                if (u.id.isNotEmpty()) {
                    val delOk = api.deleteLine(u.id)
                    addLog("🗑️ Eliminado del panel: ${u.usuario} (id: ${u.id}) - Result: $delOk")
                }
                val createOk = api.createLine(u.usuario, u.password, u.vencimiento, u.adultos)
                if (createOk.isSuccess) {
                    addLog("✅ Creado en panel: ${u.usuario}")
                    procesados++
                } else {
                    addLog("❌ Error al crear ${u.usuario}: ${createOk.exceptionOrNull()?.message}")
                }
            }
            
            delay(1500)

            val newIds = api.getTableIds()
            val updatedUsers = allUsers.map { u ->
                if (vigentes.contains(u) && newIds.containsKey(u.usuario)) {
                    val newId = newIds[u.usuario]!!
                    addLog("📝 ID actualizado: ${u.usuario} → $newId")
                    u.copy(id = newId)
                } else u
            }

            db.saveUsers(updatedUsers)
            addLog("🎉 Ciclo completado exitosamente. $procesados usuarios renovados.")
        } catch (e: Exception) {
            addLog("❌ Error inesperado en ciclo: ${e.message}")
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

            val createOk = api.createLine(username, pass, expDateStr, adultos)
            if (createOk.isFailure) {
                val err = createOk.exceptionOrNull()?.message ?: "Desconocido"
                addLog("❌ Error al crear: $err")
                return@launch
            }
            
            delay(1500) // The server might take a moment to reflect the new user in the table

            val ids = api.getTableIds()
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
        } catch (e: Exception) {
            addLog("Error inesperado al agregar usuario: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }
    
    fun replaceUsers(newUsers: List<UserModel>) = viewModelScope.launch {
        db.saveUsers(newUsers)
        addLog("📥 BD importada: ${newUsers.size} usuarios cargados.")
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
                        val delOk = api.deleteLine(user.id)
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
                        api.deleteLine(oldUser.id)
                        addLog("🗑️ Eliminada línea anterior en panel: ${oldUser.usuario}")
                    }
                    val createOk = api.createLine(newUser.usuario, newUser.password, newUser.vencimiento, newUser.adultos)
                    if (createOk.isSuccess) {
                        delay(1500)
                        val ids = api.getTableIds()
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
}
