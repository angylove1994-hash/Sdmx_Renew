package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.UserModel
import com.example.data.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class SdmxApiService {
    private val cookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            Log.d("SdmxApiCookieJar", "saveFromResponse: $url -> ${cookies.joinToString { "${it.name}=${it.value}" }}")
            val host = "sdmx.vip" // force save to sdmx.vip
            val list = cookieStore[host] ?: mutableListOf()
            cookies.forEach { cookie ->
                list.removeAll { it.name == cookie.name }
                list.add(cookie)
            }
            cookieStore[host] = list
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookies = cookieStore["sdmx.vip"] ?: emptyList()
            Log.d("SdmxApiCookieJar", "loadForRequest: $url -> ${cookies.joinToString { "${it.name}=${it.value}" }}")
            return cookies
        }
        
        fun getCookieString(): String {
            return cookieStore["sdmx.vip"]?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun login(context: Context?, user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
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
            
            val request = Request.Builder()
                .url("https://sdmx.vip/resellers/login")
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://sdmx.vip")
                .addHeader("Referer", "https://sdmx.vip/resellers/login?referrer=logout")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .addHeader("Cache-Control", "max-age=0")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .build()
                
            try {
                client.newCall(request).execute().use { response ->
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
    
    suspend fun createLine(username: String, pass: String, expDate: String, adultos: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val formBuilder = FormBody.Builder()
            .add("action", "line")
            .add("trial", "1")
            .add("bouquets_selected", "")
            .add("username", username)
            .add("password", pass)
            .add("package", if (adultos) "150" else "88")
            .add("package_cost", "0")
            .add("package_duration", if (adultos) "24 hours" else "2 hours")
            .add("max_connections", "2")
            .add("exp_date", "$expDate 00:00")
            .add("contact", "")
            .add("reseller_notes", "")
            .add("isp_clear", "")
            .add("bouquets_selected[]", "19")
            .add("bouquets_selected[]", "24")
            .add("bouquets_selected[]", "21")
            .add("bouquets_selected[]", "8")
            .add("bouquets_selected[]", "23")
            
        if (adultos) {
            formBuilder.add("bouquets_selected[]", "96")
        }
        
        val request = Request.Builder()
            .url("https://sdmx.vip/resellers/post.php?action=line")
            .post(formBuilder.build())
            .addHeader("Accept", "*/*")
            .addHeader("Host", "sdmx.vip")
            .addHeader("Origin", "https://sdmx.vip")
            .addHeader("Referer", "https://sdmx.vip/resellers/line?trial=1")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/150.0.0.0 Safari/537.36")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Cookie", cookieJar.getCookieString())
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                Log.d("SdmxApi", "Create response: $bodyStr")
                if (bodyStr.contains("\"result\":false") || (bodyStr.contains("error", ignoreCase = true) && !bodyStr.contains("success"))) {
                    Log.e("SdmxApi", "Error from server: $bodyStr")
                    try {
                        val obj = JSONObject(bodyStr)
                        val msg = obj.optString("message", "")
                        if (msg.isNotEmpty()) return@withContext Result.failure(Exception(msg))
                    } catch (e: Exception) {}
                    return@withContext Result.failure(Exception("Error de servidor. Revisa el log."))
                }
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error HTTP ${response.code}"))
                }
                return@withContext Result.success("OK")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(Exception(e.message ?: "Error de red"))
        }
    }
    
    suspend fun deleteLine(id: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://sdmx.vip/resellers/api?action=line&sub=delete&user_id=$id")
            .get()
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Host", "sdmx.vip")
            .addHeader("Referer", "https://sdmx.vip/resellers/lines?order=0&dir=desc")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/150.0.0.0 Safari/537.36")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Cookie", cookieJar.getCookieString())
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                Log.d("SdmxApi", "Delete response: $bodyStr")
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    suspend fun getTableRows(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("sdmx.vip")
            .addPathSegments("resellers/table")
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

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Host", "sdmx.vip")
            .addHeader("Referer", "https://sdmx.vip/resellers/lines?order=0&dir=desc")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/150.0.0.0 Safari/537.36")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Cookie", cookieJar.getCookieString())
            .build()
            
        val list = mutableListOf<Pair<String, String>>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext list
                val jsonStr = response.body?.string() ?: return@withContext list
                
                Log.d("SdmxApi", "Table JSON length: ${jsonStr.length}")
                
                val jsonResponse = JSONObject(jsonStr)
                val dataArray = jsonResponse.optJSONArray("data") ?: return@withContext list
                
                val cleanRegex = Regex("<.*?>")
                for (i in 0 until dataArray.length()) {
                    val row = dataArray.optJSONArray(i) ?: continue
                    if (row.length() >= 2) {
                        val rawId = row.optString(0)
                        val rawUsername = row.optString(1)
                        
                        var cleanId = rawId.replace(cleanRegex, "").trim()
                        val cleanUsername = rawUsername.replace(cleanRegex, "").trim()
                        
                        if (cleanId.isEmpty() && rawId.contains("value=")) {
                            val match = Regex("value=[\"']?(\\d+)[\"']?").find(rawId)
                            if (match != null) cleanId = match.groupValues[1]
                        }
                        
                        if (cleanId.isNotEmpty() && cleanUsername.isNotEmpty()) {
                            list.add(Pair(cleanUsername, cleanId))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    suspend fun getTableIds(): Map<String, String> = getTableRows().toMap()

    suspend fun verifyHealthCheck(context: Context?, sdUser: String, sdPass: String): Boolean = withContext(Dispatchers.IO) {
        context?.let { LogManager.addLog(it, "🔍 [Paso 1/3] Verificación previa: Login en panel SDMX...") }
        val loginOk = login(context, sdUser, sdPass)
        if (!loginOk) {
            context?.let { LogManager.addLog(it, "❌ ERROR EN VERIFICACIÓN PREVIA (Paso 1): No se pudo iniciar sesión. Se cancela el ciclo.") }
            return@withContext false
        }

        // Clean up any old Test00777 line if present
        try {
            val initialIds = getTableIds()
            val existingTestId = initialIds["Test00777"]
            if (existingTestId != null) {
                context?.let { LogManager.addLog(it, "🧹 Limpiando usuario de prueba previo 'Test00777' (ID: $existingTestId)...") }
                deleteLine(existingTestId)
                kotlinx.coroutines.delay(1000)
            }
        } catch (e: Exception) {
            // Ignore cleanup error
        }

        // Paso 2: Crear usuario de prueba
        context?.let { LogManager.addLog(it, "🧪 [Paso 2/3] Verificación previa: Creando usuario de prueba 'Test00777'...") }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, 1)
        val testExpDate = sdf.format(cal.time)

        val createRes = createLine("Test00777", "Test00777", testExpDate, adultos = false)
        if (createRes.isFailure) {
            val err = createRes.exceptionOrNull()?.message ?: "Error desconocido"
            context?.let { LogManager.addLog(it, "❌ ERROR EN VERIFICACIÓN PREVIA (Paso 2): No se pudo crear usuario 'Test00777'. Detalle: $err. Se cancela el ciclo.") }
            return@withContext false
        }

        context?.let { LogManager.addLog(it, "✅ [Paso 2/3] Usuario de prueba 'Test00777' creado en el panel.") }

        // Paso 3: Obtener ID y borrarlo inmediatamente
        context?.let { LogManager.addLog(it, "🔎 [Paso 3/3] Verificación previa: Obteniendo ID y eliminando usuario 'Test00777'...") }
        kotlinx.coroutines.delay(1500)
        val newIds = getTableIds()
        val testId = newIds["Test00777"]

        if (testId != null) {
            val delOk = deleteLine(testId)
            if (delOk) {
                context?.let { LogManager.addLog(it, "✅ [Paso 3/3] Usuario 'Test00777' (ID: $testId) eliminado correctamente.") }
            } else {
                context?.let { LogManager.addLog(it, "⚠️ Advertencia en Paso 3: Borrado de 'Test00777' retornó falso. ID era $testId.") }
            }
        } else {
            context?.let { LogManager.addLog(it, "⚠️ Advertencia en Paso 3: No se encontró ID para 'Test00777' en la tabla.") }
        }

        context?.let { LogManager.addLog(it, "🎉 VERIFICACIÓN PREVIA COMPLETADA CON ÉXITO. Sistema SDMX listo para renovaciones.") }
        return@withContext true
    }
}
