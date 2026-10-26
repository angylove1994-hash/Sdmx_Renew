package com.example.link

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Almacén en disco de los últimos 1000 IDs de comandos procesados con su respectiva respuesta.
 * Garantiza idempotencia: si un comando con el mismo ID llega nuevamente, la app responde
 * con la respuesta previamente guardada sin reejecutar la lógica.
 *
 * Características:
 * - Capacidad máxima: 1000 elementos (FIFO / orden de inserción).
 * - Persistencia atómica: escritura en archivo temporal y renombramiento atómico.
 * - Concurrencia segura: protegido por Mutex para operaciones coroutine.
 */
class ProcessedStore(private val storeFile: File) {

    constructor(context: Context) : this(File(context.filesDir, DEFAULT_FILE_NAME))
    constructor(baseDir: File, fileName: String = DEFAULT_FILE_NAME) : this(File(baseDir, fileName))

    companion object {
        const val DEFAULT_FILE_NAME = "link_processed_ids.json"
        const val MAX_CAPACITY = 1000
    }

    private val mutex = Mutex()
    private val gson = Gson()

    // Mantiene el orden de inserción
    private val memoryStore = LinkedHashMap<String, LinkEnvelope>()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (!storeFile.exists()) return
        try {
            val json = storeFile.readText(Charsets.UTF_8)
            val mapType = object : TypeToken<LinkedHashMap<String, LinkEnvelope>>() {}.type
            val loaded: LinkedHashMap<String, LinkEnvelope>? = gson.fromJson(json, mapType)
            if (loaded != null) {
                memoryStore.putAll(loaded)
                trimToMaxCapacity()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // En caso de corrupción, se mantiene la memoria vacía
        }
    }

    suspend fun get(id: String): LinkEnvelope? = mutex.withLock {
        memoryStore[id]
    }

    suspend fun has(id: String): Boolean = mutex.withLock {
        memoryStore.containsKey(id)
    }

    suspend fun put(id: String, response: LinkEnvelope) = mutex.withLock {
        memoryStore[id] = response
        trimToMaxCapacity()
        saveToDiskLocked()
    }

    suspend fun size(): Int = mutex.withLock {
        memoryStore.size
    }

    suspend fun clear() = mutex.withLock {
        memoryStore.clear()
        saveToDiskLocked()
    }

    private fun trimToMaxCapacity() {
        while (memoryStore.size > MAX_CAPACITY) {
            val oldestKey = memoryStore.keys.firstOrNull() ?: break
            memoryStore.remove(oldestKey)
        }
    }

    private suspend fun saveToDiskLocked() = withContext(Dispatchers.IO) {
        try {
            val parent = storeFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val tempFile = File(storeFile.parentFile, "${storeFile.name}.tmp")
            val json = gson.toJson(memoryStore)
            tempFile.writeText(json, Charsets.UTF_8)

            if (!tempFile.renameTo(storeFile)) {
                // Si el rename atómico falla por alguna razón del SO, reemplazar
                if (storeFile.exists()) {
                    storeFile.delete()
                }
                tempFile.renameTo(storeFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
