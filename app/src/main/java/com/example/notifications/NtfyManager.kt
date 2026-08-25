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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NtfyManager {
    private const val TAG = "NtfyManager"
    const val DEFAULT_TOPIC = "Gato_Negro_Reportes"
    const val BASE_URL = "https://ntfy.sh"

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
            
            // Map priority string to ntfy integer priority (1..5)
            val priorityInt = when (priority.lowercase()) {
                "min", "1" -> 1
                "low", "2" -> 2
                "high", "4" -> 4
                "urgent", "max", "5" -> 5
                else -> 3
            }

            val tagsArray = JSONArray()
            tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                tagsArray.put(it)
            }

            // Using JSON payload guarantees full UTF-8 support (accents, emojis, etc.)
            // without any HTTP header character restriction issues.
            val jsonPayload = JSONObject().apply {
                put("topic", topic)
                put("title", title)
                put("message", body)
                put("priority", priorityInt)
                put("tags", tagsArray)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(BASE_URL)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = try { response.peekBody(1024).string() } catch (e: Exception) { "" }
                if (response.isSuccessful) {
                    Log.d(TAG, "Ntfy push sent successfully to topic: $topic. Response: $responseStr")
                    true
                } else {
                    val code = response.code
                    Log.e(TAG, "Failed to send Ntfy push. HTTP $code. Response: $responseStr")
                    LogManager.addLog(context, "⚠️ [Ntfy.sh] Error HTTP $code al enviar push a '$topic': $responseStr")
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
        recentLogs: List<String> = emptyList()
    ): Boolean {
        val title = if (isSuccess) {
            "SDMX: $summaryTitle"
        } else {
            "🚨 SDMX ALERTA: $summaryTitle"
        }

        val tags = if (isSuccess) "white_check_mark,arrows_counterclockwise" else "warning,rotating_light,loudspeaker"
        val priority = if (isSuccess) "default" else "urgent"

        val sb = StringBuilder()
        sb.appendLine("📡 SDMX AUTO-RENEW")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📋 Estado: ${if (isSuccess) "✅ EXITOSO" else "❌ FALLIDO (Reintento en 10s)"}")
        sb.appendLine("ℹ️ Detalle: $summaryDetails")

        // In case of error, append only the key failure reason instead of entire log flood
        if (!isSuccess && recentLogs.isNotEmpty()) {
            val errorLines = recentLogs.filter {
                it.contains("❌") || it.contains("Error", ignoreCase = true) || it.contains("⚠️") || it.contains("Fallo", ignoreCase = true)
            }.take(3)

            if (errorLines.isNotEmpty()) {
                sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
                sb.appendLine("🔍 Motivo del error:")
                errorLines.forEach { line ->
                    sb.appendLine("• $line")
                }
            }
        }

        val success = sendPushNotification(
            context = context,
            title = title,
            body = sb.toString().trim(),
            tags = tags,
            priority = priority
        )

        if (success) {
            Log.d(TAG, "Ntfy push summary sent to ${PreferencesManager.getNtfyTopic(context).ifEmpty { DEFAULT_TOPIC }}")
        }
        return success
    }
}
