package com.latchi.admin

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * واجهة إضافة مستخدم جديد — صفحة مستقلة تماماً.
 *
 * الإصلاح الكارثي: كانت سابقاً تنقل لنفس AdminActivity مع باقي الأدوات،
 * الآن هي شاشة مخصصة بالكامل مع أزرار مدة واضحة ومعاينة بيانات فورية.
 */
class AddUserActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_API_URL = "https://script.google.com/macros/s/AKfycbycNO9V5P4jbHQFNDZeQM0FJwqhSlCJMxXV3mCzqrJXM3hYG9JCtUk0tow6bm6Ijsv8/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    private lateinit var inputNewCode: EditText
    private lateinit var inputUserName: EditText
    private lateinit var inputUserPlaylist: EditText
    private lateinit var inputLinkExpiry: EditText
    private lateinit var txtLog: TextView
    private lateinit var progressOverlay: View
    private lateinit var progressStatus: TextView
    private lateinit var btnTrial: TextView
    private lateinit var btn1Month: TextView
    private lateinit var btn3Months: TextView
    private lateinit var btn1Year: TextView
    private lateinit var txtSelectedExpiry: TextView

    private var selectedDurationDays = 30
    private var selectedExpiryDate = "2027-12-31"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        buildUi()
        genRandomCode()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)

        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "👤 إضافة مستخدم جديد",
            subtitle = "Generate VIP Code",
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
            addView(TextView(this@AddUserActivity).apply {
                text = "🛠️ توليد كود اشتراك أوتوماتيكي"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@AddUserActivity).apply {
                text = "ولّد كود اشتراك جديد وحدد صلاحيته. سيتم تسجيل الكود مباشرة في Google Sheet في حسابك السحابي."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            })
        }, cardLp())

        // ===== Code row (random + paste/copy) =====
        content.addView(VipUiHelper.buildInputLabel(this, "🔑 كود الاشتراك (يُولّد تلقائياً)"))
        val codeContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        inputNewCode = EditText(this).apply {
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#FFE680"))
            setBackgroundColor(Color.TRANSPARENT)
            setTypeface(null, android.graphics.Typeface.BOLD)
            textSize = 18f
            setPadding(dp(4), dp(6), dp(4), dp(6))
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            gravity = Gravity.CENTER
            hint = "الكود (يُولد تلقائياً)"
        }
        codeContainer.addView(inputNewCode, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val codeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        codeRow.addView(VipUiHelper.buildMiniButton(
            this, "🎲 عشوائي", VipUiHelper.BtnVariant.GOLD
        ) { genRandomCode() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        codeRow.addView(VipUiHelper.buildMiniButton(
            this, "📋 نسخ", VipUiHelper.BtnVariant.NEON_BLUE
        ) {
            val code = inputNewCode.text.toString().trim()
            if (code.isNotBlank()) {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("code", code))
                VipUiHelper.showSuccessOverlay(this, "📋 تم نسخ الكود", code, "حسناً", {}, null, null)
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
        codeContainer.addView(codeRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(codeContainer, cardLp())

        // ===== Name input =====
        content.addView(VipUiHelper.buildInputLabel(this, "👤 اسم المشترك"))
        val nameContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        inputUserName = EditText(this).apply {
            hint = "مثال: أحمد VIP"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(true)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        nameContainer.addView(inputUserName, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(nameContainer, cardLp())

        // ===== Playlist input (with paste) =====
        content.addView(VipUiHelper.buildInputLabel(this, "🔗 رابط IPTV / Playlist (اختياري)"))
        val playlistContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        inputUserPlaylist = EditText(this).apply {
            hint = "رابط M3U شخصي (اتركه فارغاً لاستخدام السيرفر الموحد)"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            minLines = 2
            setSingleLine(false)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            isLongClickable = true
        }
        playlistContainer.addView(inputUserPlaylist, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val plRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        plRow.addView(VipUiHelper.buildMiniButton(
            this, "📋 لصق", VipUiHelper.BtnVariant.NEON_BLUE
        ) {
            VipUiHelper.pasteFromClipboard(this@AddUserActivity) { txt ->
                inputUserPlaylist.setText(txt)
                inputUserPlaylist.setSelection(txt.length)
            }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        plRow.addView(VipUiHelper.buildMiniButton(
            this, "🧹 مسح", VipUiHelper.BtnVariant.NEON_PURPLE
        ) { inputUserPlaylist.setText("") }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        playlistContainer.addView(plRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(playlistContainer, cardLp())

        // ===== Link expiry input =====
        content.addView(VipUiHelper.buildInputLabel(this, "⏳ تاريخ نهاية الرابط الحقيقي (اختياري)"))
        val expiryContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        inputLinkExpiry = EditText(this).apply {
            hint = "2027-12-30"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            inputType = InputType.TYPE_CLASS_DATETIME
            setSingleLine(true)
        }
        expiryContainer.addView(inputLinkExpiry, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(expiryContainer, cardLp())

        // ===== Duration buttons =====
        content.addView(VipUiHelper.buildInputLabel(this, "📅 المدة والصلاحية"))
        val durCard = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(12))
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(buildDurBtn("24 ساعة", 1).also { btnTrial = it }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(4) })
        row1.addView(buildDurBtn("1 شهر", 30).also { btn1Month = it }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        row1.addView(buildDurBtn("3 أشهر", 90).also { btn3Months = it }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        row1.addView(buildDurBtn("1 سنة", 365).also { btn1Year = it }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4) })
        durCard.addView(row1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        txtSelectedExpiry = TextView(this).apply {
            text = "📅 الصلاحية المختارة: —"
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
            gravity = Gravity.CENTER
        }
        durCard.addView(txtSelectedExpiry, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        content.addView(durCard, cardLp())

        // Default = 30 days
        updateDurationSelection(30)

        // ===== Submit button =====
        content.addView(VipUiHelper.buildPrimaryButton(
            this, "🚀 توليد وتسجيل الكود", VipUiHelper.BtnVariant.GOLD
        ) { submitNewCode() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            topMargin = dp(4)
        })

        // ===== Progress overlay =====
        buildProgressOverlay(root)

        // ===== Log card =====
        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(TextView(this@AddUserActivity).apply {
                text = "📜 سجل العملية"
                setTextColor(Color.parseColor("#7FE6FF"))
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            txtLog = TextView(this@AddUserActivity).apply {
                text = "⏳ في انتظار التوليد..."
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
            text = "جارِ التسجيل..."
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

    private fun buildDurBtn(text: String, days: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_vip_pill_blue)
            isClickable = true
            isFocusable = true
            setOnClickListener { updateDurationSelection(days) }
            setPadding(0, dp(10), 0, dp(10))
        }
    }

    private fun updateDurationSelection(days: Int) {
        selectedDurationDays = days
        // Reset all
        listOf(btnTrial, btn1Month, btn3Months, btn1Year).forEach { btn ->
            btn.setBackgroundResource(R.drawable.bg_vip_pill_blue)
            btn.setTextColor(Color.WHITE)
        }
        // Highlight selected
        val selectedBtn = when (days) {
            1 -> btnTrial
            30 -> btn1Month
            90 -> btn3Months
            365 -> btn1Year
            else -> btn1Month
        }
        selectedBtn.setBackgroundResource(R.drawable.bg_vip_btn_neon_blue)
        selectedBtn.setTextColor(Color.parseColor("#0A0F2C"))

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, days)
        selectedExpiryDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        txtSelectedExpiry.text = "📅 الصلاحية المختارة: $selectedExpiryDate ($days يوم)"
        appendLog("تم تحديد المدة: $days يوم → تاريخ الانتهاء: $selectedExpiryDate")
    }

    private fun genRandomCode() {
        val code = (100000..999999).random().toString()
        inputNewCode.setText(code)
        if (inputUserName.text.toString().isBlank() || inputUserName.text.toString().startsWith("User ")) {
            inputUserName.setText("User $code")
        }
    }

    private fun submitNewCode() {
        val code = inputNewCode.text.toString().trim()
        val name = inputUserName.text.toString().trim()
        val playlist = inputUserPlaylist.text.toString().trim()
        val linkExpiry = inputLinkExpiry.text.toString().trim()

        if (code.isBlank() || name.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ يرجى ملء الكود والاسم أولاً")
            return
        }

        showProgress("جارِ الاتصال بـ Google Script...")
        appendLog("⏳ بدأ تسجيل الكود: $code")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val encCode = URLEncoder.encode(code, "UTF-8")
                val encName = URLEncoder.encode(name, "UTF-8")
                val encPlaylist = URLEncoder.encode(playlist, "UTF-8")
                val encExpiry = URLEncoder.encode(selectedExpiryDate, "UTF-8")
                val encLinkExpiry = URLEncoder.encode(linkExpiry, "UTF-8")
                val encSecret = URLEncoder.encode(SECRET, "UTF-8")

                val url = "$apiUrl?action=add_code&secret=$encSecret&code=$encCode&name=$encName&playlist_url=$encPlaylist&expires_at=$encExpiry&link_expires_at=$encLinkExpiry&max_devices=1"
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
                                appendLog("✅ نجاح حقيقي (HTTP 200): تم تسجيل الكود في Google Sheet ✓")
                                showSuccessDialog(code, name, selectedExpiryDate, playlist, linkExpiry)
                                genRandomCode()
                            } else {
                                appendLog("❌ فشل التسجيل: السيرفر أرجع [$msg]")
                                VipUiHelper.showErrorOverlay(this@AddUserActivity, "❌ فشل التسجيل:\n$msg")
                            }
                        } catch (e: Exception) {
                            appendLog("❌ فشل تحليل الرد: السيرفر لم يرجع JSON صالح [$res]")
                            VipUiHelper.showErrorOverlay(this@AddUserActivity, "❌ الرد غير مفهوم:\n$res")
                        }
                    } else {
                        appendLog("❌ فشل الاتصال بالسيرفر (HTTP $resCode)")
                        VipUiHelper.showErrorOverlay(this@AddUserActivity, "❌ فشل الاتصال (HTTP $resCode)")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    appendLog("❌ انقطع الاتصال: ${e.localizedMessage}")
                    VipUiHelper.showErrorOverlay(this@AddUserActivity, "❌ انقطع الاتصال:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun showSuccessDialog(code: String, name: String, expiry: String, playlist: String, linkExpiry: String) {
        val msg = buildString {
            append("👤 الاسم: ").append(name).append("\n")
            append("🔑 الكود: ").append(code).append("\n")
            append("📅 الصلاحية: ").append(expiry).append("\n")
            if (linkExpiry.isNotBlank()) append("⏳ نهاية الرابط: ").append(linkExpiry).append("\n")
            if (playlist.isNotBlank()) append("🔗 الرابط: ").append(playlist.take(80)).append("\n")
            else append("🔗 الرابط: السيرفر الموحد العام")
        }
        VipUiHelper.showSuccessOverlay(
            this,
            title = "✨ تم إنشاء المستخدم بنجاح",
            message = msg,
            primaryText = "📋 نسخ الكود",
            onPrimary = {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("code", code))
            },
            secondaryText = "حسناً",
            onSecondary = {}
        )
    }

    private fun appendLog(line: String) {
        runOnUiThread {
            if (::txtLog.isInitialized) {
                txtLog.text = "✓ $line\n--------------------------------\n${txtLog.text}"
            }
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
