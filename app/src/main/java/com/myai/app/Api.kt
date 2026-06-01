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

    /** Send a chat message in a specific conversation. Returns the AI reply. */
    fun chat(userId: String, message: String, mode: String, convId: String = "default"): String {
        return try {
            val body = JSONObject()
                .put("user_id", userId).put("message", message).put("mode", mode).put("conv_id", convId)
            val res = postJson("$BASE/chat", body)
            JSONObject(res).optString("reply", "…")
        } catch (e: Exception) {
            "I couldn't reach the server. Check your internet and try again.\n(${e.message})"
        }
    }

    /** List the user's past conversations: (conv_id, title), newest first. */
    fun conversations(userId: String): List<Pair<String, String>> {
        return try {
            val arr = JSONObject(get("$BASE/conversations?user_id=$userId")).getJSONArray("conversations")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it); o.optString("conv_id") to o.optString("title")
            }
        } catch (_: Exception) { emptyList() }
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

    /** Returns the parsed JSON object from /signup or /login. */
    fun signup(name: String, email: String, password: String, recovery: String = ""): JSONObject {
        return try {
            JSONObject(postJson("$BASE/signup", JSONObject().put("name", name).put("email", email).put("password", password).put("recovery", recovery)))
        } catch (e: Exception) { JSONObject().put("ok", false).put("error", "No internet or server is waking up. Try again.") }
    }

    fun login(email: String, password: String): JSONObject {
        return try {
            JSONObject(postJson("$BASE/login", JSONObject().put("email", email).put("password", password)))
        } catch (e: Exception) { JSONObject().put("ok", false).put("error", "No internet or server is waking up. Try again.") }
    }

    /** Teach the AI new knowledge. Returns true on success. */
    fun teach(userId: String, text: String): Boolean {
        return try {
            val r = postJson("$BASE/teach", JSONObject().put("user_id", userId).put("text", text).put("source", "note"))
            JSONObject(r).optBoolean("ok", false)
        } catch (_: Exception) { false }
    }

    /** Submit a feature request (Creator Console). */
    fun featureRequest(userId: String, name: String, request: String): Boolean {
        return try {
            postJson("$BASE/feature_request", JSONObject().put("user_id", userId).put("name", name).put("request", request))
            true
        } catch (_: Exception) { false }
    }

    /** Get the saved feature requests (returns the raw JSON array string). */
    fun featureRequests(): String {
        return try { get("$BASE/feature_requests") } catch (_: Exception) { "{\"requests\":[]}" }
    }

    /** Messages of one conversation, oldest first. Returns list of (role, content). */
    fun history(userId: String, convId: String = "default"): List<Pair<String, String>> {
        return try {
            val arr = JSONObject(get("$BASE/history?user_id=$userId&conv_id=$convId")).getJSONArray("history")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it); o.optString("role") to o.optString("content")
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Analyze a photo. image_b64 = base64 (no prefix). Returns reply. */
    fun vision(userId: String, imageB64: String, question: String): String {
        return try {
            val r = postJson("$BASE/vision", JSONObject().put("user_id", userId).put("image_b64", imageB64).put("question", question))
            JSONObject(r).optString("reply", "I couldn't read that image.")
        } catch (e: Exception) { "Couldn't analyze the image (${e.message})." }
    }

    /** Reset password using recovery word. Returns the JSON result. */
    fun resetPassword(email: String, recovery: String, newPassword: String): JSONObject {
        return try {
            JSONObject(postJson("$BASE/reset_password", JSONObject().put("email", email).put("recovery", recovery).put("new_password", newPassword)))
        } catch (e: Exception) { JSONObject().put("ok", false).put("error", "No connection. Try again.") }
    }

    /** Business trial status: (allowed, daysLeft, locked). */
    fun accessStatus(userId: String): Triple<Boolean, Int, Boolean> {
        return try {
            val o = JSONObject(get("$BASE/access_status?user_id=$userId"))
            Triple(o.optBoolean("business_allowed", true), o.optInt("trial_days_left", 0), o.optBoolean("locked", false))
        } catch (_: Exception) { Triple(true, 0, false) }
    }

    /** Start a Paystack payment. Returns (ok, url, reference). */
    fun payStart(userId: String, email: String): Triple<Boolean, String, String> {
        return try {
            val o = JSONObject(postJson("$BASE/pay/start", JSONObject().put("user_id", userId).put("email", email)))
            Triple(o.optBoolean("ok", false), o.optString("url", ""), o.optString("reference", ""))
        } catch (_: Exception) { Triple(false, "", "") }
    }

    /** Verify a payment after the user pays. Returns true if now premium. */
    fun payVerify(reference: String, userId: String): Boolean {
        return try {
            JSONObject(get("$BASE/pay/verify?reference=$reference&user_id=$userId")).optBoolean("ok", false)
        } catch (_: Exception) { false }
    }

    /** Build a complete website/web-app from a description. Returns the HTML code. */
    fun build(prompt: String): String {
        return try {
            JSONObject(postJson("$BASE/build", JSONObject().put("prompt", prompt))).optString("code", "")
        } catch (_: Exception) { "" }
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
