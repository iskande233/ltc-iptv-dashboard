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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Dashboard رئيسي لـ LATCHI IPTV Admin — النسخة VIP.
 *
 * الإصلاحات الجذرية:
 * 1. تمت إضافة زر "🗑️ تنظيف جميع المستخدمين" لمسح قاعدة بيانات Google Sheet بضغطة زر.
 * 2. تمت إضافة شاشات الذكاء الاصطناعي البارزة (Gemini AI Assistant و Xtream Tester).
 * 3. كل زر يفتح Activity مستقلة لضمان أقصى درجات الاستقرار.
 * 4. واجهة VIP Dark Theme بتدرج Royal Blue → Midnight Blue مع حدود نيون.
 * 5. بطاقات Stat Cards في الأعلى تعرض حالة السيرفر، رقم الإصدار، تاريخ آخر تحديث.
 * 6. كل المحتوى داخل ScrollView لاستجابة كاملة على جميع أحجام الهواتف.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val GOOGLE_SCRIPT = "https://script.google.com/macros/s/AKfycbwoxD7eNi6AVvhw9l_hPzaUkVt1F9U6trUXs28QYuNld_Ip15ZoefcTAdkd4B_DqoGO/exec"
        private const val ADMIN_SECRET = "LatchiAdmin2026"
        private const val DEFAULT_CODEMAGIC_TOKEN = "9OVMA35F09K3nv1djPFqSnQIQCKkq_b4_twyExdllp4"
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

        if (prefs.getString("codemagic_token", "").isNullOrBlank()) {
            prefs.edit().putString("codemagic_token", DEFAULT_CODEMAGIC_TOKEN).apply()
        }
        prefs.edit().putString("apiUrl", GOOGLE_SCRIPT).apply()

        buildDashboard()
        checkServerStatus()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)

        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "LATCHI IPTV Dashboard",
            subtitle = "Royal Admin Control Center • VIP",
            onBack = { /* لا يوجد رجوع */ }
        ).apply {
            val back = getChildAt(0) as TextView
            back.text = "👑"
            back.setOnClickListener { /* لا شيء */ }
            back.setTextColor(Color.parseColor("#FFD700"))
        })

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

        // ===== Stat Cards (3 بطاقات) =====
        content.addView(buildStatRow())

        // ===== Section title: النشر =====
        content.addView(sectionTitle("🚀 النشر والتحديث"))

        val publishRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        publishRow.addView(VipUiHelper.buildPrimaryButton(
            this, "⬇️ تحميل APK", VipUiHelper.BtnVariant.NEON_GREEN
        ) {
            startActivity(Intent(this, CodemagicCenterActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginEnd = dp(8) })

        publishRow.addView(VipUiHelper.buildPrimaryButton(
            this, "🚀 نشر للمستخدمين", VipUiHelper.BtnVariant.GOLD
        ) {
            publishLatestSavedBuildToUsers()
        }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginStart = dp(8) })
        content.addView(publishRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        content.addView(VipUiHelper.buildPrimaryButton(
            this, "🌐 نشر رابط APK خارجي", VipUiHelper.BtnVariant.NEON_BLUE
        ) {
            showExternalApkPublishDialog()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply { bottomMargin = dp(12) })

        statusText = TextView(this).apply {
            text = "⏳ جاري فحص آخر تحديث..."
            setTextColor(Color.parseColor("#B8C0E0"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        content.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })


        // ===== Section title: ✦ الذكاء الاصطناعي وفحص الروابط =====
        content.addView(sectionTitle("✦ الذكاء الاصطناعي وفحص الروابط"))

        val geminiCard = vipActionCard("✦", "المساعد الذكي Gemini AI", "أوامر صوتية بالدارجة 🇩🇿 • إدارة التطبيق بالكامل", VipUiHelper.BtnVariant.GOLD) {
            startActivity(Intent(this, GeminiAssistantActivity::class.java))
        }
        content.addView(geminiCard, cardLp().apply { bottomMargin = dp(12) })

        val xtreamTesterCard = vipActionCard("🔍", "فاحص الأكواد الذكي", "Xtream & M3U Tester • فحص 100 رابط دفعة واحدة", VipUiHelper.BtnVariant.NEON_GREEN) {
            startActivity(Intent(this, XtreamTesterActivity::class.java))
        }
        content.addView(xtreamTesterCard, cardLp().apply { bottomMargin = dp(16) })


        // ===== Section title: الأدوات الإدارية =====
        content.addView(sectionTitle("🛠️ الأدوات الإدارية"))

        // Row 1: الغربال | كود ماجيك
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(vipActionCard("🧹", "واجهة الغربال", "تصفية M3U + AI",
            VipUiHelper.BtnVariant.NEON_BLUE) {
            startActivity(Intent(this, SanitizerActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(150), 1f).apply { marginEnd = dp(8) })

        row1.addView(vipActionCard("🧙", "واجهة كود ماجيك", "Token + Build",
            VipUiHelper.BtnVariant.NEON_PURPLE) {
            startActivity(Intent(this, CodemagicCenterActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(150), 1f).apply { marginStart = dp(8) })
        content.addView(row1, cardLp().apply { bottomMargin = dp(12) })

        // Row 2: إضافة مستخدم | تعميم الرابط
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(vipActionCard("👤", "إضافة مستخدم", "توليد كود",
            VipUiHelper.BtnVariant.GOLD) {
            startActivity(Intent(this, AddUserActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(150), 1f).apply { marginEnd = dp(8) })

        row2.addView(vipActionCard("🔗", "تعميم الرابط", "Broadcast Server",
            VipUiHelper.BtnVariant.NEON_GREEN) {
            startActivity(Intent(this, MasterLinkActivity::class.java))
        }, LinearLayout.LayoutParams(0, dp(150), 1f).apply { marginStart = dp(8) })
        content.addView(row2, cardLp().apply { bottomMargin = dp(12) })

        // Row 3: إدارة المستخدمين والسيرفرات
        content.addView(vipActionCard("👥", "إدارة المستخدمين والسيرفرات", "Users + Servers Manager",
            VipUiHelper.BtnVariant.NEON_BLUE) {
            startActivity(Intent(this, ServerListActivity::class.java))
        }, cardLp().apply { bottomMargin = dp(12) })

        // ===== زر جديد: 🗑️ تنظيف جميع المستخدمين =====
        content.addView(vipActionCard("🗑️", "تنظيف جميع المستخدمين", "مسح كل الحسابات والأكواد من Google Sheets بضغطة زر 🧹",
            VipUiHelper.BtnVariant.NEON_PURPLE) {
            showDeleteAllUsersConfirmDialog()
        }, cardLp().apply { bottomMargin = dp(12) })

        // Row 4: إضافة MAC
        content.addView(vipActionCard("📟", "إضافة MAC / Stalker", "Portal + MAC Address",
            VipUiHelper.BtnVariant.NEON_PURPLE) {
            startActivity(Intent(this, AddMacActivity::class.java))
        }, cardLp().apply { bottomMargin = dp(20) })

        // Footer
        content.addView(TextView(this).apply {
            text = "LATCHI IPTV Dashboard v$APP_VERSION\n© 2026 — VIP Edition"
            setTextColor(Color.parseColor("#7A82A8"))
            textSize = 11f
            gravity = Gravity.CENTER
            setLineSpacing(4f, 1.05f)
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun showDeleteAllUsersConfirmDialog() {
        VipUiHelper.showWarningOverlay(
            this,
            title = "⚠️ تحذير مَلكي خطير!",
            message = "هل أنت متأكد 100% أنك تريد مسح كل المشتركين والأكواد من قاعدة بيانات Google Sheets؟\nهذا الإجراء سيقوم بتنظيف القائمة بالكامل ولا يمكن التراجع عنه!",
            primaryText = "🗑️ نعم، نظّف قاعدة البيانات",
            onPrimary = { executeDeleteAllUsers() },
            secondaryText = "إلغاء",
            onSecondary = {}
        )
    }

    private fun executeDeleteAllUsers() {
        statusText?.text = "⏳ جاري تنظيف كل المستخدمين من قاعدة البيانات..."
        val pd = AlertDialog.Builder(this)
            .setTitle("🧹 تنظيف قاعدة البيانات")
            .setMessage("جاري جلب وحذف جميع المستخدمين...")
            .setCancelable(false)
            .create()
        pd.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encSecret = enc(ADMIN_SECRET)
                
                // 1. نجرب إرسال action=delete_all_users
                val deleteAllUrl = "$GOOGLE_SCRIPT?action=delete_all_users&secret=$encSecret"
                val req1 = Request.Builder().url(deleteAllUrl).get().build()
                client.newCall(req1).execute().use { res1 ->
                    val body1 = res1.body?.string().orEmpty()
                    try {
                        val j1 = JSONObject(body1)
                        if (j1.optBoolean("success", false)) {
                            withContext(Dispatchers.Main) {
                                pd.dismiss()
                                statusText?.text = "✅ تم تنظيف جميع المستخدمين بنجاح!"
                                VipUiHelper.showSuccessOverlay(
                                    this@MainActivity,
                                    "🧹 تم التنظيف",
                                    "تم مسح جميع المشتركين والأكواد من Google Sheet بنجاح ✓",
                                    "رائع!",
                                    {}
                                )
                            }
                            return@launch
                        }
                    } catch (_: Exception) {}
                }

                // 2. إذا لم ينجح أو لم يكن مدعوماً، نستخدم حلقة (Loop) تجلب الجميع وتحذفهم
                val getAllUrl = "$GOOGLE_SCRIPT?action=get_all_users&secret=$encSecret"
                val req2 = Request.Builder().url(getAllUrl).get().build()
                client.newCall(req2).execute().use { res2 ->
                    val body2 = res2.body?.string().orEmpty()
                    val json2 = JSONObject(body2)
                    val arr = json2.optJSONArray("users") ?: JSONArray()
                    
                    val total = arr.length()
                    if (total == 0) {
                        withContext(Dispatchers.Main) {
                            pd.dismiss()
                            statusText?.text = "✅ قاعدة البيانات نظيفة بالفعل (لا يوجد مستخدمون)."
                            VipUiHelper.showSuccessOverlay(this@MainActivity, "🧹 قاعدة البيانات نظيفة", "لا يوجد أي مستخدمين في القائمة ✓", "حسناً", {})
                        }
                        return@launch
                    }

                    var deletedCount = 0
                    for (i in 0 until total) {
                        val u = arr.getJSONObject(i)
                        val code = u.optString("code")
                        if (code.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                pd.setMessage("جاري مسح المستخدم ${i + 1} / $total\n(الكود: $code)...")
                            }
                            val delUrl = "$GOOGLE_SCRIPT?action=delete_user&secret=$encSecret&code=${enc(code)}"
                            try {
                                client.newCall(Request.Builder().url(delUrl).get().build()).execute().close()
                                deletedCount++
                            } catch (_: Exception) {}
                        }
                    }

                    withContext(Dispatchers.Main) {
                        pd.dismiss()
                        statusText?.text = "✅ تم تنظيف $deletedCount / $total مستخدم."
                        VipUiHelper.showSuccessOverlay(
                            this@MainActivity,
                            "🧹 تم التنظيف الشامل",
                            "تم مسح $deletedCount مستخدم من قاعدة بيانات Google Sheets بنجاح ✓",
                            "ممتاز!",
                            {}
                        )
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    statusText?.text = "❌ فشل التنظيف: ${e.localizedMessage}"
                    VipUiHelper.showErrorOverlay(this@MainActivity, "❌ حدث خطأ أثناء التنظيف:\n${e.localizedMessage}")
                }
            }
        }
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
            text = "🟢 السيرفر"
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
            text = "📦 الإصدار"
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        versionCard.addView(TextView(this).apply {
            text = "v$APP_VERSION\n(Code $APP_VERSION_CODE)"
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
            text = "📅 آخر تحديث"
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

    private fun sectionTitle(t: String): TextView {
        return TextView(this).apply {
            text = t
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(16), 0, dp(10))
        }
    }

    private fun vipActionCard(emoji: String, title: String, subtitle: String, variant: VipUiHelper.BtnVariant, onClick: () -> Unit): LinearLayout {
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
                textSize = 28f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(
                    when (variant) {
                        VipUiHelper.BtnVariant.GOLD -> Color.parseColor("#FFD700")
                        VipUiHelper.BtnVariant.NEON_BLUE -> Color.parseColor("#7FE6FF")
                        VipUiHelper.BtnVariant.NEON_GREEN -> Color.parseColor("#39FF8B")
                        VipUiHelper.BtnVariant.NEON_PURPLE -> Color.parseColor("#A06BFF")
                    }
                )
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun checkServerStatus() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder().url(GOOGLE_SCRIPT).get().build()
                client.newCall(req).execute().use { res ->
                    val ok = res.isSuccessful
                    withContext(Dispatchers.Main) {
                        serverStatusCard?.text = if (ok) "✅ متصل" else "❌ خطأ ${res.code}"
                        serverStatusCard?.setTextColor(
                            if (ok) Color.parseColor("#39FF8B") else Color.parseColor("#FF5577")
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    serverStatusCard?.text = "🔴 غير متصل"
                    serverStatusCard?.setTextColor(Color.parseColor("#FF5577"))
                }
            }
        }

        val lastPub = prefs.getString("published_build_id", "").orEmpty()
        val lastVer = prefs.getString("last_success_version", "").orEmpty()
        val lastUpdatedAt = prefs.getString("last_publish_time", "").orEmpty()
        if (lastVer.isNotBlank() || lastPub.isNotBlank()) {
            lastPublishCard?.text = if (lastVer.isNotBlank()) {
                if (lastUpdatedAt.isNotBlank()) "$lastVer\n$lastUpdatedAt" else lastVer
            } else "—"
        } else {
            lastPublishCard?.text = "لا يوجد\nنشر بعد"
        }
    }

    private fun showExternalApkPublishDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            setBackgroundResource(R.drawable.bg_vip_card)
        }
        container.addView(TextView(this).apply {
            text = "🌐 نشر تحديث من رابط خارجي"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = "ألصق رابط APK مباشر، ثم أدخل VersionCode أعلى من نسخة التطبيق الحالية. سيتم إرساله إلى Google Script ليظهر للمستخدمين داخل تطبيق المشاهدة."
            setTextColor(Color.parseColor("#C7B7D8"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        })

        val urlInput = vipEditText("رابط APK مباشر", "https://.../app-release.apk", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val versionInput = vipEditText("اسم النسخة", "1.1.4", InputType.TYPE_CLASS_TEXT)
        val codeInput = vipEditText("VersionCode", "مثال: 1781557885", InputType.TYPE_CLASS_NUMBER)
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
        container.addView(codeInput.first); container.addView(codeInput.second)
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
            val versionCode = codeInput.second.text.toString().trim()
            val notes = notesInput.second.text.toString().trim().ifBlank { "تحديث جديد من LATCHI IPTV عبر رابط خارجي." }
            if (apkUrl.isBlank() || !apkUrl.startsWith("http", ignoreCase = true) || versionCode.isBlank()) {
                VipUiHelper.showErrorOverlay(this, "أدخل رابط APK صحيح و VersionCode.")
                return@buildMiniButton
            }
            dialogRef?.dismiss()
            publishExternalApkUpdate(apkUrl, version, versionCode, notes, forceCheck.isChecked)
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
        statusText?.text = "⏳ جاري نشر رابط APK الخارجي..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = buildString {
                    append(GOOGLE_SCRIPT)
                    append("?action=set_app_update")
                    append("&secret=").append(enc(ADMIN_SECRET))
                    append("&version_code=").append(enc(versionCode))
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
                        .putString("downloaded_version_code", versionCode)
                        .putString("downloaded_version", version)
                        .putString("published_build_id", "external-url")
                        .putString("last_publish_time", publishTime)
                        .apply()
                    withContext(Dispatchers.Main) {
                        statusText?.text = "✅ تم نشر الرابط الخارجي: $version"
                        lastPublishCard?.text = "$version\n$publishTime"
                        VipUiHelper.showSuccessOverlay(
                            this@MainActivity,
                            title = "🌐 تم نشر الرابط الخارجي",
                            message = "الإصدار: $version\nVersionCode: $versionCode\nسيظهر التحديث داخل تطبيق المشاهدة، وسيتم التحميل والتثبيت من داخل التطبيق.",
                            primaryText = "حسناً",
                            onPrimary = {}
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText?.text = "❌ فشل نشر الرابط الخارجي: ${e.localizedMessage}"
                    VipUiHelper.showErrorOverlay(this@MainActivity, "❌ فشل نشر الرابط الخارجي:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun publishLatestSavedBuildToUsers() {
        val apkUrl = prefs.getString("downloaded_apk_url", "").orEmpty()
            .ifBlank { prefs.getString("last_success_apk_url", "").orEmpty() }
        val versionCode = prefs.getString("downloaded_version_code", "").orEmpty()
            .ifBlank { prefs.getString("last_success_version_code", "").orEmpty() }
        val version = prefs.getString("downloaded_version", "").orEmpty()
            .ifBlank { prefs.getString("last_success_version", "").orEmpty() }
            .ifBlank { "LATCHI IPTV Update" }

        if (apkUrl.isBlank() || versionCode.isBlank()) {
            VipUiHelper.showWarningOverlay(
                this,
                title = "⚠️ لا يوجد Build محفوظ",
                message = "افتح واجهة كود ماجيك، اجلب آخر بناء ناجح وحمّل الـ APK أولاً، ثم اضغط زر النشر العلوي من جديد.",
                primaryText = "🧙 فتح كود ماجيك",
                onPrimary = { startActivity(Intent(this, CodemagicCenterActivity::class.java)) },
                secondaryText = "إلغاء",
                onSecondary = {}
            )
            return
        }

        statusText?.text = "⏳ جاري نشر التحديث للمستخدمين..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = buildString {
                    append(GOOGLE_SCRIPT)
                    append("?action=set_app_update")
                    append("&secret=").append(enc(ADMIN_SECRET))
                    append("&version_code=").append(enc(versionCode))
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
                        .putString("published_build_id", prefs.getString("downloaded_build_id", ""))
                        .putString("last_publish_time", publishTime)
                        .apply()
                    withContext(Dispatchers.Main) {
                        statusText?.text = "✅ تم نشر التحديث لكل المستخدمين: $version"
                        lastPublishCard?.text = "$version\n$publishTime"
                        VipUiHelper.showSuccessOverlay(
                            this@MainActivity,
                            title = "🚀 تم نشر التحديث",
                            message = "الإصدار: $version\nالتاريخ: $publishTime\nتم إخطار كل المستخدمين في تطبيق المشاهدة ✓",
                            primaryText = "حسناً",
                            onPrimary = {}
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText?.text = "❌ فشل النشر: ${e.localizedMessage}"
                    VipUiHelper.showErrorOverlay(this@MainActivity, "❌ فشل النشر:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun cardLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
}
