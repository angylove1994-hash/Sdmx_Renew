package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.HttpConfigStorage
import com.example.data.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.*

class SdmxApiService {
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            Log.d("SdmxApiCookieJar", "saveFromResponse: $url -> ${cookies.joinToString { "${it.name}=${it.value}" }}")
            val host = url.host.ifEmpty { "sdmx.vip" }
            val list = cookieStore[host] ?: cookieStore["sdmx.vip"] ?: mutableListOf()
            cookies.forEach { cookie ->
                list.removeAll { it.name == cookie.name }
                list.add(cookie)
            }
            cookieStore[host] = list
            cookieStore["sdmx.vip"] = list
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host.ifEmpty { "sdmx.vip" }
            val cookies = cookieStore[host] ?: cookieStore["sdmx.vip"] ?: emptyList()
            Log.d("SdmxApiCookieJar", "loadForRequest: $url -> ${cookies.joinToString { "${it.name}=${it.value}" }}")
            return cookies
        }
    }

    private fun getOkHttpClient(ignoreSsl: Boolean = true): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)

        if (ignoreSsl) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                )

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val sslSocketFactory = sslContext.socketFactory

                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Log.e("SdmxApi", "Error configurando SSL inseguro (TrustAll): ${e.message}")
            }
        }

        return builder.build()
    }

    private fun applyCustomHeaders(builder: Request.Builder, customHeadersJson: String) {
        if (customHeadersJson.isBlank() || customHeadersJson.trim() == "{}") return
        try {
            val json = JSONObject(customHeadersJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key, "")
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    builder.addHeader(key, value)
                }
            }
        } catch (e: Exception) {
            Log.e("SdmxApi", "Error al aplicar custom headers: ${e.message}")
        }
    }

    suspend fun login(context: Context?, user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val config = HttpConfigStorage.getConfig(context)
        val client = getOkHttpClient(config.ignoreSslErrors)
        val maxAttempts = 3
        var currentAttempt = 1

        while (currentAttempt <= maxAttempts) {
            context?.let { 
                if (currentAttempt > 1) {
                    LogManager.addLog(it, "🔄 Reintentando login HTTP ($currentAttempt/$maxAttempts) para: $user...")
                } else {
                    LogManager.addLog(it, "Iniciando login HTTP para: $user...")
                }
            }
            
            val formBody = FormBody.Builder()
                .add("referrer", "logout")
                .add("username", user)
                .add("password", pass)
                .add("login", "")
                .build()
            
            val requestBuilder = Request.Builder()
                .url(config.loginUrl)
                .post(formBody)
                .addHeader("User-Agent", config.userAgent)
                .addHeader("Origin", config.loginOrigin)
                .addHeader("Referer", config.loginReferer)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .addHeader("Cache-Control", "max-age=0")
                .addHeader("Upgrade-Insecure-Requests", "1")

            applyCustomHeaders(requestBuilder, config.customHeadersJson)

            try {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val code = response.code
                    val location = response.header("Location") ?: ""
                    val finalUrl = response.request.url.toString()
                    
                    val isSuccess = (response.isSuccessful || code == 302 || code == 301) &&
                            (finalUrl.contains("dashboard") || finalUrl.contains("index") || location.contains("dashboard") || location.contains("index") || (!finalUrl.contains("login") && code != 200))
                    
                    if (!isSuccess) {
                        val bodyString = try {
                            val peeked = response.peekBody(4096).string()
                            peeked.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                        } catch (e: Exception) {
                            "No se pudo leer el cuerpo de la respuesta: ${e.message}"
                        }
                        val snippet = if (bodyString.length > 250) bodyString.substring(0, 250) + "..." else bodyString
                        
                        context?.let { 
                            LogManager.addLog(it, "❌ Login fallido. Código: $code. URL final: $finalUrl")
                            LogManager.addLog(it, "🔍 Detalle respuesta: $snippet")
                        }
                        return@withContext false
                    } else {
                        context?.let { 
                            LogManager.addLog(it, "✅ Login exitoso. Redirigido a: $finalUrl")
                        }
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                val isLastAttempt = currentAttempt == maxAttempts
                context?.let { 
                    LogManager.addLog(it, "❌ Error de conexión al hacer login (${e.javaClass.simpleName}): ${e.message}${if (!isLastAttempt) " - Reintentando..." else ""}")
                }
                if (isLastAttempt) {
                    e.printStackTrace()
                    return@withContext false
                }
                kotlinx.coroutines.delay(2000)
            }
            currentAttempt++
        }
        return@withContext false
    }
    
    suspend fun createLine(context: Context? = null, username: String, pass: String, expDate: String, adultos: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val config = HttpConfigStorage.getConfig(context)
        val client = getOkHttpClient(config.ignoreSslErrors)

        val pkg = if (adultos) config.packageAdults else config.packageNormal
        val duration = if (adultos) config.packageDurationAdults else config.packageDurationNormal

        val formBuilder = FormBody.Builder()
            .add("username", username)
            .add("password", pass)
            .add("package", pkg)
            .add("package_cost", "0")
            .add("package_duration", duration)
            .add("exp_date", "$expDate 00:00")
            .add("contact", "")
            .add("reseller_notes", "")
            .add("isp_clear", "")
            .add("bouquets_selected", "")

        val defaultBouquets = config.bouquetsDefault.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (bq in defaultBouquets) {
            formBuilder.add("bouquets_selected[]", bq)
        }
            
        if (adultos && config.bouquetAdults.isNotEmpty()) {
            formBuilder.add("bouquets_selected[]", config.bouquetAdults.trim())
        }
        
        val requestBuilder = Request.Builder()
            .url(config.createLineUrl)
            .post(formBuilder.build())
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Origin", config.loginOrigin)
            .addHeader("Referer", config.createLineReferer)
            .addHeader("User-Agent", config.userAgent)
            .addHeader("X-Requested-With", "XMLHttpRequest")

        applyCustomHeaders(requestBuilder, config.customHeadersJson)
            
        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                Log.d("SdmxApi", "Create response code=${response.code}, body: $bodyStr")
                
                context?.let { ctx ->
                    val cleanSnippet = bodyStr.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                    val disp = if (cleanSnippet.length > 200) cleanSnippet.substring(0, 200) + "..." else cleanSnippet
                    LogManager.addLog(ctx, "📥 [SDMX Resp HTTP ${response.code}] $disp")
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error HTTP ${response.code}"))
                }

                // If the panel explicitly returns JSON {"result": false, ...}
                if (bodyStr.contains("\"result\":false", ignoreCase = true) || bodyStr.contains("\"result\": false", ignoreCase = true)) {
                    var errorMsg = "SDMX rechazó la creación (result=false)"
                    try {
                        val obj = JSONObject(bodyStr)
                        val msg = obj.optString("message", "").ifEmpty {
                            obj.optString("error", "").ifEmpty {
                                obj.optString("msg", "")
                            }
                        }
                        if (msg.isNotEmpty() && msg != "null") {
                            errorMsg = msg
                        }
                    } catch (_: Exception) {}
                    return@withContext Result.failure(Exception(errorMsg))
                }

                return@withContext Result.success("OK")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(Exception(e.message ?: "Error de red"))
        }
    }
    
    suspend fun deleteLine(context: Context? = null, id: String): Boolean = withContext(Dispatchers.IO) {
        val config = HttpConfigStorage.getConfig(context)
        val client = getOkHttpClient(config.ignoreSslErrors)

        val targetUrl = config.deleteLineUrl.replace("{id}", id)
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Referer", config.deleteLineReferer)
            .addHeader("User-Agent", config.userAgent)
            .addHeader("X-Requested-With", "XMLHttpRequest")

        applyCustomHeaders(requestBuilder, config.customHeadersJson)
            
        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                Log.d("SdmxApi", "Delete response: $bodyStr")
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    suspend fun getTableRows(context: Context? = null): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val config = HttpConfigStorage.getConfig(context)
        val client = getOkHttpClient(config.ignoreSslErrors)

        val parsedUrl = config.tableUrl.toHttpUrlOrNull() ?: HttpUrl.Builder().scheme("https").host("sdmx.vip").addPathSegments("resellers/table").build()
        val urlBuilder = parsedUrl.newBuilder()
            .addQueryParameter("draw", "1")
            .addQueryParameter("id", "lines")
            .addQueryParameter("filter", "")
            .addQueryParameter("reseller", "")
            .addQueryParameter("start", "0")
            .addQueryParameter("length", "10000")
            .addQueryParameter("order[0][column]", "0")
            .addQueryParameter("order[0][dir]", "desc")
            .addQueryParameter("search[value]", "")
            .addQueryParameter("search[regex]", "false")
            .addQueryParameter("_", System.currentTimeMillis().toString())

        for (i in 0..11) {
            urlBuilder.addQueryParameter("columns[$i][data]", "$i")
            urlBuilder.addQueryParameter("columns[$i][name]", "")
            urlBuilder.addQueryParameter("columns[$i][searchable]", "true")
            urlBuilder.addQueryParameter("columns[$i][orderable]", if (i == 5 || i == 7 || i == 11) "false" else "true")
            urlBuilder.addQueryParameter("columns[$i][search][value]", "")
            urlBuilder.addQueryParameter("columns[$i][search][regex]", "false")
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Referer", config.tableReferer)
            .addHeader("User-Agent", config.userAgent)
            .addHeader("X-Requested-With", "XMLHttpRequest")

        applyCustomHeaders(requestBuilder, config.customHeadersJson)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    context?.let { LogManager.addLog(it, "❌ Error al consultar tabla SDMX: HTTP ${response.code}") }
                    return@withContext emptyList()
                }
                
                val bodyStr = response.body?.string() ?: ""
                val results = mutableListOf<Pair<String, String>>()
                
                val json = JSONObject(bodyStr)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()
                
                for (i in 0 until data.length()) {
                    val row = data.getJSONArray(i)
                    if (row.length() >= 2) {
                        val col0 = row.getString(0)
                        val col1 = row.getString(1)
                        
                        val idMatch = Regex("id=[\"'](\\d+)[\"']|user_id=[\"'](\\d+)[\"']|data-id=[\"'](\\d+)[\"']|value=[\"'](\\d+)[\"']").find(col0)
                        val id = idMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } ?: col0.replace(Regex("<[^>]*>"), "").trim()
                        
                        val userClean = col1.replace(Regex("<[^>]*>"), "").trim()
                        
                        if (id.isNotEmpty() && userClean.isNotEmpty()) {
                            results.add(Pair(id, userClean))
                        }
                    }
                }
                
                return@withContext results
            }
        } catch (e: Exception) {
            e.printStackTrace()
            context?.let { LogManager.addLog(it, "❌ Error leyendo tabla SDMX: ${e.message}") }
            return@withContext emptyList()
        }
    }

    suspend fun getTableIds(context: Context? = null): Map<String, String> {
        val rows = getTableRows(context)
        val map = mutableMapOf<String, String>()
        for (row in rows) {
            map[row.second] = row.first
        }
        return map
    }

    suspend fun renewLine(
        context: Context? = null,
        username: String,
        pass: String,
        adultos: Boolean,
        meses: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, meses)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val expDate = sdf.format(cal.time)

        context?.let { LogManager.addLog(it, "⏳ [1/2] Eliminando versión anterior de '$username' en SDMX...") }
        val table = getTableRows(context)
        val match = table.find { it.second.equals(username, ignoreCase = true) }
        
        if (match != null) {
            val id = match.first
            context?.let { LogManager.addLog(it, "🗑️ Encontrado en panel ID #$id para '$username'. Borrando...") }
            deleteLine(context, id)
            kotlinx.coroutines.delay(1000)
        } else {
            context?.let { LogManager.addLog(it, "ℹ️ Línea '$username' no localizada en tabla (o ya expiró). Procediendo a creación.") }
        }

        context?.let { LogManager.addLog(it, "✨ [2/2] Creando línea '$username' en SDMX...") }
        val createResult = createLine(context, username, pass, expDate, adultos)
        if (createResult.isSuccess) {
            context?.let { LogManager.addLog(it, "✅ Línea '$username' renovada/creada exitosamente en SDMX.") }
            return@withContext true
        } else {
            val errMsg = createResult.exceptionOrNull()?.message ?: "Desconocido"
            context?.let { LogManager.addLog(it, "❌ Falló creación de '$username': $errMsg") }
            return@withContext false
        }
    }

    suspend fun verifyHealthCheck(context: Context, user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        LogManager.addLog(context, "🔍 Verificando estado del panel SDMX...")
        val loginOk = login(context, user, pass)
        if (!loginOk) {
            LogManager.addLog(context, "❌ Prueba previa: Falló la autenticación con SDMX.")
            return@withContext false
        }
        
        val rows = getTableRows(context)
        LogManager.addLog(context, "✅ Prueba previa exitosa. Panel accesible, ${rows.size} líneas detectadas en SDMX.")
        return@withContext true
    }
}
