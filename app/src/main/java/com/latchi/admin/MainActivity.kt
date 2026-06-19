package com.latchi.admin

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
        private const val APP_VERSION = "3.0.0 CONTROL"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var serverStatusText: TextView? = null
    private var revisionText: TextView? = null

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
        content.addView(sectionTitle("🚀 الواجهات الرئيسية"))

        val horizontalScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val cardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }
        val cardWidth = dp(220)
        cardsRow.addView(dashboardCard("📦", "واجهة تحديث التطبيق", "نشر APK خارجي أو آخر Build للمستخدمين") {
            startActivity(Intent(this, AppUpdateCenterActivity::class.java))
        }, LinearLayout.LayoutParams(cardWidth, dp(180)).apply { marginEnd = dp(10) })
        cardsRow.addView(dashboardCard("🔗", "واجهة تعميم السيرفر", "تغيير السيرفر الرسمي للمستخدمين بسرعة") {
            startActivity(Intent(this, MasterLinkActivity::class.java))
        }, LinearLayout.LayoutParams(cardWidth, dp(180)).apply { marginEnd = dp(10) })
        cardsRow.addView(dashboardCard("🧙", "واجهة كود ماجيك", "إدارة Builds و APK من Codemagic") {
            startActivity(Intent(this, CodemagicCenterActivity::class.java))
        }, LinearLayout.LayoutParams(cardWidth, dp(180)).apply { marginEnd = dp(10) })
        cardsRow.addView(dashboardCard("🗂️", "واجهة بنك المصادر + الفلترة", "حفظ الروابط، اختبارها، اختيار الرسمي، والفلاتر الذكية") {
            startActivity(Intent(this, CategoryVisibilityControlActivity::class.java))
        }, LinearLayout.LayoutParams(cardWidth, dp(180)))
        horizontalScroll.addView(cardsRow)
        content.addView(horizontalScroll, cardLp())

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
                text = "🌐 حالة المنظومة"
                setTextColor(Color.parseColor("#7FE6FF"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
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
            addView(serverStatusText)
            addView(revisionText)
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
                    withContext(Dispatchers.Main) {
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
