package com.rahulbaberwal.wattwarden.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Fires a JSON POST at Home Assistant / IFTTT / any smart-plug webhook. */
object Webhook {
    suspend fun fire(url: String, payload: Map<String, Any>): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url.trim()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONObject(payload).toString().toByteArray()) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }
}
