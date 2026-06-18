package com.latchi.admin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeminiKeyManager — إدارة مفتاح Gemini API بأمان
 * المفتاح يُخزن في SharedPreferences فقط — لا يظهر في الكود أبداً
 */
object GeminiKeyManager {

    private const val PREFS = "gemini_prefs"
    private const val KEY_PRIMARY   = "gemini_key_1"
    private const val KEY_SECONDARY = "gemini_key_2"
    private const val MODEL = "gemini-2.0-flash"

    // ─── حفظ المفاتيح ────────────────────────────────────────────
    fun saveKeys(context: Context, key1: String, key2: String = "") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PRIMARY,   key1.trim())
            .putString(KEY_SECONDARY, key2.trim())
            .apply()
    }

    fun getKey1(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PRIMARY, "") ?: ""

    fun getKey2(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SECONDARY, "") ?: ""

    fun hasKey(context: Context): Boolean = getKey1(context).isNotBlank()

    // ─── استدعاء Gemini (يجرب المفتاح الأول ثم الثاني) ──────────
    fun ask(context: Context, prompt: String, maxTokens: Int = 500): String? {
        val keys = listOf(getKey1(context), getKey2(context)).filter { it.isNotBlank() }
        if (keys.isEmpty()) return "❌ لم يتم إعداد مفتاح Gemini بعد\nاذهب للإعدادات → Gemini Key"

        for (key in keys) {
            try {
                val result = callGemini(key, prompt, maxTokens)
                if (result != null) return result
            } catch (_: Exception) { continue }
        }
        return "❌ فشل الاتصال بـ Gemini — تأكد من المفتاح والإنترنت"
    }

    private fun callGemini(apiKey: String, prompt: String, maxTokens: Int): String? {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod  = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput       = true
        conn.connectTimeout = 15_000
        conn.readTimeout    = 25_000

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", maxTokens)
                put("temperature", 0.3)
            })
        }

        conn.outputStream.write(body.toString().toByteArray(Charsets.UTF_8))

        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            if (err.contains("API_KEY_INVALID") || err.contains("PERMISSION_DENIED"))
                throw Exception("invalid_key")
            return null
        }

        return JSONObject(conn.inputStream.bufferedReader().readText())
            .optJSONArray("candidates")
            ?.getJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.getJSONObject(0)
            ?.optString("text", "")
            ?.trim()
    }
}
