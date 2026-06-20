package com.latchi.admin

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppUpdateCenterActivity : AppCompatActivity() {

    companion object {
        private const val GOOGLE_SCRIPT = "https://script.google.com/macros/s/AKfycbxThygspXN6eB8cDUfY7XavKmhXZfewEUfQqd3vARScZ5y7adterInsbXshNkgPgfiF/exec"
        private const val ADMIN_SECRET = "LatchiAdmin2026"
        private const val APP_VERSION = "2.1.0 VIP"
        private const val APP_VERSION_CODE = "3"
    }

    private val prefs by lazy { getSharedPreferences("admin_prefs", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var statusText: TextView? = null
    private var serverStatusCard: TextView? = null
    private var lastPublishCard: TextView? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        buildDashboard()
        checkServerStatus()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)

        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "🚀 تحديث التطبيق",
            subtitle = "App Update Center • GitHub Releases",
            onBack = { finish() }
        ))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        content.addView(buildStatRow())
        content.addView(sectionTitle("🚀 التحديث والنشر"))

        val publishRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        publishRow.addView(VipUiHelper.buildPrimaryButton(
            this, "⬇️ فتح كود ماجيك", VipUiHelper.BtnVariant.NEON_GREEN
        ) {
            startActivity(Intent(this, CodemagicCenterActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginEnd = dp(8) })

        publishRow.addView(VipUiHelper.buildPrimaryButton(
            this, "🚀 نشر آخر Build", VipUiHelper.BtnVariant.GOLD
        ) {
            publishLatestSavedBuildToUsers()
        }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginStart = dp(8) })
        content.addView(publishRow)

        content.addView(VipUiHelper.buildPrimaryButton(
            this, "🌐 نشر رابط APK مباشر (GitHub Releases)", VipUiHelper.BtnVariant.NEON_BLUE
        ) {
            showExternalApkPublishDialog()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply { bottomMargin = dp(12) })

        statusText = TextView(this).apply {
            text = "⏳ جاري فحص حالة النشر..."
            setTextColor(Color.parseColor("#B8C0E0"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        content.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(TextView(this@AppUpdateCenterActivity).apply {
                text = "ℹ️ كيف تخدم هذه الواجهة؟"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@AppUpdateCenterActivity).apply {
                text = "1) ارفع النسخة النهائية كـ Release على GitHub.\n2) خذ رابط APK المباشر الذي ينتهي بـ app-release.apk أو .apk.\n3) الصق الرابط هنا وسيقوم تطبيق المشاهدة بسحبه مباشرة بدون Tokens أو Headers.\n4) خيار Codemagic يبقى متاحًا فقط إذا أردت حفظ Build داخليًا."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(4f, 1.05f)
            })
        })
    }

    private fun buildStatRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(16))
        }

        val serverCard = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        serverCard.addView(TextView(this).apply {
            text = "🌐 السكريبت"
            setTextColor(Color.parseColor("#39FF8B"))
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        serverStatusCard = TextView(this).apply {
            text = "فحص..."
            setTextColor(Color.parseColor("#F2F4FF"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        serverCard.addView(serverStatusCard)
        row.addView(serverCard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })

        val versionCard = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        versionCard.addView(TextView(this).apply {
            text = "📦 النسخة"
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        versionCard.addView(TextView(this).apply {
            text = "v$APP_VERSION\nCode $APP_VERSION_CODE"
            setTextColor(Color.parseColor("#F2F4FF"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(versionCard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6); marginEnd = dp(6) })

        val updateCard = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        updateCard.addView(TextView(this).apply {
            text = "📅 آخر نشر"
            setTextColor(Color.parseColor("#FFB347"))
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        lastPublishCard = TextView(this).apply {
            text = "—"
            setTextColor(Color.parseColor("#F2F4FF"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        updateCard.addView(lastPublishCard)
        row.addView(updateCard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })

        return row
    }

    private fun checkServerStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder().url("$GOOGLE_SCRIPT?action=get_live_master_state").get().build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val ok = res.isSuccessful && (json?.optBoolean("success", false) == true)
                    val revision = json?.optLong("server_revision", 0L) ?: 0L
                    val appUpdateCode = json?.optLong("app_update_version_code", 0L) ?: 0L
                    val appUpdateUrl = json?.optString("app_update_apk_url", "").orEmpty()
                    withContext(Dispatchers.Main) {
                        serverStatusCard?.text = if (ok) "✅ متصل\nRev $revision\nAppCode $appUpdateCode" else "❌ خطأ ${res.code}"
                        serverStatusCard?.setTextColor(if (ok) Color.parseColor("#39FF8B") else Color.parseColor("#FF5577"))
                        statusText?.text = when {
                            !ok -> "⚠️ تعذر قراءة حالة السكريبت"
                            appUpdateUrl.isBlank() -> "🌐 السكريبت جاهز • لا يوجد تحديث منشور بعد"
                            else -> "🌐 السكريبت جاهز للنشر\n📦 آخر VersionCode منشور: $appUpdateCode"
                        }
                        // 🛡️ حفظ آخر app_update_version_code في prefs لمعاينته لاحقاً
                        if (ok) {
                            prefs.edit().putLong("last_known_app_update_version_code", appUpdateCode).apply()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    serverStatusCard?.text = "🔴 غير متصل"
                    serverStatusCard?.setTextColor(Color.parseColor("#FF5577"))
                    statusText?.text = "❌ تعذر الاتصال بالسكريبت: ${e.localizedMessage}"
                }
            }
        }

        val lastVer = prefs.getString("downloaded_version", "").orEmpty()
            .ifBlank { prefs.getString("last_success_version", "").orEmpty() }
        val lastUpdatedAt = prefs.getString("last_publish_time", "").orEmpty()
        lastPublishCard?.text = when {
            lastVer.isNotBlank() && lastUpdatedAt.isNotBlank() -> "$lastVer\n$lastUpdatedAt"
            lastVer.isNotBlank() -> lastVer
            else -> "لا يوجد\nنشر بعد"
        }
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#7FE6FF"))
        textSize = 15f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(dp(4), dp(16), 0, dp(10))
    }

    private fun showExternalApkPublishDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            setBackgroundResource(R.drawable.bg_vip_card)
        }
        container.addView(TextView(this).apply {
            text = "🌐 نشر تحديث من GitHub Releases"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = "ألصق رابط APK مباشر من GitHub Releases (عام وينتهي بـ .apk).\n🛡️ الـ VersionCode يُحسب تلقائياً ليكون أكبر من كل القيم السابقة — لا داعي لإدخاله يدوياً."
            setTextColor(Color.parseColor("#C7B7D8"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        })

        val urlInput = vipEditText("رابط APK مباشر", "https://github.com/.../releases/download/v2.1/app-release.apk", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val versionInput = vipEditText("اسم النسخة", "مثال: LATCHI IPTV 2.1.0 (اختياري)", InputType.TYPE_CLASS_TEXT)
        val notesInput = vipEditText("ملاحظات التحديث", "تحديث جديد من LATCHI IPTV", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val forceCheck = CheckBox(this).apply {
            text = "تحديث إجباري"
            setTextColor(Color.parseColor("#F2F4FF"))
            textSize = 13f
            isChecked = false
            setPadding(0, dp(6), 0, dp(6))
        }

        container.addView(urlInput.first); container.addView(urlInput.second)
        container.addView(versionInput.first); container.addView(versionInput.second)
        container.addView(notesInput.first); container.addView(notesInput.second)
        container.addView(forceCheck)

        var dialogRef: AlertDialog? = null
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        row.addView(VipUiHelper.buildMiniButton(this, "إلغاء", VipUiHelper.BtnVariant.NEON_PURPLE) {
            dialogRef?.dismiss()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        row.addView(VipUiHelper.buildMiniButton(this, "🚀 نشر", VipUiHelper.BtnVariant.GOLD) {
            val apkUrl = urlInput.second.text.toString().trim()
            val version = versionInput.second.text.toString().trim().ifBlank { "LATCHI IPTV Update" }
            val notes = notesInput.second.text.toString().trim().ifBlank { "تحديث جديد من LATCHI IPTV عبر رابط خارجي." }
            if (apkUrl.isBlank() || !apkUrl.startsWith("http", ignoreCase = true)) {
                VipUiHelper.showErrorOverlay(this, "أدخل رابط APK صحيح يبدأ بـ http أو https.")
                return@buildMiniButton
            }
            dialogRef?.dismiss()
            // 🛡️ لا نمرر versionCode - يُحسب تلقائياً في publishExternalApkUpdate
            publishExternalApkUpdate(apkUrl, version, "", notes, forceCheck.isChecked)
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        container.addView(row)

        dialogRef = AlertDialog.Builder(this).setView(container).create()
        dialogRef.show()
    }

    private fun vipEditText(label: String, hint: String, inputType: Int): Pair<TextView, EditText> {
        val tv = VipUiHelper.buildInputLabel(this, label)
        val input = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#7788AA"))
            textSize = 14f
            minLines = if ((inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) 3 else 1
            maxLines = if ((inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) 4 else 1
            setSingleLine((inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0)
            setBackgroundResource(R.drawable.bg_vip_input)
            setPadding(dp(12), 0, dp(12), 0)
        }
        return tv to input
    }

    private fun publishExternalApkUpdate(apkUrl: String, version: String, versionCode: String, notes: String, forceUpdate: Boolean) {
        statusText?.text = "⏳ جاري حساب VersionCode..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 🛡️ ضمان versionCode أكبر من كل القيم السابقة
                val monotonic = VersionCodeHelper.computeMonotonicVersionCode(this@AppUpdateCenterActivity)
                val finalVersionCode = if (versionCode.toLongOrNull() != null &&
                    versionCode.toLong() > monotonic.newVersionCode) {
                    versionCode
                } else {
                    monotonic.newVersionCode.toString()
                }

                withContext(Dispatchers.Main) {
                    statusText?.text = "⏳ جاري نشر رابط APK الخارجي...\nVersionCode: $finalVersionCode (${monotonic.reason})"
                }

                val url = buildString {
                    append(GOOGLE_SCRIPT)
                    append("?action=set_app_update")
                    append("&secret=").append(enc(ADMIN_SECRET))
                    append("&version_code=").append(enc(finalVersionCode))
                    append("&version_name=").append(enc(version))
                    append("&apk_url=").append(enc(apkUrl))
                    append("&force_update=").append(enc(forceUpdate.toString()))
                    append("&notes_ar=").append(enc(notes))
                }
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val json = try { JSONObject(body) } catch (_: Exception) { JSONObject().put("success", false).put("message", body) }
                    if (!response.isSuccessful || !json.optBoolean("success", false)) {
                        throw Exception(json.optString("message", body))
                    }
                    val publishTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    prefs.edit()
                        .putString("downloaded_apk_url", apkUrl)
                        .putString("downloaded_version_code", finalVersionCode)
                        .putString("downloaded_version", version)
                        .putString("published_build_id", "external-url")
                        .putString("last_publish_time", publishTime)
                        .putString("published_version_code", finalVersionCode)
                        .apply()
                    withContext(Dispatchers.Main) {
                        statusText?.text = "✅ تم نشر الرابط الخارجي: $version\nVersionCode: $finalVersionCode"
                        lastPublishCard?.text = "$version\n$publishTime"
                        VipUiHelper.showSuccessOverlay(
                            this@AppUpdateCenterActivity,
                            "🌐 تم نشر الرابط الخارجي",
                            "الإصدار: $version\nVersionCode: $finalVersionCode\nسيظهر التحديث لكل المستخدمين خلال ثوانٍ",
                            "حسناً", {}
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText?.text = "❌ فشل نشر الرابط الخارجي: ${e.localizedMessage}"
                    VipUiHelper.showErrorOverlay(this@AppUpdateCenterActivity, "❌ فشل نشر الرابط الخارجي:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun publishLatestSavedBuildToUsers() {
        val apkUrl = prefs.getString("downloaded_apk_url", "").orEmpty().ifBlank { prefs.getString("last_success_apk_url", "").orEmpty() }
        val rawVersionCode = prefs.getString("downloaded_version_code", "").orEmpty().ifBlank { prefs.getString("last_success_version_code", "").orEmpty() }
        val version = prefs.getString("downloaded_version", "").orEmpty().ifBlank { prefs.getString("last_success_version", "").orEmpty() }.ifBlank { "LATCHI IPTV Update" }

        if (apkUrl.isBlank() || rawVersionCode.isBlank()) {
            VipUiHelper.showWarningOverlay(this, "⚠️ لا يوجد Build محفوظ", "افتح واجهة كود ماجيك أولاً ثم حمّل أو انشر Build جاهز.", "🧙 فتح كود ماجيك", {
                startActivity(Intent(this, CodemagicCenterActivity::class.java))
            }, "إلغاء", {})
            return
        }

        statusText?.text = "⏳ جاري حساب VersionCode..."
        // 🛡️ ضمان versionCode أكبر من كل القيم السابقة (يحل مشكلة update_available=false)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val monotonic = VersionCodeHelper.computeMonotonicVersionCode(this@AppUpdateCenterActivity)
                val newVersionCode = monotonic.newVersionCode.toString()
                // لو rawVersionCode من Codemagic أكبر من المحسوب → استخدمه (احترام Codemagic)
                val finalVersionCode = if (rawVersionCode.toLongOrNull() != null &&
                    rawVersionCode.toLong() > monotonic.newVersionCode) {
                    rawVersionCode
                } else {
                    newVersionCode
                }

                withContext(Dispatchers.Main) {
                    statusText?.text = "⏳ جاري نشر التحديث للمستخدمين...\nVersionCode: $finalVersionCode (${monotonic.reason})"
                }

                val url = buildString {
                    append(GOOGLE_SCRIPT)
                    append("?action=set_app_update")
                    append("&secret=").append(enc(ADMIN_SECRET))
                    append("&version_code=").append(enc(finalVersionCode))
                    append("&version_name=").append(enc(version))
                    append("&apk_url=").append(enc(apkUrl))
                    append("&force_update=false")
                    append("&notes_ar=").append(enc("تحديث جديد من LATCHI IPTV عبر لوحة التحكم."))
                }
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val json = try { JSONObject(body) } catch (_: Exception) { JSONObject().put("success", false).put("message", body) }
                    if (!response.isSuccessful || !json.optBoolean("success", false)) {
                        throw Exception(json.optString("message", body))
                    }
                    val publishTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    prefs.edit()
                        .putString("last_publish_time", publishTime)
                        .putString("published_version_code", finalVersionCode)
                        .putString("last_published_version_code", finalVersionCode)
                        .apply()
                    withContext(Dispatchers.Main) {
                        statusText?.text = "✅ تم نشر التحديث لكل المستخدمين\nVersion: $version\nVersionCode: $finalVersionCode"
                        lastPublishCard?.text = "$version\n$publishTime"
                        VipUiHelper.showSuccessOverlay(
                            this@AppUpdateCenterActivity,
                            "🚀 تم نشر التحديث",
                            "الإصدار: $version\nVersionCode: $finalVersionCode\nالتاريخ: $publishTime\n\nسيظهر التحديث لكل المستخدمين خلال ثوانٍ",
                            "حسناً", {}
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText?.text = "❌ فشل النشر: ${e.localizedMessage}"
                    VipUiHelper.showErrorOverlay(this@AppUpdateCenterActivity, "❌ فشل النشر:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
