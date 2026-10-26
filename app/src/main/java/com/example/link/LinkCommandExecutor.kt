package com.example.link

import android.content.Context
import com.example.data.AppLocks
import com.example.data.LocalDatabase
import com.example.data.LogManager
import com.example.data.PreferencesManager
import com.example.data.UserModel
import com.example.network.SdmxApiService
import com.example.notifications.NtfyManager
import com.example.worker.SdmxExecutionEngine
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

object LinkCommandExecutor {

    private val rateLimiter = RateLimiter(maxPerMinute = 30)
    private val api = SdmxApiService()
    private val gson = Gson()

    /**
     * Procesa un mensaje de comando JSON entrante, valida su firma, idempotencia, rate limit
     * y ejecuta la acción correspondiente respetando la jerarquía de locks.
     *
     * Orden de locks: SdmxExecutionEngine.executionMutex -> AppLocks.data -> LocalDatabase.mutex
     */
    suspend fun handleMessage(context: Context, rawMessage: String): LinkEnvelope? = withContext(Dispatchers.IO) {
        val envelope = LinkEnvelope.fromJson(rawMessage.trim()) ?: return@withContext null
        if (envelope.cmd.isNullOrBlank()) {
            // No es un sobre de comando para la app
            return@withContext null
        }

        val secret = PreferencesManager.getLinkSecret(context)
        val respTopic = PreferencesManager.getLinkTopicResp(context)
        val processedStore = ProcessedStore(context)

        // 1. Idempotencia: Si ya fue procesado, reenviar respuesta guardada
        val cached = processedStore.get(envelope.id)
        if (cached != null) {
            LogManager.addLog(context, "🔁 [Enlace Bot] Comando repetido '${envelope.cmd}' (ID ${envelope.id}). Reenviando respuesta en caché.")
            NtfyManager.publish(context, respTopic, cached.toJson(), priority = 3)
            return@withContext cached
        }

        // 2. Verificación de firma HMAC-SHA256
        if (!LinkProtocol.verify(envelope, secret)) {
            val resp = LinkProtocol.buildResponse(
                re = envelope.id,
                ok = false,
                code = LinkCodes.BAD_SIGNATURE,
                msg = "Firma HMAC-SHA256 inválida o secreta incorrecta",
                data = null,
                secret = secret
            )
            processedStore.put(envelope.id, resp)
            NtfyManager.publish(context, respTopic, resp.toJson(), priority = 3)
            LogManager.addLog(context, "❌ [Enlace Bot] Firma rechazada para comando '${envelope.cmd}' (ID ${envelope.id})")
            return@withContext resp
        }

        // 3. Verificación de frescura / TTL
        if (!LinkProtocol.isFresh(envelope.cmd, envelope.ts)) {
            val resp = LinkProtocol.buildResponse(
                re = envelope.id,
                ok = false,
                code = LinkCodes.EXPIRED,
                msg = "Comando expirado por límite de tiempo (TTL)",
                data = null,
                secret = secret
            )
            processedStore.put(envelope.id, resp)
            NtfyManager.publish(context, respTopic, resp.toJson(), priority = 3)
            LogManager.addLog(context, "⏳ [Enlace Bot] Comando '${envelope.cmd}' expirado (ts=${envelope.ts})")
            return@withContext resp
        }

        // 4. Rate Limiter (máximo 30 comandos de escritura por minuto)
        val isWriteCommand = envelope.cmd in listOf("ADD_USER", "EXTEND_USER", "DELETE_USER")
        if (isWriteCommand && !rateLimiter.tryAcquire()) {
            val resp = LinkProtocol.buildResponse(
                re = envelope.id,
                ok = false,
                code = LinkCodes.RATE_LIMITED,
                msg = "Límite de tasa excedido (máximo 30 comandos por minuto)",
                data = null,
                secret = secret
            )
            processedStore.put(envelope.id, resp)
            NtfyManager.publish(context, respTopic, resp.toJson(), priority = 3)
            LogManager.addLog(context, "⚠️ [Enlace Bot] Comando '${envelope.cmd}' bloqueado por RATE_LIMITED")
            return@withContext resp
        }

        // 5. Verificación de integridad de la base de datos local
        val db = LocalDatabase.get(context)
        if (isWriteCommand && db.hasLoadError.value) {
            val resp = LinkProtocol.buildResponse(
                re = envelope.id,
                ok = false,
                code = LinkCodes.INTERNAL,
                msg = "Base de datos local dañada; operaciones de escritura suspendidas",
                data = null,
                secret = secret
            )
            processedStore.put(envelope.id, resp)
            NtfyManager.publish(context, respTopic, resp.toJson(), priority = 3)
            LogManager.addLog(context, "🛑 [Enlace Bot] Escritura suspendida: base de datos local con error de carga.")
            return@withContext resp
        }

        // 6. Ejecución del comando
        LogManager.addLog(context, "⚡ [Enlace Bot] Procesando '${envelope.cmd}' (ID ${envelope.id})...")
        val response = when (envelope.cmd) {
            "PING" -> executePing(context, envelope, secret, db)
            "LIST_USERS" -> executeListUsers(envelope, secret, db)
            "ADD_USER" -> executeAddUser(context, envelope, secret, db)
            "EXTEND_USER" -> executeExtendUser(context, envelope, secret, db)
            "DELETE_USER" -> executeDeleteUser(context, envelope, secret, db)
            else -> {
                LinkProtocol.buildResponse(
                    re = envelope.id,
                    ok = false,
                    code = LinkCodes.BAD_REQUEST,
                    msg = "Comando desconocido: ${envelope.cmd}",
                    data = null,
                    secret = secret
                )
            }
        }

        // 7. Guardar en store para idempotencia y enviar por NTFY
        processedStore.put(envelope.id, response)
        NtfyManager.publish(context, respTopic, response.toJson(), priority = 3)
        return@withContext response
    }

    private suspend fun executePing(
        context: Context,
        envelope: LinkEnvelope,
        secret: String,
        db: LocalDatabase
    ): LinkEnvelope {
        val users = db.getUsers()
        val vigentesCount = users.count { it.isVigente() }
        val lastRun = PreferencesManager.getSyncLastRunTime(context)
        val nextRun = PreferencesManager.getSyncNextRunTime(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val data = mapOf(
            "app" to "Sdmx_Renew",
            "usuarios_total" to users.size,
            "vigentes" to vigentesCount,
            "ultimo_ciclo" to if (lastRun > 0) sdf.format(Date(lastRun)) else "Nunca",
            "proximo_ciclo" to if (nextRun > 0) sdf.format(Date(nextRun)) else "Pendiente",
            "ciclo_en_curso" to SdmxExecutionEngine.executionMutex.isLocked
        )

        return LinkProtocol.buildResponse(
            re = envelope.id,
            ok = true,
            code = LinkCodes.OK,
            msg = "Pong",
            data = data,
            secret = secret
        )
    }

    private suspend fun executeListUsers(
        envelope: LinkEnvelope,
        secret: String,
        db: LocalDatabase
    ): LinkEnvelope {
        val users = db.getUsers()
        val payloadObj = try {
            JsonParser.parseString(envelope.payload).asJsonObject
        } catch (_: Exception) {
            JsonObject()
        }

        val pagina = payloadObj.get("pagina")?.asInt ?: 1
        val limite = (payloadObj.get("limite")?.asInt ?: 50).coerceIn(1, 200)
        val total = users.size
        val paginas = maxOf(1, ceil(total / limite.toDouble()).toInt())
        val paginaActual = pagina.coerceIn(1, paginas)
        val startIndex = (paginaActual - 1) * limite

        val items = users.drop(startIndex).take(limite).map { u ->
            mapOf(
                "id" to u.id,
                "usuario" to u.usuario,
                "password" to u.password,
                "vence" to u.vencimiento,
                "adultos" to u.adultos,
                "estado" to u.getEstado(),
                "dias_restantes" to u.getDiasRestantes(),
                "origen" to u.origen,
                "telefono" to (u.telefono ?: ""),
                "creado_por" to (u.creado_por ?: "")
            )
        }

        val data = mapOf(
            "pagina" to paginaActual,
            "paginas" to paginas,
            "total" to total,
            "items" to items
        )

        return LinkProtocol.buildResponse(
            re = envelope.id,
            ok = true,
            code = LinkCodes.OK,
            msg = "Usuarios listados",
            data = data,
            secret = secret
        )
    }

    private suspend fun executeAddUser(
        context: Context,
        envelope: LinkEnvelope,
        secret: String,
        db: LocalDatabase
    ): LinkEnvelope {
        val payloadObj = try {
            JsonParser.parseString(envelope.payload).asJsonObject
        } catch (e: Exception) {
            return LinkProtocol.buildResponse(envelope.id, false, LinkCodes.BAD_REQUEST, "Payload JSON inválido", null, secret)
        }

        val usuario = payloadObj.get("usuario")?.asString?.trim() ?: ""
        val password = payloadObj.get("password")?.asString ?: ""
        val vence = payloadObj.get("vence")?.asString?.trim() ?: ""
        val modo = payloadObj.get("modo")?.asString ?: "crear_en_panel"
        val adultos = payloadObj.get("adultos")?.asBoolean ?: false
        val origen = payloadObj.get("origen")?.asString?.trim()?.ifEmpty { "manual" } ?: "manual"
        val telefono = payloadObj.get("telefono")?.asString?.trim()
        val creado_por = payloadObj.get("creado_por")?.asString?.trim()
        val reemplaza = payloadObj.get("reemplaza")?.asString?.trim()

        // Validaciones del protocolo
        val userVal = LinkProtocol.validateUsername(usuario, origen)
        if (!userVal.isValid) {
            return LinkProtocol.buildResponse(envelope.id, false, userVal.errorCode ?: LinkCodes.INVALID_USERNAME, userVal.errorMessage ?: "Usuario inválido", null, secret)
        }

        val passVal = LinkProtocol.validatePassword(password)
        if (!passVal.isValid) {
            return LinkProtocol.buildResponse(envelope.id, false, passVal.errorCode ?: LinkCodes.INVALID_PASSWORD, passVal.errorMessage ?: "Contraseña inválida", null, secret)
        }

        val dateVal = LinkProtocol.validateDate(vence)
        if (!dateVal.isValid) {
            return LinkProtocol.buildResponse(envelope.id, false, dateVal.errorCode ?: LinkCodes.INVALID_DATE, dateVal.errorMessage ?: "Fecha inválida", null, secret)
        }

        // Adquisición estricta de locks: executionMutex -> AppLocks.data -> LocalDatabase.mutex
        return SdmxExecutionEngine.executionMutex.withLock {
            AppLocks.data.withLock {
                // Verificar si ya existe en base de datos local
                if (db.findUser(usuario) != null) {
                    return@withLock LinkProtocol.buildResponse(
                        envelope.id,
                        false,
                        LinkCodes.DUPLICATE_USER,
                        "El usuario '$usuario' ya existe en la base de datos local",
                        null,
                        secret
                    )
                }

                // Si reemplaza a una cuenta previa (ej. Test00XX proveniente de prueba NFY)
                if (!reemplaza.isNullOrBlank()) {
                    LogManager.addLog(context, "🔄 [Enlace Bot] Reemplazando '$reemplaza' con '$usuario'...")
                    val (adminUser, adminPass) = PreferencesManager.getSyncCredentials(context)
                    if (adminUser.isNotEmpty() && adminPass.isNotEmpty()) {
                        api.deleteUserFromPanel(context, adminUser, adminPass, reemplaza)
                    }
                    db.removeUser(reemplaza)
                }

                var lineId = ""
                if (modo == "crear_en_panel") {
                    val (adminUser, adminPass) = PreferencesManager.getSyncCredentials(context)
                    if (adminUser.isEmpty() || adminPass.isEmpty()) {
                        return@withLock LinkProtocol.buildResponse(
                            envelope.id,
                            false,
                            LinkCodes.PANEL_LOGIN_FAILED,
                            "Credenciales de administrador SDMX no configuradas",
                            null,
                            secret
                        )
                    }

                    val loginOk = api.login(context, adminUser, adminPass)
                    if (!loginOk) {
                        return@withLock LinkProtocol.buildResponse(
                            envelope.id,
                            false,
                            LinkCodes.PANEL_LOGIN_FAILED,
                            "Fallo de inicio de sesión en el panel SDMX",
                            null,
                            secret
                        )
                    }

                    val createRes = api.createLine(context, usuario, password, vence, adultos)
                    if (createRes.isFailure) {
                        val errMsg = createRes.exceptionOrNull()?.message ?: "Panel rechazó la creación"
                        return@withLock LinkProtocol.buildResponse(
                            envelope.id,
                            false,
                            LinkCodes.PANEL_CREATE_FAILED,
                            "Error al crear en panel SDMX: $errMsg",
                            null,
                            secret
                        )
                    }

                    delay(1200)
                    val tableIds = api.getTableIds(context)
                    lineId = tableIds[usuario.lowercase()] ?: tableIds[usuario] ?: ""
                } else if (modo == "registrar_existente") {
                    val tableIds = api.getTableIds(context)
                    lineId = tableIds[usuario.lowercase()] ?: tableIds[usuario] ?: ""
                }

                // Crear UserModel con los campos obligatorios: origen, telefono, creado_por y creadoEn (now)
                val newUser = UserModel(
                    id = lineId,
                    usuario = usuario,
                    password = password,
                    vencimiento = vence,
                    adultos = adultos,
                    origen = origen,
                    telefono = telefono,
                    creado_por = creado_por,
                    notas = if (!reemplaza.isNullOrBlank()) "Reemplaza a $reemplaza" else null,
                    creadoEn = System.currentTimeMillis()
                )

                // Uso atómico obligatorio de LocalDatabase.addIfAbsent
                val added = db.addIfAbsent(newUser)
                if (!added) {
                    return@withLock LinkProtocol.buildResponse(
                        envelope.id,
                        false,
                        LinkCodes.DUPLICATE_USER,
                        "El usuario '$usuario' ya existía al momento de insertar",
                        null,
                        secret
                    )
                }

                LogManager.addLog(context, "✅ [Enlace Bot] Usuario registrado: $usuario | vence: $vence | origen: $origen")

                val data = mapOf(
                    "id" to newUser.id,
                    "usuario" to newUser.usuario,
                    "vence" to newUser.vencimiento,
                    "estado" to newUser.getEstado(),
                    "ya_existia" to false,
                    "reemplazo" to (!reemplaza.isNullOrBlank())
                )

                LinkProtocol.buildResponse(
                    re = envelope.id,
                    ok = true,
                    code = LinkCodes.OK,
                    msg = "Usuario registrado exitosamente",
                    data = data,
                    secret = secret
                )
            }
        }
    }

    private suspend fun executeExtendUser(
        context: Context,
        envelope: LinkEnvelope,
        secret: String,
        db: LocalDatabase
    ): LinkEnvelope {
        val payloadObj = try {
            JsonParser.parseString(envelope.payload).asJsonObject
        } catch (e: Exception) {
            return LinkProtocol.buildResponse(envelope.id, false, LinkCodes.BAD_REQUEST, "Payload JSON inválido", null, secret)
        }

        val usuario = payloadObj.get("usuario")?.asString?.trim() ?: ""
        val base = payloadObj.get("base")?.asString ?: "desde_vencimiento"
        val dias = payloadObj.get("dias")?.asInt
        val meses = payloadObj.get("meses")?.asInt
        val venceFijo = payloadObj.get("vence")?.asString?.trim()

        // Adquisición estricta de locks: executionMutex -> AppLocks.data -> LocalDatabase.mutex
        return SdmxExecutionEngine.executionMutex.withLock {
            AppLocks.data.withLock {
                val user = db.findUser(usuario)
                if (user == null) {
                    return@withLock LinkProtocol.buildResponse(
                        envelope.id,
                        false,
                        LinkCodes.NOT_FOUND,
                        "Usuario '$usuario' no encontrado",
                        null,
                        secret
                    )
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val vence_anterior = user.vencimiento

                val vence_nueva = when {
                    !venceFijo.isNullOrBlank() -> {
                        val valRes = LinkProtocol.validateDate(venceFijo)
                        if (!valRes.isValid) {
                            return@withLock LinkProtocol.buildResponse(
                                envelope.id,
                                false,
                                valRes.errorCode ?: LinkCodes.INVALID_DATE,
                                valRes.errorMessage ?: "Fecha inválida",
                                null,
                                secret
                            )
                        }
                        venceFijo
                    }
                    base == "desde_hoy" -> {
                        val cal = Calendar.getInstance()
                        if (dias != null && dias > 0) {
                            cal.add(Calendar.DAY_OF_YEAR, dias)
                        } else if (meses != null && meses > 0) {
                            cal.add(Calendar.MONTH, meses)
                        } else {
                            cal.add(Calendar.DAY_OF_YEAR, 30) // Default 30 días
                        }
                        sdf.format(cal.time)
                    }
                    else -> { // "desde_vencimiento"
                        val cal = Calendar.getInstance()
                        try {
                            val parsed = sdf.parse(user.vencimiento.substringBefore("T"))
                            if (parsed != null && !parsed.before(cal.time)) {
                                cal.time = parsed
                            }
                        } catch (_: Exception) {}

                        if (dias != null && dias > 0) {
                            cal.add(Calendar.DAY_OF_YEAR, dias)
                        } else if (meses != null && meses > 0) {
                            cal.add(Calendar.MONTH, meses)
                        } else {
                            cal.add(Calendar.MONTH, 1)
                        }
                        sdf.format(cal.time)
                    }
                }

                // Actualizar usuario en base de datos
                db.atomicUpdate { list ->
                    val idx = list.indexOfFirst { it.usuario.equals(usuario, ignoreCase = true) }
                    if (idx != -1) {
                        list[idx] = list[idx].copy(vencimiento = vence_nueva)
                    }
                }

                LogManager.addLog(context, "📅 [Enlace Bot] Extendido: $usuario ($vence_anterior → $vence_nueva)")

                val data = mapOf(
                    "usuario" to user.usuario,
                    "vence_anterior" to vence_anterior,
                    "vence_nueva" to vence_nueva
                )

                LinkProtocol.buildResponse(
                    re = envelope.id,
                    ok = true,
                    code = LinkCodes.OK,
                    msg = "Vigencia extendida exitosamente",
                    data = data,
                    secret = secret
                )
            }
        }
    }

    private suspend fun executeDeleteUser(
        context: Context,
        envelope: LinkEnvelope,
        secret: String,
        db: LocalDatabase
    ): LinkEnvelope {
        val payloadObj = try {
            JsonParser.parseString(envelope.payload).asJsonObject
        } catch (e: Exception) {
            return LinkProtocol.buildResponse(envelope.id, false, LinkCodes.BAD_REQUEST, "Payload JSON inválido", null, secret)
        }

        val usuario = payloadObj.get("usuario")?.asString?.trim() ?: ""

        // Adquisición estricta de locks: executionMutex -> AppLocks.data -> LocalDatabase.mutex
        return SdmxExecutionEngine.executionMutex.withLock {
            AppLocks.data.withLock {
                val user = db.findUser(usuario)
                if (user == null) {
                    // Contrato: Si no existe -> NOT_FOUND (el bot lo trata como borrado correcto)
                    return@withLock LinkProtocol.buildResponse(
                        envelope.id,
                        false,
                        LinkCodes.NOT_FOUND,
                        "Usuario '$usuario' no encontrado en la app",
                        null,
                        secret
                    )
                }

                // Borrar del panel si las credenciales están configuradas
                val (adminUser, adminPass) = PreferencesManager.getSyncCredentials(context)
                if (adminUser.isNotEmpty() && adminPass.isNotEmpty()) {
                    api.deleteUserFromPanel(context, adminUser, adminPass, user.usuario, user.id)
                }

                db.removeUser(user.usuario)
                LogManager.addLog(context, "🗑️ [Enlace Bot] Usuario eliminado: $usuario")

                val data = mapOf("usuario" to user.usuario)

                LinkProtocol.buildResponse(
                    re = envelope.id,
                    ok = true,
                    code = LinkCodes.OK,
                    msg = "Usuario eliminado exitosamente",
                    data = data,
                    secret = secret
                )
            }
        }
    }
}
