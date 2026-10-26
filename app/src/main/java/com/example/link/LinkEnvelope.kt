package com.example.link

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Envelope para la comunicación con el bot de WhatsApp vía NTFY (Protocolo v1).
 *
 * Comandos entrantes (bot -> app):
 *   cmd != null, re == null
 *
 * Respuestas salientes (app -> bot):
 *   cmd == null, re != null (id del comando al que responde)
 *
 * payload: String con el JSON serializado (evita discrepancias de orden de claves en firma).
 * sig: HMAC-SHA256 en hex minúsculas.
 */
data class LinkEnvelope(
    @SerializedName("v") val v: Int = 1,
    @SerializedName("id") val id: String,
    @SerializedName("ts") val ts: Long, // epoch en segundos UTC
    @SerializedName("cmd") val cmd: String? = null,
    @SerializedName("re") val re: String? = null,
    @SerializedName("payload") val payload: String, // JSON como STRING
    @SerializedName("sig") val sig: String
) {
    companion object {
        private val gson = Gson()

        fun fromJson(jsonStr: String): LinkEnvelope? {
            return try {
                gson.fromJson(jsonStr, LinkEnvelope::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        return gson.toJson(this)
    }
}

/**
 * Estructura del contenido JSON en el campo `payload` de una respuesta.
 */
data class LinkResponsePayload(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("code") val code: String,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: Any? = null
) {
    companion object {
        private val gson = Gson()

        fun fromJson(jsonStr: String): LinkResponsePayload? {
            return try {
                gson.fromJson(jsonStr, LinkResponsePayload::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        return gson.toJson(this)
    }
}

/**
 * Códigos de respuesta del protocolo v1.
 */
object LinkCodes {
    const val OK = "OK"
    const val QUEUED = "QUEUED"
    const val BAD_SIGNATURE = "BAD_SIGNATURE"
    const val EXPIRED = "EXPIRED"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val INVALID_USERNAME = "INVALID_USERNAME"
    const val INVALID_PASSWORD = "INVALID_PASSWORD"
    const val INVALID_DATE = "INVALID_DATE"
    const val DUPLICATE_USER = "DUPLICATE_USER"
    const val NOT_FOUND = "NOT_FOUND"
    const val PANEL_LOGIN_FAILED = "PANEL_LOGIN_FAILED"
    const val PANEL_CREATE_FAILED = "PANEL_CREATE_FAILED"
    const val ID_NOT_FOUND = "ID_NOT_FOUND"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val INTERNAL = "INTERNAL"
}
