package com.myai.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Talks to YOUR backend (https://my-ai-backend-itf0.onrender.com).
 * The backend holds the AI brain, memory, modes, and tools.
 */
object Api {
    const val BASE = "https://my-ai-backend-itf0.onrender.com"

    /** Send a chat message. Returns the AI reply (or an error string). */
    fun chat(userId: String, message: String, mode: String): String {
        return try {
            val body = JSONObject()
                .put("user_id", userId).put("message", message).put("mode", mode)
            val res = postJson("$BASE/chat", body)
            JSONObject(res).optString("reply", "…")
        } catch (e: Exception) {
            "I couldn't reach the server. Check your internet and try again.\n(${e.message})"
        }
    }

    /** Save a fact to long-term memory. */
    fun remember(userId: String, fact: String) {
        try { postJson("$BASE/remember", JSONObject().put("user_id", userId).put("fact", fact)) } catch (_: Exception) {}
    }

    /** Get an image URL for a prompt (Pollinations via backend). */
    fun imageUrl(prompt: String): String {
        val enc = URLEncoder.encode(prompt, "UTF-8")
        // backend returns a url; but we can also build it directly for speed
        return "https://image.pollinations.ai/prompt/$enc?width=768&height=768&nologo=true&seed=${(0..99999).random()}"
    }

    fun wake() { try { get("$BASE/") } catch (_: Exception) {} }

    private fun postJson(urlStr: String, body: JSONObject): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 90000
        conn.readTimeout = 90000
        val os: OutputStream = conn.outputStream
        os.write(body.toString().toByteArray(Charsets.UTF_8)); os.flush(); os.close()
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream)).readText()
    }

    private fun get(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection)
        conn.connectTimeout = 90000; conn.readTimeout = 90000
        return BufferedReader(InputStreamReader(conn.inputStream)).readText()
    }
}
