package com.latchi.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * واجهة إضافة جهاز MAC / Stalker Portal — محدثة بنمط VIP Royal Dark.
 */
class AddMacActivity : AppCompatActivity() {

    companion object {
        private const val GOOGLE_SCRIPT = "https://script.google.com/macros/s/AKfycbzuPV0N6lmytlgWd5EO21Wpxj1cqkKFMZ1n_T4ANsofXuk5BTW499hLYRWiHAazyX-E/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var portalInput: EditText
    private lateinit var macInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var statusText: TextView
    private lateinit var txtLog: TextView
    private lateinit var progressOverlay: View
    private lateinit var progressStatus: TextView

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)

        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "📟 إضافة جهاز MAC / Stalker",
            subtitle = "Portal + MAC Address",
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
            addView(TextView(this@AddMacActivity).apply {
                text = "📟 إضافة جهاز MAC / Stalker"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@AddMacActivity).apply {
                text = "أدخل معلومات جهاز MAC (Mag/Stalker) لربطه بالتطبيق.\nالمستخدم يدخل MAC يدوياً في تطبيق المشاهدة."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            })
        }, cardLp())

        // ===== Portal URL with paste =====
        content.addView(VipUiHelper.buildInputLabel(this, "🌐 رابط البوابة (Portal URL)"))
        val portalContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        portalInput = EditText(this).apply {
            hint = "http://server.com/c"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            minLines = 2
            setSingleLine(false)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isLongClickable = true
        }
        portalContainer.addView(portalInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val portalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        portalRow.addView(VipUiHelper.buildMiniButton(
            this, "📋 لصق", VipUiHelper.BtnVariant.NEON_BLUE
        ) {
            VipUiHelper.pasteFromClipboard(this@AddMacActivity) { txt ->
                portalInput.setText(txt)
                portalInput.setSelection(txt.length)
            }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        portalRow.addView(VipUiHelper.buildMiniButton(
            this, "🧹 مسح", VipUiHelper.BtnVariant.NEON_PURPLE
        ) { portalInput.setText("") }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        portalContainer.addView(portalRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(portalContainer, cardLp())

        // ===== MAC Address =====
        content.addView(VipUiHelper.buildInputLabel(this, "📟 عنوان MAC"))
        val macContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        macInput = EditText(this).apply {
            hint = "00:1A:79:XX:XX:XX"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#FFE680"))
            setBackgroundColor(Color.TRANSPARENT)
            setTypeface(null, android.graphics.Typeface.BOLD)
            textSize = 16f
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isSingleLine = true
        }
        macContainer.addView(macInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(macContainer, cardLp())

        // ===== Name =====
        content.addView(VipUiHelper.buildInputLabel(this, "👤 اسم المستخدم / اللقب"))
        val nameContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        nameInput = EditText(this).apply {
            hint = "مثال: Mag VIP"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isSingleLine = true
        }
        nameContainer.addView(nameInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(nameContainer, cardLp())

        // ===== Code (optional) =====
        content.addView(VipUiHelper.buildInputLabel(this, "🔑 كود التفعيل (اختياري - يُولّد تلقائياً)"))
        val codeContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        codeInput = EditText(this).apply {
            hint = "اتركه فارغاً ليُولّد تلقائياً"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isSingleLine = true
        }
        codeContainer.addView(codeInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(codeContainer, cardLp())

        // ===== Submit button =====
        content.addView(VipUiHelper.buildPrimaryButton(
            this, "➕ إضافة جهاز MAC", VipUiHelper.BtnVariant.GOLD
        ) {
            submitMac()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            topMargin = dp(4)
        })

        // ===== Status =====
        statusText = TextView(this).apply {
            text = "⏳ في انتظار الإدخال..."
            setTextColor(Color.parseColor("#B8C0E0"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        // ===== Help note =====
        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(TextView(this@AddMacActivity).apply {
                text = "ℹ️ ملاحظة هامة"
                setTextColor(Color.parseColor("#FFB347"))
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@AddMacActivity).apply {
                text = "في تطبيق المشاهدة، يجب أن يكون هناك خيار 'إضافة MAC' في شاشة المصادر.\nإذا غير موجود، تطبيق المشاهدة يحتاج تحديث."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            })
        }, cardLp().apply { topMargin = dp(12) })

        // ===== Progress overlay =====
        buildProgressOverlay(root)
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
            text = "جارِ الإضافة..."
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

    private fun submitMac() {
        val portal = portalInput.text.toString().trim().trimEnd('/')
        val mac = macInput.text.toString().trim().uppercase()
        val name = nameInput.text.toString().trim()
        val code = codeInput.text.toString().trim().ifBlank { (100000..999999).random().toString() }

        if (portal.isBlank() || mac.isBlank() || name.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ يرجى ملء البوابة وMAC والاسم")
            return
        }

        // ✅ الإصلاح الجذري:
        // نبني رابط mac:// ونرسله كـ playlist_url عادي
        // هذا يضمن أن Google Script يحفظه ويرجعه للتطبيق بشكل صحيح
        // وأن ActivationConfig.extractPlaylistUrl() يفككه ويبني mac:// من source_type=mac
        // الطريقتان تعملان — نرسل الاثنتين لضمان التوافق
        val macSchemeUrl = "mac://stalker?portal=${URLEncoder.encode(portal, "UTF-8")}&mac=${URLEncoder.encode(mac, "UTF-8")}"

        showProgress("جارِ إضافة MAC...")
        statusText.text = "⏳ جاري إضافة MAC: $mac..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
                    .getString("apiUrl", GOOGLE_SCRIPT) ?: GOOGLE_SCRIPT

                val ts = System.currentTimeMillis()
                val url = buildString {
                    append(apiUrl)
                    append("?action=add_code")
                    append("&secret=").append(enc(SECRET))
                    append("&code=").append(enc(code))
                    append("&name=").append(enc(name))
                    // نرسل source_type + portal + mac (لـ Script المحدّث)
                    append("&source_type=mac")
                    append("&portal_url=").append(enc(portal))
                    append("&mac_address=").append(enc(mac))
                    // + playlist_url كـ mac:// (للتوافق مع Script القديم)
                    append("&playlist_url=").append(enc(macSchemeUrl))
                    // expires_at افتراضي سنة كاملة
                    append("&expires_at=").append(enc(getOneYearFromNow()))
                    append("&max_devices=1")
                    append("&_t=$ts")
                }

                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (_: Exception) { JSONObject() }
                    withContext(Dispatchers.Main) {
                        hideProgress()
                        if (res.isSuccessful && json.optBoolean("success", false)) {
                            statusText.text = "✅ تم إضافة MAC بنجاح!\nالكود: $code | MAC: $mac"
                            VipUiHelper.showSuccessOverlay(
                                this@AddMacActivity,
                                title = "📟 تم إضافة جهاز MAC",
                                message = "الكود: $code\nMAC: $mac\nالبوابة: $portal\nالاسم: $name\nتم التسجيل في Google Sheet ✓",
                                primaryText = "📋 نسخ الكود",
                                onPrimary = {
                                    val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("code", code))
                                },
                                secondaryText = "حسناً",
                                onSecondary = {}
                            )
                        } else {
                            val errMsg = json.optString("message", "خطأ غير معروف")
                            statusText.text = "❌ فشل: $errMsg"
                            VipUiHelper.showErrorOverlay(this@AddMacActivity, "❌ فشل الإضافة:\n$errMsg")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    statusText.text = "❌ فشل الاتصال: ${e.localizedMessage}"
                    VipUiHelper.showErrorOverlay(this@AddMacActivity, "❌ فشل الاتصال:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun getOneYearFromNow(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.YEAR, 1)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    private fun cardLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
}
