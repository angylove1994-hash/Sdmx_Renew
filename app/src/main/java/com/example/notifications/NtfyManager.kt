package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.LogManager
import com.example.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object NtfyManager {
    private const val TAG = "NtfyManager"
    const val DEFAULT_TOPIC = "Gato_Negro_Reportes"
    const val BASE_URL = "https://ntfy.sh/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun sendPushNotification(
        context: Context,
        title: String,
        body: String,
        tags: String = "bell",
        priority: String = "default" // min, low, default, high, max (or 1..5)
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val topic = PreferencesManager.getNtfyTopic(context).ifEmpty { DEFAULT_TOPIC }
            val url = "$BASE_URL$topic"

            val requestBody = body.toRequestBody("text/plain; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Title", title)
                .header("Priority", priority)
                .header("Tags", tags)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Ntfy push sent successfully to topic: $topic")
                    true
                } else {
                    val code = response.code
                    Log.e(TAG, "Failed to send Ntfy push. HTTP $code")
                    LogManager.addLog(context, "⚠️ [Ntfy.sh] Error HTTP $code al enviar push a '$topic'")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending Ntfy push: ${e.message}", e)
            LogManager.addLog(context, "❌ [Ntfy.sh] Falló envío de push: ${e.message}")
            false
        }
    }

    suspend fun sendExecutionReport(
        context: Context,
        isSuccess: Boolean,
        summaryTitle: String,
        summaryDetails: String,
        recentLogs: List<String>
    ): Boolean {
        val title = if (isSuccess) {
            "SDMX: $summaryTitle"
        } else {
            "SDMX ALERTA: $summaryTitle"
        }

        val tags = if (isSuccess) "white_check_mark,arrows_counterclockwise" else "warning,x,loudspeaker"
        val priority = if (isSuccess) "default" else "high"

        val sb = StringBuilder()
        sb.appendLine("📡 REPORTE DE EJECUCIÓN SDMX AUTO-RENEW")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📋 Estado: ${if (isSuccess) "✅ EXITOSO" else "❌ CON ERRORES / FALLIDO"}")
        sb.appendLine("ℹ️ Detalle: $summaryDetails")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📜 LOG COMPLETO DE EJECUCIÓN:")
        
        if (recentLogs.isEmpty()) {
            sb.appendLine("(Sin líneas de log)")
        } else {
            // Include chronological logs (oldest to newest for easy reading)
            recentLogs.reversed().takeLast(40).forEach { line ->
                sb.appendLine(line)
            }
        }

        val success = sendPushNotification(
            context = context,
            title = title,
            body = sb.toString(),
            tags = tags,
            priority = priority
        )

        if (success) {
            LogManager.addLog(context, "📲 [Ntfy.sh] Reporte y logs enviados a https://ntfy.sh/${PreferencesManager.getNtfyTopic(context).ifEmpty { DEFAULT_TOPIC }}")
        }
        return success
    }
}
