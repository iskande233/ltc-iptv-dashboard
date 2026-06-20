package com.latchi.admin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 🛡️ VersionCodeHelper
 *
 * يحل المشكلة الحرجة:
 *   - BuildConfig.VERSION_CODE في تطبيق المشاهدة = (System.currentTimeMillis() / 1000).toInt()
 *   - إذا المستخدم ثبّت build أحدث مما نشره admin، السكريبت يرد update_available=false
 *
 * الحل:
 *   - عند النشر، نضمن أن versionCode الجديد > كل القيم السابقة
 *   - نستخدم: max(currentTimeSeconds, lastKnownVersionCode + 1)
 *   - هذا يضمن أن كل المستخدمين يحصلون على التحديث
 */
object VersionCodeHelper {

    private const val GOOGLE_SCRIPT = "https://script.google.com/macros/s/AKfycbxThygspXN6eB8cDUfY7XavKmhXZfewEUfQqd3vARScZ5y7adterInsbXshNkgPgfiF/exec"
    private const val SECRET = "LatchiAdmin2026"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * النتيجة بعد الحساب
     */
    data class MonotonicResult(
        val newVersionCode: Long,
        val previousVersionCode: Long,
        val currentTimeSeconds: Long,
        val reason: String
    )

    /**
     * يقرأ آخر versionCode من السكريبت، ثم يحسب versionCode جديد مضمون أنه أكبر.
     *
     * المنطق:
     *   newCode = max(nowSeconds + 60, previousVersionCode + 1)
     *
     * الـ +60 buffer يضمن أن:
     *   - إذا المستخدم ثبّت build في نفس الدقيقة، التحديث يعمل
     *   - لا يوجد race condition بين البناء والنشر
     *
     * هذا يضمن:
     *   - أكبر من أي BuildConfig.VERSION_CODE للمستخدم (لأن nowSeconds > وقت بناء المستخدم)
     *   - أكبر من آخر versionCode مُحفوظ (monotonic increase)
     */
    suspend fun computeMonotonicVersionCode(context: Context): MonotonicResult {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val nowPlusBuffer = nowSeconds + 60L  // safety buffer for same-second installs
        val previousCode = fetchLatestVersionCode(context)
        val newCode = maxOf(nowPlusBuffer, previousCode + 1)
        val reason = when {
            previousCode == 0L -> "first_publish:using_now+60=$nowPlusBuffer"
            nowPlusBuffer > previousCode -> "now_is_newer:max(now+60=$nowPlusBuffer,prev+1=${previousCode + 1})"
            else -> "previous_is_newer:using_prev+1=${previousCode + 1}"
        }
        return MonotonicResult(
            newVersionCode = newCode,
            previousVersionCode = previousCode,
            currentTimeSeconds = nowSeconds,
            reason = reason
        )
    }

    /**
     * Emergency fix: يجبر versionCode أن يكون أعلى بكثير من الحالي.
     * يُستخدم عندما يكون التحديث عالقاً بسبب versionCode قديم في السكريبت.
     *
     * newCode = max(nowSeconds + 3600, previousVersionCode + 1)
     * (+3600 = ساعة كاملة فوق أي تثبيت حديث)
     */
    suspend fun forceMonotonicVersionCode(context: Context): MonotonicResult {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val nowPlusBig = nowSeconds + 3600L
        val previousCode = fetchLatestVersionCode(context)
        val newCode = maxOf(nowPlusBig, previousCode + 1)
        return MonotonicResult(
            newVersionCode = newCode,
            previousVersionCode = previousCode,
            currentTimeSeconds = nowSeconds,
            reason = "force:max(now+3600=$nowPlusBig,prev+1=${previousCode + 1})"
        )
    }

    /**
     * يقرأ آخر versionCode من السكريبت عبر get_live_master_state.
     * يُرجع 0 إذا تعذر القراءة.
     */
    private suspend fun fetchLatestVersionCode(context: Context): Long {
        return withContext(Dispatchers.IO) {
            try {
                val apiUrl = context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
                    .getString("apiUrl", GOOGLE_SCRIPT) ?: GOOGLE_SCRIPT
                val url = "$apiUrl?action=get_live_master_state&_t=$nowSeconds"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    val json = JSONObject(body)
                    json.optLong("app_update_version_code", 0L)
                }
            } catch (e: Exception) {
                0L
            }
        }
    }

    /**
     * صيغة "v{versionCode}" للـ GitHub Release tag.
     * Example: v1781990948
     */
    fun versionCodeToTag(versionCode: Long): String = "v$versionCode"

    /**
     * اسم نسخة واضح من versionCode.
     * Example: "LATCHI IPTV 2.1 (b45678)"  (45678 = آخر 5 أرقام من timestamp)
     */
    fun versionCodeToName(versionCode: Long, baseName: String = "LATCHI IPTV"): String {
        val suffix = versionCode.toString().takeLast(5)
        return "$baseName (b$suffix)"
    }

    private val nowSeconds: Long
        get() = System.currentTimeMillis() / 1000L

    /**
     * helper for URL encoding
     */
    fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
}
