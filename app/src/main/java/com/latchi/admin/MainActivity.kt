package com.latchi.admin

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * اللوحة الرئيسية الجديدة — بسيطة وواضحة.
 * 4 واجهات فقط:
 * 1) تحديث التطبيق
 * 2) تعميم السيرفر
 * 3) كود ماجيك
 * 4) بنك المصادر + الفلترة الذكية
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val GOOGLE_SCRIPT = "https://script.google.com/macros/s/AKfycbxThygspXN6eB8cDUfY7XavKmhXZfewEUfQqd3vARScZ5y7adterInsbXshNkgPgfiF/exec"
        private const val ADMIN_SECRET = "LatchiAdmin2026"
        private const val APP_VERSION = "3.0.0 CONTROL"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var serverStatusText: TextView? = null
    private var revisionText: TextView? = null
    private var currentSourceTitleText: TextView? = null
    private var currentSourceDateText: TextView? = null
    private var appModeText: TextView? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        buildUi()
        loadRemoteStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)

        root.addView(
            VipUiHelper.buildTopBar(
                this,
                title = "LATCHI Control Center",
                subtitle = "4 واجهات فقط • سريع وواضح",
                onBack = {}
            ).apply {
                val back = getChildAt(0) as TextView
                back.text = "👑"
                back.setOnClickListener { }
                back.setTextColor(Color.parseColor("#FFD700"))
            }
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        content.addView(buildStatusCard())
        content.addView(buildModeSwitchCard(), cardLp())
        content.addView(sectionTitle("🚀 الواجهات الرئيسية"))
        content.addView(dashboardCard("📦", "واجهة تحديث التطبيق", "نشر APK خارجي أو آخر Build للمستخدمين") {
            startActivity(Intent(this, AppUpdateCenterActivity::class.java))
        }, cardLp())
        content.addView(dashboardCard("🔗", "واجهة تعميم السيرفر", "تغيير السيرفر الرسمي للمستخدمين بسرعة") {
            startActivity(Intent(this, MasterLinkActivity::class.java))
        }, cardLp())
        content.addView(dashboardCard("🧙", "واجهة كود ماجيك", "إدارة Builds و APK من Codemagic") {
            startActivity(Intent(this, CodemagicCenterActivity::class.java))
        }, cardLp())
        content.addView(dashboardCard("🗂️", "واجهة بنك المصادر + الفلترة", "حفظ الروابط، اختبارها، اختيار الرسمي، والفلاتر الذكية") {
            startActivity(Intent(this, CategoryVisibilityControlActivity::class.java))
        }, cardLp())
        content.addView(dashboardCard("🎨", "واجهة منظم الفئات", "إعادة تسمية + ترتيب فئات كل سيرفر محفوظ قبل البث") {
            startActivity(Intent(this, CategoryOrganizerActivity::class.java))
        }, cardLp())

        content.addView(sectionTitle("👥 المستخدمون"))
        content.addView(dashboardCard("➕", "واجهة إضافة مستخدم جديد", "توليد كود جديد وتسجيله في Google Sheet") {
            startActivity(Intent(this, AddUserActivity::class.java))
        }, cardLp())
        content.addView(dashboardCard("👥", "واجهة إدارة المستخدمين", "عرض كل المستخدمين، تحديد متعدد، حذف المحددين أو الكل") {
            startActivity(Intent(this, UserManagementActivity::class.java))
        }, cardLp())
        content.addView(dashboardCard("🗑️", "حذف جميع المستخدمين", "تنظيف كل المستخدمين من Google Sheet") {
            showDeleteAllUsersConfirmDialog()
        }, cardLp())

        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(TextView(this@MainActivity).apply {
                text = "🧠 كيف ستخدم منظومة الروابط؟"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "1) تخزن روابط M3U / Xtream الشغالة في بنك المصادر داخل الهاتف.\n2) تختبرها وتشوف expiry والحالة والسرعة.\n3) تعلّم رابط واحد على أنه Active / Official.\n4) من واجهة التعميم تضغط نشر، والسكربت يرفع revision للمستخدمين.\n5) من واجهة الفلترة تضبط hidden categories و beIN / ALWAN keywords."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setLineSpacing(4f, 1.05f)
                setPadding(0, dp(8), 0, 0)
            })
        }, cardLp())

        content.addView(TextView(this).apply {
            text = "LATCHI Dashboard v$APP_VERSION"
            setTextColor(Color.parseColor("#7A82A8"))
            textSize = 11f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(16), 0, 0)
        })
    }

    private fun buildStatusCard(): LinearLayout {
        return VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(TextView(this@MainActivity).apply {
                text = "📡 السيرفر المعمم الحالي"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            currentSourceTitleText = TextView(this@MainActivity).apply {
                text = "—"
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(10), 0, 0)
            }
            currentSourceDateText = TextView(this@MainActivity).apply {
                text = "—"
                setTextColor(Color.parseColor("#39FF8B"))
                textSize = 12f
                setPadding(0, dp(4), 0, 0)
            }
            serverStatusText = TextView(this@MainActivity).apply {
                text = "⏳ جاري فحص السكريبت..."
                setTextColor(Color.parseColor("#F2F4FF"))
                textSize = 13f
                setPadding(0, dp(10), 0, 0)
            }
            revisionText = TextView(this@MainActivity).apply {
                text = "Revision: --"
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(4), 0, 0)
            }
            addView(currentSourceTitleText)
            addView(currentSourceDateText)
            addView(serverStatusText)
            addView(revisionText)
        }
    }

    private fun buildModeSwitchCard(): LinearLayout {
        return VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(TextView(this@MainActivity).apply {
                text = "🔀 وضع التطبيق"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            appModeText = TextView(this@MainActivity).apply {
                text = "⏳ جاري القراءة..."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 13f
                setPadding(0, dp(6), 0, dp(10))
            }
            addView(appModeText)
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(VipUiHelper.buildMiniButton(this@MainActivity, "🔓 تفعيل المجاني", VipUiHelper.BtnVariant.NEON_GREEN) {
                switchAppMode("free")
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
            row.addView(VipUiHelper.buildMiniButton(this@MainActivity, "🔐 تفعيل VIP", VipUiHelper.BtnVariant.GOLD) {
                switchAppMode("vip")
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
            addView(row)
            addView(TextView(this@MainActivity).apply {
                text = "المجاني: يدخل مباشرة من غير كود ويستعمل السيرفر المعمم.\nVIP: يطلب كود تفعيل وتبقى كل أدوات المستخدمين والكودات شغالة."
                setTextColor(Color.parseColor("#8891B8"))
                textSize = 11f
                setPadding(0, dp(10), 0, 0)
            })
        }
    }

    private fun switchAppMode(mode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = JSONObject().apply {
                    put("action", "toggle_app_mode")
                    put("password", "iskander_khantouche_2026")
                    put("secret", ADMIN_SECRET)
                    put("mode", mode)
                }.toString()
                val req = Request.Builder()
                    .url(GOOGLE_SCRIPT)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                val json = client.newCall(req).execute().use { res -> JSONObject(res.body?.string().orEmpty()) }
                if (!json.optBoolean("success", json.optString("status") == "success")) throw Exception(json.optString("message", "فشل تغيير الوضع"))
                withContext(Dispatchers.Main) {
                    appModeText?.text = if (mode == "free") "🔓 الوضع الحالي: مجاني" else "🔐 الوضع الحالي: VIP"
                    VipUiHelper.showSuccessOverlay(this@MainActivity, "✅ تم التغيير", json.optString("message", "تم تغيير وضع التطبيق"), "حسناً", {})
                    loadRemoteStatus()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { VipUiHelper.showErrorOverlay(this@MainActivity, "❌ فشل تغيير الوضع:\n${e.localizedMessage}") }
            }
        }
    }

    private fun showDeleteAllUsersConfirmDialog() {
        VipUiHelper.showWarningOverlay(
            this,
            title = "⚠️ تحذير خطير",
            message = "هل تريد حذف جميع المستخدمين من Google Sheet؟ هذا الإجراء لا يمكن التراجع عنه.",
            primaryText = "🗑️ نعم، احذف الكل",
            onPrimary = { executeDeleteAllUsers() },
            secondaryText = "إلغاء",
            onSecondary = {}
        )
    }

    private fun executeDeleteAllUsers() {
        val progress = AlertDialog.Builder(this)
            .setTitle("🧹 تنظيف قاعدة البيانات")
            .setMessage("جاري حذف جميع المستخدمين...")
            .setCancelable(false)
            .create()
        progress.show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "$GOOGLE_SCRIPT?action=delete_all_users&secret=${enc(ADMIN_SECRET)}"
                val response = client.newCall(Request.Builder().url(url).get().build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                    JSONObject(body)
                }
                if (!response.optBoolean("success", false)) throw Exception(response.optString("message", "فشل الحذف"))
                val deleted = response.optInt("deleted_count", 0)
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    VipUiHelper.showSuccessOverlay(this@MainActivity, "✅ تم الحذف", "تم حذف $deleted مستخدم من Google Sheet", "حسناً", {})
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    VipUiHelper.showErrorOverlay(this@MainActivity, "❌ فشل حذف المستخدمين:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun loadRemoteStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url("$GOOGLE_SCRIPT?action=get_live_master_state").get().build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val ok = response.isSuccessful && (json?.optBoolean("success", false) == true)
                    val rev = json?.optLong("server_revision", 0L) ?: 0L
                    val masterUrl = json?.optString("master_url", json.optString("default_playlist_url", "")) ?: ""
                    val appMode = json?.optString("app_mode", "vip") ?: "vip"
                    withContext(Dispatchers.Main) {
                        val publishedName = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("published_source_name", "")?.trim().orEmpty()
                        val publishedExpiry = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("published_source_expiry", "")?.trim().orEmpty()
                        currentSourceTitleText?.text = publishedName.ifBlank { "غير محدد" }
                        currentSourceDateText?.text = publishedExpiry.ifBlank { "—" }
                        serverStatusText?.text = if (ok) "✅ السكريبت متصل ويشتغل" else "❌ فشل الاتصال بالسكريبت"
                        serverStatusText?.setTextColor(if (ok) Color.parseColor("#39FF8B") else Color.parseColor("#FF5577"))
                        revisionText?.text = if (masterUrl.isNotBlank()) "Revision: $rev\nMaster: ${masterUrl.take(60)}..." else "Revision: $rev"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    serverStatusText?.text = "❌ تعذر الاتصال: ${e.localizedMessage}"
                    serverStatusText?.setTextColor(Color.parseColor("#FF5577"))
                }
            }
        }
    }

    private fun dashboardCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_vip_card)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = emoji
                textSize = 30f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#7FE6FF"))
        textSize = 15f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(dp(4), dp(12), 0, dp(10))
    }

    private fun cardLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(12) }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
