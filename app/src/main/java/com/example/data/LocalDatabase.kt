package com.example.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LocalDatabase private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: LocalDatabase? = null

        fun get(context: Context): LocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        @Synchronized
        fun resetForTesting() {
            INSTANCE = null
        }
    }

    private val file = File(context.filesDir, "sdmx_users.json")
    private val tempFile = File(context.filesDir, "sdmx_users.json.tmp")
    private val bakFile = File(context.filesDir, "sdmx_users.json.bak")
    private val gson = Gson()
    private val mutex = Mutex()

    private val _users = MutableStateFlow<List<UserModel>>(emptyList())
    val users: StateFlow<List<UserModel>> = _users.asStateFlow()

    private val _hasLoadError = MutableStateFlow(false)
    val hasLoadError: StateFlow<Boolean> = _hasLoadError.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        val listType = object : TypeToken<List<UserModel>>() {}.type

        if (file.exists()) {
            try {
                val json = file.readText()
                val parsed: List<UserModel>? = gson.fromJson(json, listType)
                if (parsed != null) {
                    _users.value = parsed
                    _hasLoadError.value = false
                } else {
                    throw IllegalStateException("El contenido de sdmx_users.json no representa una lista válida de usuarios")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _hasLoadError.value = true
                // Conserva copia .bak del archivo corrupto y registra el error.
                // NO guardes una lista vacía encima: conserva copia .bak y registra el error en LogManager.
                try {
                    file.copyTo(bakFile, overwrite = true)
                } catch (bakEx: Exception) {
                    bakEx.printStackTrace()
                }
                LogManager.addLog(
                    context,
                    "❌ Error al leer sdmx_users.json: ${e.message}. Se preservó sdmx_users.json.bak"
                )
            }
            return
        }

        // Usa .tmp solo si el principal no existe y valida el JSON
        if (tempFile.exists()) {
            try {
                val json = tempFile.readText()
                val parsed: List<UserModel>? = gson.fromJson(json, listType)
                if (parsed != null) {
                    val renamed = tempFile.renameTo(file)
                    if (!renamed) {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    }
                    _users.value = parsed
                    _hasLoadError.value = false
                    LogManager.addLog(
                        context,
                        "⚠️ Base de datos recuperada desde sdmx_users.json.tmp (${parsed.size} usuarios)"
                    )
                    return
                } else {
                    throw IllegalStateException("El contenido de sdmx_users.json.tmp no es una lista válida")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _hasLoadError.value = true
                try {
                    tempFile.copyTo(bakFile, overwrite = true)
                } catch (bakEx: Exception) {
                    bakEx.printStackTrace()
                }
                LogManager.addLog(
                    context,
                    "❌ Error al validar sdmx_users.json.tmp: ${e.message}. Se preservó sdmx_users.json.bak"
                )
            }
        }

        // Si no existe ningún archivo (instalación inicial)
        _users.value = emptyList()
        _hasLoadError.value = false
    }

    private fun writeUsersToFileLocked(newUsers: List<UserModel>) {
        try {
            val json = gson.toJson(newUsers)
            tempFile.writeText(json)
            val renamed = tempFile.renameTo(file)
            if (!renamed) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            _users.value = newUsers.toList()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveUsers(newUsers: List<UserModel>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeUsersToFileLocked(newUsers)
        }
    }

    suspend fun getUsers(): List<UserModel> = mutex.withLock {
        _users.value
    }

    suspend fun findUser(username: String): UserModel? = mutex.withLock {
        _users.value.find { it.usuario.equals(username.trim(), ignoreCase = true) }
    }

    suspend fun findUserById(id: String): UserModel? = mutex.withLock {
        if (id.isEmpty()) null else _users.value.find { it.id == id }
    }

    suspend fun addUser(user: UserModel) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _users.value.toMutableList()
            val idx = current.indexOfFirst { it.usuario.equals(user.usuario.trim(), ignoreCase = true) }
            if (idx != -1) {
                current[idx] = user
            } else {
                current.add(user)
            }
            writeUsersToFileLocked(current)
        }
    }

    suspend fun addIfAbsent(user: UserModel): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _users.value.toMutableList()
            val exists = current.any { it.usuario.equals(user.usuario.trim(), ignoreCase = true) }
            if (exists) {
                false
            } else {
                current.add(user)
                writeUsersToFileLocked(current)
                true
            }
        }
    }

    suspend fun removeUser(username: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _users.value.toMutableList()
            val removed = current.removeAll { it.usuario.equals(username.trim(), ignoreCase = true) }
            if (removed) {
                writeUsersToFileLocked(current)
            }
            removed
        }
    }

    suspend fun updateTableIds(tableIds: Map<String, String>): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _users.value.toMutableList()
            var updatedCount = 0
            val lowerMap = tableIds.mapKeys { it.key.trim().lowercase() }
            for (i in current.indices) {
                val old = current[i]
                val newId = lowerMap[old.usuario.trim().lowercase()] ?: tableIds[old.usuario.trim()]
                if (!newId.isNullOrEmpty() && newId != old.id) {
                    updatedCount++
                    current[i] = old.copy(id = newId)
                }
            }
            if (updatedCount > 0) {
                writeUsersToFileLocked(current)
            }
            updatedCount
        }
    }

    suspend fun atomicUpdate(block: (MutableList<UserModel>) -> Unit): List<UserModel> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _users.value.toMutableList()
            block(current)
            writeUsersToFileLocked(current)
            _users.value
        }
    }

    fun getFile(): File = file
    fun getTempFile(): File = tempFile
    fun getBakFile(): File = bakFile
}
