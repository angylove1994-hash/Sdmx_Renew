package com.example.link

import android.content.Context
import android.util.Log
import com.example.data.LogManager
import com.example.data.PreferencesManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class LinkCommandListener(private val context: Context) {

    companion object {
        private const val TAG = "LinkCommandListener"
    }

    private var listenerJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Streaming infinito sin timeout de lectura
        .retryOnConnectionFailure(true)
        .build()

    fun start(scope: CoroutineScope) {
        if (listenerJob?.isActive == true) return
        listenerJob = scope.launch(Dispatchers.IO) {
            runListenerLoop()
        }
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
    }

    private suspend fun runListenerLoop() {
        while (currentCoroutineContext().isActive) {
            val enabled = PreferencesManager.isLinkEnabled(context)
            if (!enabled) {
                // Si el enlace está apagado, esperar 5 segundos antes de volver a consultar
                delay(5000L)
                continue
            }

            val baseUrl = PreferencesManager.getNtfyBaseUrl(context).ifEmpty { PreferencesManager.DEFAULT_NTFY_BASE_URL }
            val cmdTopic = PreferencesManager.getLinkTopicCmd(context).ifEmpty { PreferencesManager.DEFAULT_LINK_TOPIC_CMD }
            val token = PreferencesManager.getLinkToken(context)

            val streamUrl = "${baseUrl.trimEnd('/')}/$cmdTopic/json?since=24h"
            LogManager.addLog(context, "📡 [Enlace Bot] Conectando escucha NTFY en '$cmdTopic'...")

            try {
                val reqBuilder = Request.Builder()
                    .url(streamUrl)
                    .get()
                    .addHeader("Accept", "application/x-ndjson, application/json")

                if (token.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $token")
                }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        LogManager.addLog(context, "⚠️ [Enlace Bot] Error HTTP ${response.code} conectando a NTFY. Reintentando en 10s...")
                        delay(10_000L)
                        return@use
                    }

                    LogManager.addLog(context, "🟢 [Enlace Bot] Escucha conectada a '$cmdTopic'")
                    val inputStream = response.body?.byteStream() ?: return@use
                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue

                        try {
                            val json = JSONObject(line)
                            val event = json.optString("event")
                            if (event == "message") {
                                val messageBody = json.optString("message")
                                if (messageBody.isNotEmpty()) {
                                    LinkCommandExecutor.handleMessage(context, messageBody)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error procesando línea NTFY: ${e.message}", e)
                        }
                    }
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Excepción en conexión NTFY: ${e.message}", e)
                LogManager.addLog(context, "⚠️ [Enlace Bot] Conexión interrumpida: ${e.message}. Reconectando en 8s...")
                delay(8000L)
            }
        }
    }
}
