package com.latchi.admin

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TrueMasterInfo(
    val success: Boolean,
    val isOnline: Boolean,
    val status: String = "—",
    val expiryDate: String = "—",
    val daysLeft: Int = 0,
    val maxConnections: String = "1",
    val activeConnections: String = "0",
    val username: String = "—",
    val host: String = "—"
)

/**
 * 👑 XtreamMasterInfoHelper
 *
 * الأداة الملكية الحصرية لاستخراج تفاصيل الحساب الحقيقية من أي رابط Xtream/M3U.
 * تتصل مباشرة بـ player_api.php لجلب:
 * 1. تاريخ انتهاء الصلاحية الحقيقي (Expiry Date).
 * 2. الأجهزة المسموح بها والمتصلة حالياً (Connections).
 * 3. حالة الحساب (Status).
 */
object XtreamMasterInfoHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun fetchInfo(m3uUrl: String): TrueMasterInfo {
        val cleanUrl = m3uUrl.trim().replace("&amp;", "&")
        if (cleanUrl.isBlank() || !cleanUrl.contains("username", true)) {
            return TrueMasterInfo(false, false, error("الرابط لا يحتوي على بيانات Xtream"))
        }

        return try {
            val uri = URI(cleanUrl)
            val host = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
            val query = uri.rawQuery ?: return TrueMasterInfo(false, false, error("استعلام الرابط فارغ"))
            
            val params = query.split("&").mapNotNull {
                val p = it.split("=", limit = 2)
                if (p.size == 2) URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8") else null
            }.toMap()

            val user = params["username"] ?: return TrueMasterInfo(false, false, error("اليوزر غير متوفر"))
            val pass = params["password"] ?: return TrueMasterInfo(false, false, error("الباسوورد غير متوفر"))

            val apiUrl = "$host/player_api.php?username=${URLEncoder.encode(user, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return TrueMasterInfo(false, false, error("HTTP ${response.code}"))
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val userInfo = json.optJSONObject("user_info") ?: return TrueMasterInfo(false, true, error("لا توجد user_info"))

                val status = userInfo.optString("status", "Active")
                val maxCons = userInfo.optString("max_connections", "1")
                val activeCons = userInfo.optString("active_cons", "0")

                // حساب تاريخ الانتهاء الحقيقي
                val expRaw = userInfo.optString("exp_date", "")
                var expDate = "بدون انتهاء ♾️"
                var diffDays = 999

                if (expRaw.isNotBlank() && expRaw.lowercase() != "null") {
                    try {
                        val ts = expRaw.toLong()
                        val date = Date(ts * 1000L)
                        expDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                        diffDays = ((date.time - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                    } catch (_: Exception) {
                        expDate = expRaw
                    }
                }

                TrueMasterInfo(
                    success = true,
                    isOnline = true,
                    status = status,
                    expiryDate = expDate,
                    daysLeft = diffDays,
                    maxConnections = maxCons,
                    activeConnections = activeCons,
                    username = user,
                    host = host
                )
            }

        } catch (e: Exception) {
            TrueMasterInfo(false, false, error("خطأ: ${e.localizedMessage}"))
        }
    }

    private fun error(msg: String): String = msg
    private fun java.net.URLEncoder.encode(v: String, enc: String): String = java.net.URLEncoder.encode(v, enc)
}
