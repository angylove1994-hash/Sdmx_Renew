package com.example.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogManager {
    private const val LOG_FILE_NAME = "sdmx_logs.txt"
    private const val MAX_LOG_LINES = 150

    @Synchronized
    fun addLog(context: Context, message: String) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
            val dateSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val timestamp = sdf.format(Date())
            val dateStamp = dateSdf.format(Date())
            val formattedMsg = "[$timestamp] $message"
            
            // Append to file
            file.appendText("$formattedMsg\n")
            
            // Keep file size limited to MAX_LOG_LINES
            var lines = file.readLines()
            if (lines.size > MAX_LOG_LINES) {
                lines = lines.takeLast(MAX_LOG_LINES)
                file.writeText(lines.joinToString("\n") + "\n")
            }
            
            Log.d("LogManager", "[$dateStamp] $message")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getLogs(context: Context): List<String> {
        return try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                file.readLines().asReversed() // show latest first
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    @Synchronized
    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
