package com.example.link

import java.security.MessageDigest
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class ValidationResult(
    val isValid: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun ok(): ValidationResult = ValidationResult(true)
        fun error(code: String, msg: String): ValidationResult = ValidationResult(false, code, msg)
    }
}

object LinkProtocol {

    /**
     * Calcula la firma HMAC-SHA256 en hexadecimal en minúsculas.
     */
    fun sign(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Construye la cadena canónica a firmar según el tipo de sobre:
     * - Comandos: "v|id|ts|cmd|payload"
     * - Respuestas: "v|id|ts|re|payload"
     */
    fun buildSignableString(envelope: LinkEnvelope): String? {
        return when {
            envelope.cmd != null -> "${envelope.v}|${envelope.id}|${envelope.ts}|${envelope.cmd}|${envelope.payload}"
            envelope.re != null -> "${envelope.v}|${envelope.id}|${envelope.ts}|${envelope.re}|${envelope.payload}"
            else -> null
        }
    }

    /**
     * Verifica la firma de un envelope usando comparación en tiempo constante (MessageDigest.isEqual).
     */
    fun verify(envelope: LinkEnvelope, secret: String): Boolean {
        if (secret.isBlank() || envelope.sig.isBlank()) return false
        val signable = buildSignableString(envelope) ?: return false
        val expectedSig = sign(secret, signable)
        return MessageDigest.isEqual(
            expectedSig.toByteArray(Charsets.UTF_8),
            envelope.sig.lowercase(Locale.US).toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Genera una respuesta completa y firmada para responder a un comando con id [re].
     */
    fun buildResponse(
        re: String,
        ok: Boolean,
        code: String,
        msg: String,
        data: Any? = null,
        secret: String
    ): LinkEnvelope {
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis() / 1000L
        val payloadObj = LinkResponsePayload(ok = ok, code = code, msg = msg, data = data)
        val payloadStr = payloadObj.toJson()
        val signable = "1|$id|$ts|$re|$payloadStr"
        val sig = sign(secret, signable)

        return LinkEnvelope(
            v = 1,
            id = id,
            ts = ts,
            cmd = null,
            re = re,
            payload = payloadStr,
            sig = sig
        )
    }

    /**
     * Comprueba si el comando no ha expirado según el TTL del protocolo:
     * - PING y LIST_USERS: 120 segundos
     * - ADD_USER, EXTEND_USER, DELETE_USER u otros: 24 horas (86400 segundos)
     */
    fun isFresh(
        cmd: String?,
        ts: Long,
        nowSeconds: Long = System.currentTimeMillis() / 1000L
    ): Boolean {
        val diff = abs(nowSeconds - ts)
        val maxTtlSeconds = when (cmd) {
            "PING", "LIST_USERS" -> 120L
            else -> 86400L // 24 horas para operaciones idempotentes de usuario
        }
        return diff <= maxTtlSeconds
    }

    /**
     * Valida el formato de usuario:
     * - Longitud 4-32, caracteres [A-Za-z0-9._-]
     * - Prefijo Test + dígitos reservado para pruebas del bot (solo permitido si origen == "prueba")
     */
    fun validateUsername(usuario: String?, origen: String? = null): ValidationResult {
        if (usuario.isNullOrBlank()) {
            return ValidationResult.error(LinkCodes.INVALID_USERNAME, "El nombre de usuario no puede estar vacío")
        }
        val validCharRegex = Regex("^[A-Za-z0-9._-]{4,32}$")
        if (!validCharRegex.matches(usuario)) {
            return ValidationResult.error(
                LinkCodes.INVALID_USERNAME,
                "Usuario inválido: debe tener entre 4 y 32 caracteres (alfanuméricos, '.', '_', '-')"
            )
        }

        // Prefijo Test + dígitos reservado para pruebas
        val isTestUser = Regex("^Test[0-9]+$", RegexOption.IGNORE_CASE).matches(usuario)
        if (isTestUser && origen != "prueba") {
            return ValidationResult.error(
                LinkCodes.INVALID_USERNAME,
                "El prefijo Test con dígitos está reservado exclusivamente para cuentas de prueba"
            )
        }

        return ValidationResult.ok()
    }

    /**
     * Valida la contraseña:
     * - Longitud 8 a 32 caracteres
     * - Sin espacios
     * - Solo caracteres ASCII imprimibles (0x21 a 0x7E)
     */
    fun validatePassword(password: String?): ValidationResult {
        if (password == null || password.length < 8 || password.length > 32) {
            return ValidationResult.error(
                LinkCodes.INVALID_PASSWORD,
                "Contraseña inválida: debe tener entre 8 y 32 caracteres"
            )
        }
        if (password.contains(' ')) {
            return ValidationResult.error(
                LinkCodes.INVALID_PASSWORD,
                "La contraseña no debe contener espacios en blanco"
            )
        }
        val isAsciiPrintable = password.all { it.code in 0x21..0x7E }
        if (!isAsciiPrintable) {
            return ValidationResult.error(
                LinkCodes.INVALID_PASSWORD,
                "La contraseña contiene caracteres no permitidos (solo ASCII imprimible 0x21-0x7E)"
            )
        }

        return ValidationResult.ok()
    }

    /**
     * Valida la fecha de vencimiento:
     * - Formato: yyyy-MM-dd (Locale.US) estricto (no laxo, ej. 29 de febrero en año no bisiesto falla)
     * - >= hoy (el mismo día del vencimiento todavía se renueva)
     * - <= hoy + 400 días
     */
    fun validateDate(
        vence: String?,
        todayDateString: String? = null,
        maxDaysAhead: Int = 400
    ): ValidationResult {
        if (vence.isNullOrBlank()) {
            return ValidationResult.error(LinkCodes.INVALID_DATE, "La fecha de vencimiento no puede estar vacía")
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }

        val parsedDate: Date = try {
            val d = sdf.parse(vence) ?: return ValidationResult.error(LinkCodes.INVALID_DATE, "Fecha inválida")
            // Asegurar que el string formateado coincida exactamente con la entrada
            if (sdf.format(d) != vence) {
                return ValidationResult.error(LinkCodes.INVALID_DATE, "Fecha inválida o inexistente en el calendario")
            }
            d
        } catch (e: ParseException) {
            return ValidationResult.error(LinkCodes.INVALID_DATE, "Formato de fecha inválido (requerido: yyyy-MM-dd)")
        }

        val todayDate: Date = try {
            if (!todayDateString.isNullOrBlank()) {
                sdf.parse(todayDateString) ?: sdf.parse(sdf.format(Date()))!!
            } else {
                sdf.parse(sdf.format(Date()))!!
            }
        } catch (e: Exception) {
            sdf.parse(sdf.format(Date()))!!
        }

        if (parsedDate.before(todayDate)) {
            return ValidationResult.error(
                LinkCodes.INVALID_DATE,
                "La fecha de vencimiento ($vence) no puede ser anterior a hoy (${sdf.format(todayDate)})"
            )
        }

        val calMax = Calendar.getInstance(Locale.US).apply {
            time = todayDate
            add(Calendar.DAY_OF_YEAR, maxDaysAhead)
        }
        val maxAllowedDate = calMax.time

        if (parsedDate.after(maxAllowedDate)) {
            return ValidationResult.error(
                LinkCodes.INVALID_DATE,
                "La fecha de vencimiento supera el límite máximo permitido de $maxDaysAhead días"
            )
        }

        return ValidationResult.ok()
    }
}
