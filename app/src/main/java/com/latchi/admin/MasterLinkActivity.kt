package com.latchi.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * واجهة تعميم السيرفر الموحد — صفحة مستقلة تماماً.
 *
 * الإصلاح الكارثي: كانت سابقاً تنقل لنفس AdminActivity،
 * الآن هي شاشة مخصصة مع أزرار النسخ/اللصق وعرض حالة النشر.
 */
class MasterLinkActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_API_URL = "https://script.google.com/macros/s/AKfycbzuPV0N6lmytlgWd5EO21Wpxj1cqkKFMZ1n_T4ANsofXuk5BTW499hLYRWiHAazyX-E/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    private lateinit var inputMasterUrl: EditText
    private lateinit var inputMasterExpiry: EditText
    private lateinit var txtLog: TextView
    private lateinit var progressOverlay: View
    private lateinit var progressStatus: TextView
    private lateinit var txtMasterInfoCard: TextView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)

        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "🔗 تعميم السيرفر الموحد",
            subtitle = "Broadcast Master Server",
            onBack = { finish() }
        ))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        // ===== Description card =====
        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(TextView(this@MasterLinkActivity).apply {
                text = "🌍 سيرفر واحد لكل المستخدمين"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MasterLinkActivity).apply {
                text = "ألصق رابط M3U / Xtream هنا واضغط تعميم. السكريبت يبدل playlist_url لكل الأكواد ويخليه Default للكودات الجديدة."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            })
        }, cardLp())

        // ===== Master URL with paste =====
        content.addView(VipUiHelper.buildInputLabel(this, "🌐 رابط السيرفر الموحد"))
        val urlContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        inputMasterUrl = EditText(this).apply {
            hint = "https://server.com/get.php?username=xxx&password=xxx&type=m3u_plus"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            minLines = 2
            setSingleLine(false)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isLongClickable = true
        }
        urlContainer.addView(inputMasterUrl, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val urlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        urlRow.addView(VipUiHelper.buildMiniButton(
            this, "📋 لصق", VipUiHelper.BtnVariant.NEON_BLUE
        ) {
            VipUiHelper.pasteFromClipboard(this@MasterLinkActivity) { txt ->
                inputMasterUrl.setText(txt)
                inputMasterUrl.setSelection(txt.length)
            }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })
        urlRow.addView(VipUiHelper.buildMiniButton(
            this, "🧹 مسح", VipUiHelper.BtnVariant.NEON_PURPLE
        ) { inputMasterUrl.setText("") }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        urlRow.addView(VipUiHelper.buildMiniButton(
            this, "🔎 اختبار", VipUiHelper.BtnVariant.GOLD
        ) {
            val url = inputMasterUrl.text.toString().replace(" ", "").replace("&amp;", "&").trim()
            if (url.isBlank()) {
                VipUiHelper.showErrorOverlay(this, "ألصق الرابط أولاً لاختباره.")
            } else {
                showProgress("جاري فحص استجابة السيرفر وتاريخ الانتهاء...")
                CoroutineScope(Dispatchers.IO).launch {
                    val info = XtreamMasterInfoHelper.fetchInfo(url)
                    withContext(Dispatchers.Main) {
                        hideProgress()
                        if (info.success) {
                            val details = "🟢 الحالة: ${info.status}\n📅 تاريخ الانتهاء الحقيقي: ${info.expiryDate} (يتبقى ${info.daysLeft} يوم)\n📱 الحد الأقصى للأجهزة: ${info.maxConnections}\n⚡ المتصلون الآن: ${info.activeConnections}"
                            txtMasterInfoCard.text = details
                            appendLog("✅ تم جلب معلومات السيرفر الحقيقية: ينتهي في ${info.expiryDate}")
                            VipUiHelper.showSuccessOverlay(this@MasterLinkActivity, "✅ السيرفر شغال ومستقر", details, "حسناً", {})
                        } else {
                            txtMasterInfoCard.text = "❌ فشل جلب تفاصيل الحساب: ${info.expiryDate}"
                            appendLog("❌ فشل استخراج تاريخ الانتهاء")
                            VipUiHelper.showWarningOverlay(this@MasterLinkActivity, "⚠️ السيرفر يعطي خطأ", "لم نتمكن من جلب user_info. قد يكون الرابط M3U عادي وليس Xtream.", "حسناً", {})
                        }
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, dp(40), 1.2f).apply { marginStart = dp(6) })
        urlRow.addView(VipUiHelper.buildMiniButton(
            this, "📤 للتطبيق", VipUiHelper.BtnVariant.NEON_GREEN
        ) {
            val url = inputMasterUrl.text.toString().replace(" ", "").replace("&amp;", "&").trim()
            if (url.isNotBlank()) {
                try {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse("latchiiptv://master?url=" + URLEncoder.encode(url, "UTF-8"))).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(i)
                    appendLog("✅ تم إرسال الرابط لتطبيق المشاهدة")
                } catch (e: Exception) {
                    appendLog("⚠️ تطبيق المشاهدة غير مثبت: ${e.message}")
                    VipUiHelper.showWarningOverlay(this@MasterLinkActivity, "⚠️ تطبيق المشاهدة غير مثبت",
                        "تم حفظ الرابط محلياً. ثبّت تطبيق LTC IPTV ثم أعد المحاولة.",
                        "حسناً", {}, null, null)
                }
            }
        }, LinearLayout.LayoutParams(0, dp(40), 1.3f).apply { marginStart = dp(6) })
        urlContainer.addView(urlRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(urlContainer, cardLp())

        // ===== Link expiry =====
        content.addView(VipUiHelper.buildInputLabel(this, "⏳ تاريخ نهاية الرابط (اختياري)"))
        val expContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        inputMasterExpiry = EditText(this).apply {
            hint = "2027-12-30"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            inputType = android.text.InputType.TYPE_CLASS_DATETIME
            setSingleLine(true)
        }
        expContainer.addView(inputMasterExpiry, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(expContainer, cardLp())

        // ===== Master True Master Expiry Info Card =====
        val infoContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(TextView(this@MasterLinkActivity).apply {
                text = "📊 تفاصيل السيرفر الموحد الحقيقية (الذاكرة الاصطناعية)"
                setTextColor(Color.parseColor("#7FE6FF"))
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            txtMasterInfoCard = TextView(this@MasterLinkActivity).apply {
                text = "ألصق الرابط واضغط '🔎 اختبار' لجلب تاريخ الانتهاء الحقيقي والأجهزة المتصلة."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setLineSpacing(5f, 1.1f)
                setPadding(0, dp(8), 0, 0)
            }
            addView(txtMasterInfoCard)
        }
        content.addView(infoContainer, cardLp())

        // ===== Submit buttons (broadcast) =====
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        btnRow.addView(VipUiHelper.buildPrimaryButton(
            this, "⚡ تعميم السيرفر الكامل المباشر", VipUiHelper.BtnVariant.GOLD
        ) { submitMasterUrl() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(10) })

        btnRow.addView(VipUiHelper.buildPrimaryButton(
            this, "👁️ التعميم المتقدم مع التحكم وإخفاء الفئات والقنوات", VipUiHelper.BtnVariant.NEON_GREEN
        ) { 
            startActivity(Intent(this, CategoryVisibilityControlActivity::class.java))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))

        content.addView(btnRow, cardLp().apply { topMargin = dp(4) })

        // ===== Progress overlay =====
        buildProgressOverlay(root)

        // ===== Log card =====
        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(TextView(this@MasterLinkActivity).apply {
                text = "📜 سجل التعميم"
                setTextColor(Color.parseColor("#7FE6FF"))
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            txtLog = TextView(this@MasterLinkActivity).apply {
                text = "⏳ في انتظار الرابط..."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setLineSpacing(4f, 1.05f)
                setPadding(0, dp(8), 0, 0)
            }
            addView(txtLog)
        }, cardLp().apply { topMargin = dp(12) })
    }

    private fun buildProgressOverlay(parent: LinearLayout) {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC050A1A"))
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_vip_overlay)
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        inner.addView(ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(16) }
        })
        progressStatus = TextView(this).apply {
            text = "جارِ التعميم..."
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        inner.addView(progressStatus)
        overlay.addView(inner)
        parent.addView(overlay, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        progressOverlay = overlay
    }

    private fun submitMasterUrl() {
        val masterUrl = inputMasterUrl.text.toString().replace(" ", "").replace("&amp;", "&").trim()
        val linkExpiry = inputMasterExpiry.text.toString().trim()

        if (masterUrl.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ يرجى لصق الرابط الجديد أولاً")
            return
        }

        showProgress("جارِ تعميم السيرفر على كل المستخدمين...")
        appendLog("⏳ بدأ تعميم الرابط الموحد...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val encUrl = URLEncoder.encode(masterUrl, "UTF-8")
                val encLinkExpiry = URLEncoder.encode(linkExpiry, "UTF-8")
                val encSecret = URLEncoder.encode(SECRET, "UTF-8")

                val url = "$apiUrl?action=update_master_url&secret=$encSecret&master_url=$encUrl&link_expires_at=$encLinkExpiry"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 25000

                val resCode = connection.responseCode
                val res = connection.inputStream.bufferedReader().readText()

                withContext(Dispatchers.Main) {
                    hideProgress()
                    if (resCode == 200) {
                        try {
                            val json = JSONObject(res)
                            val success = json.optBoolean("success", false)
                            val msg = json.optString("message", res)
                            if (success) {
                                // 👑 إرسال أمر فرض التحديث لكي تظهر للزبائن 'تم تحديث السيرفر' وتمسح الكاش
                                try {
                                    URL("$apiUrl?action=increment_server_revision&secret=$encSecret").readText()
                                } catch (_: Exception) {}

                                appendLog("⚡✅ نجاح حقيقي: تم تعميم السيرفر على كل المستخدمين ✓${if (linkExpiry.isNotBlank()) "\n📅 نهاية الرابط: $linkExpiry" else ""}")
                                VipUiHelper.showSuccessOverlay(
                                    this@MasterLinkActivity,
                                    title = "⚡ تم تعميم السيرفر بنجاح",
                                    message = "تم تحديث playlist_url لجميع الأكواد في Google Sheet ✓\nسيتم تطبيق الرابط تلقائياً على جميع المستخدمين في تطبيق المشاهدة.\n\nالرابط المعتمد:\n$masterUrl${if (linkExpiry.isNotBlank()) "\n\n📅 تاريخ نهاية الرابط: $linkExpiry" else ""}",
                                    primaryText = "📋 نسخ الرابط",
                                    onPrimary = {
                                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("master_url", masterUrl))
                                    },
                                    secondaryText = "إغلاق",
                                    onSecondary = {}
                                )
                            } else {
                                appendLog("❌ فشل التعميم: السيرفر أرجع [$msg]")
                                VipUiHelper.showErrorOverlay(this@MasterLinkActivity, "❌ فشل التعميم:\n$msg")
                            }
                        } catch (e: Exception) {
                            appendLog("❌ فشل تحليل الرد: [$res]")
                            VipUiHelper.showErrorOverlay(this@MasterLinkActivity, "❌ الرد غير مفهوم:\n$res")
                        }
                    } else {
                        appendLog("❌ فشل الاتصال (HTTP $resCode)")
                        VipUiHelper.showErrorOverlay(this@MasterLinkActivity, "❌ فشل الاتصال (HTTP $resCode)")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    appendLog("❌ انقطع الاتصال: ${e.localizedMessage}")
                    VipUiHelper.showErrorOverlay(this@MasterLinkActivity, "❌ انقطع الاتصال:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun appendLog(line: String) {
        runOnUiThread {
            txtLog.text = "✓ $line\n--------------------------------\n${txtLog.text}"
        }
    }

    private fun showProgress(msg: String) {
        runOnUiThread {
            progressStatus.text = msg
            progressOverlay.visibility = View.VISIBLE
        }
    }

    private fun hideProgress() {
        runOnUiThread {
            progressOverlay.visibility = View.GONE
        }
    }

    private fun cardLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(12) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
