package com.example.network

import org.json.JSONObject

data class HttpConfig(
    val ignoreSslErrors: Boolean = true,
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
    val loginUrl: String = "https://sdmx.vip/resellers/login",
    val loginReferer: String = "https://sdmx.vip/resellers/login?referrer=logout",
    val loginOrigin: String = "https://sdmx.vip",
    val createLineUrl: String = "https://sdmx.vip/resellers/post.php?action=line",
    val createLineReferer: String = "https://sdmx.vip/resellers/line?trial=1",
    val packageAdults: String = "150",
    val packageNormal: String = "88",
    val packageDurationAdults: String = "24 hours",
    val packageDurationNormal: String = "2 hours",
    val bouquetsDefault: String = "19,24,21,8,23",
    val bouquetAdults: String = "96",
    val trialParam: String = "1",
    val maxConnections: String = "2",
    val deleteLineUrl: String = "https://sdmx.vip/resellers/api?action=line&sub=delete&user_id={id}",
    val deleteLineReferer: String = "https://sdmx.vip/resellers/lines?order=0&dir=desc",
    val tableUrl: String = "https://sdmx.vip/resellers/table",
    val tableReferer: String = "https://sdmx.vip/resellers/lines?order=0&dir=desc",
    val customHeadersJson: String = "{}"
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("ignoreSslErrors", ignoreSslErrors)
        json.put("userAgent", userAgent)
        json.put("loginUrl", loginUrl)
        json.put("loginReferer", loginReferer)
        json.put("loginOrigin", loginOrigin)
        json.put("createLineUrl", createLineUrl)
        json.put("createLineReferer", createLineReferer)
        json.put("packageAdults", packageAdults)
        json.put("packageNormal", packageNormal)
        json.put("packageDurationAdults", packageDurationAdults)
        json.put("packageDurationNormal", packageDurationNormal)
        json.put("bouquetsDefault", bouquetsDefault)
        json.put("bouquetAdults", bouquetAdults)
        json.put("trialParam", trialParam)
        json.put("maxConnections", maxConnections)
        json.put("deleteLineUrl", deleteLineUrl)
        json.put("deleteLineReferer", deleteLineReferer)
        json.put("tableUrl", tableUrl)
        json.put("tableReferer", tableReferer)
        json.put("customHeadersJson", customHeadersJson)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String?): HttpConfig {
            if (jsonStr.isNullOrEmpty()) return HttpConfig()
            return try {
                val json = JSONObject(jsonStr)
                HttpConfig(
                    ignoreSslErrors = json.optBoolean("ignoreSslErrors", true),
                    userAgent = json.optString("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"),
                    loginUrl = json.optString("loginUrl", "https://sdmx.vip/resellers/login"),
                    loginReferer = json.optString("loginReferer", "https://sdmx.vip/resellers/login?referrer=logout"),
                    loginOrigin = json.optString("loginOrigin", "https://sdmx.vip"),
                    createLineUrl = json.optString("createLineUrl", "https://sdmx.vip/resellers/post.php?action=line"),
                    createLineReferer = json.optString("createLineReferer", "https://sdmx.vip/resellers/line?trial=1"),
                    packageAdults = json.optString("packageAdults", "150"),
                    packageNormal = json.optString("packageNormal", "88"),
                    packageDurationAdults = json.optString("packageDurationAdults", "24 hours"),
                    packageDurationNormal = json.optString("packageDurationNormal", "2 hours"),
                    bouquetsDefault = json.optString("bouquetsDefault", "19,24,21,8,23"),
                    bouquetAdults = json.optString("bouquetAdults", "96"),
                    trialParam = json.optString("trialParam", "1"),
                    maxConnections = json.optString("maxConnections", "2"),
                    deleteLineUrl = json.optString("deleteLineUrl", "https://sdmx.vip/resellers/api?action=line&sub=delete&user_id={id}"),
                    deleteLineReferer = json.optString("deleteLineReferer", "https://sdmx.vip/resellers/lines?order=0&dir=desc"),
                    tableUrl = json.optString("tableUrl", "https://sdmx.vip/resellers/table"),
                    tableReferer = json.optString("tableReferer", "https://sdmx.vip/resellers/lines?order=0&dir=desc"),
                    customHeadersJson = json.optString("customHeadersJson", "{}")
                )
            } catch (e: Exception) {
                HttpConfig()
            }
        }
    }
}
