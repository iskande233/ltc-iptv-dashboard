package com.latchi.admin

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }


    companion object {
        private const val DEFAULT_API_URL = "https://script.google.com/macros/s/AKfycbwoxD7eNi6AVvhw9l_hPzaUkVt1F9U6trUXs28QYuNld_Ip15ZoefcTAdkd4B_DqoGO/exec"
        private const val CODEMAGIC_APP_ID = "6a2f44c281e1113131cea8f9"
        private const val CODEMAGIC_WORKFLOW_ID = "android-admin-dashboard"
    }

    private lateinit var screenAuth: LinearLayout
    private lateinit var screenDashboard: ScrollView
    private lateinit var inputOtp1: EditText
    private lateinit var inputOtp2: EditText
    private lateinit var inputOtp3: EditText
    private lateinit var inputOtp4: EditText
    private lateinit var inputOtp5: EditText
    private lateinit var inputOtp6: EditText
    private lateinit var btnOtpSubmit: TextView

    private lateinit var inputApiUrl: EditText
    private lateinit var inputNewCode: EditText
    private lateinit var btnGenRandom: TextView
    private lateinit var inputUserName: EditText
    private lateinit var inputUserPlaylist: EditText
    private lateinit var inputLinkExpiry: EditText
    private lateinit var btnAddCodeSubmit: TextView
    private lateinit var inputMasterUrl: EditText
    private lateinit var inputMasterExpiry: EditText
    private lateinit var btnMasterUrlSubmit: TextView
    private lateinit var inputRawSanitizeUrl: EditText
    private lateinit var btnSanitizeSubmit: TextView
    private lateinit var btnOpenServers: TextView
    private lateinit var btnIncrementServerRevision: TextView
    private lateinit var btnGeminiChat: TextView
    private lateinit var txtAdminLog: TextView
    private lateinit var adminVoiceOverlay: FrameLayout
    private lateinit var adminVoiceGlowContainer: View
    private lateinit var adminVoiceStatusText: TextView
    private lateinit var adminVoiceMicIcon: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingVoiceOutput: TextView? = null
    private var pendingVoiceDialog: AlertDialog? = null

    private lateinit var btnTrial: TextView
    private lateinit var btn1Month: TextView
    private lateinit var btn3Months: TextView
    private lateinit var btn1Year: TextView

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var selectedDurationDays = 30
    private var selectedExpiryDate = "2027-12-31"

    private var txtSmartUpdateStatus: TextView? = null
    private var btnSmartBuild: TextView? = null
    private var btnSmartCheck: TextView? = null
    private var btnSmartPublish: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        setContentView(R.layout.activity_main)

        screenAuth = findViewById(R.id.screenAuth)
        screenDashboard = findViewById(R.id.screenDashboard)

        inputOtp1 = findViewById(R.id.inputOtp1)
        inputOtp2 = findViewById(R.id.inputOtp2)
        inputOtp3 = findViewById(R.id.inputOtp3)
        inputOtp4 = findViewById(R.id.inputOtp4)
        inputOtp5 = findViewById(R.id.inputOtp5)
        inputOtp6 = findViewById(R.id.inputOtp6)
        btnOtpSubmit = findViewById(R.id.btnOtpSubmit)

        inputApiUrl = findViewById(R.id.inputApiUrl)
        inputNewCode = findViewById(R.id.inputNewCode)
        btnGenRandom = findViewById(R.id.btnGenRandom)
        inputUserName = findViewById(R.id.inputUserName)
        inputUserPlaylist = findViewById(R.id.inputUserPlaylist)
        inputLinkExpiry = findViewById(R.id.inputLinkExpiry)
        btnAddCodeSubmit = findViewById(R.id.btnAddCodeSubmit)
        inputMasterUrl = findViewById(R.id.inputMasterUrl)
        inputMasterExpiry = findViewById(R.id.inputMasterExpiry)
        btnMasterUrlSubmit = findViewById(R.id.btnMasterUrlSubmit)
        inputRawSanitizeUrl = findViewById(R.id.inputRawSanitizeUrl)
        btnSanitizeSubmit = findViewById(R.id.btnSanitizeSubmit)
        btnOpenServers = findViewById(R.id.btnOpenServers)
        btnIncrementServerRevision = findViewById(R.id.btnIncrementServerRevision)
        btnGeminiChat = findViewById(R.id.btnGeminiChat)
        txtAdminLog = findViewById(R.id.txtAdminLog)
        adminVoiceOverlay = findViewById(R.id.adminVoiceOverlay)
        adminVoiceGlowContainer = findViewById(R.id.adminVoiceGlowContainer)
        adminVoiceStatusText = findViewById(R.id.adminVoiceStatusText)
        adminVoiceMicIcon = findViewById(R.id.adminVoiceMicIcon)
        adminVoiceOverlay.setOnClickListener { stopAdminVoiceListening() }

        btnTrial = findViewById(R.id.btnTrial)
        btn1Month = findViewById(R.id.btn1Month)
        btn3Months = findViewById(R.id.btn3Months)
        btn1Year = findViewById(R.id.btn1Year)

        inputApiUrl.setText(DEFAULT_API_URL)
        getSharedPreferences("admin_prefs", MODE_PRIVATE).edit().putString("apiUrl", DEFAULT_API_URL).apply()
        inputApiUrl.visibility = android.view.View.GONE
        // Hide the API URL label if present
        try { (inputApiUrl.parent as? android.view.View)?.findViewWithTag<android.view.View>("api_label")?.visibility = android.view.View.GONE } catch (_: Exception) {}

        // Handle section navigation from Dashboard
        intent.getStringExtra("section")?.let { section ->
            screenAuth.visibility = View.GONE
            screenDashboard.visibility = View.VISIBLE
            screenDashboard.post {
                when (section) {
                    "sanitizer" -> scrollToView(inputRawSanitizeUrl)
                    "add_user" -> scrollToView(inputNewCode)
                    "master" -> scrollToView(inputMasterUrl)
                }
            }
        }

        // OTP bypass - Wizard removed per client request
        screenAuth.visibility = android.view.View.GONE
        screenDashboard.visibility = android.view.View.VISIBLE

        setupOtpInputs()

        btnOtpSubmit.setOnClickListener { verifyOtp() }
        btnGenRandom.setOnClickListener { genRandomCode() }
        setupDurationButtons()
        setupLanguageButtons()
        // addSmartUpdateCenter() removed - using CodemagicCenterActivity instead

        btnAddCodeSubmit.setOnClickListener { submitNewCode() }
        btnMasterUrlSubmit.setOnClickListener { submitMasterUrl() }
        btnSanitizeSubmit.setOnClickListener { executeTrueStandaloneSanitize() }
        btnIncrementServerRevision.setOnClickListener { incrementServerRevision() }
        btnOpenServers.setOnClickListener {
            saveApiUrl()
            startActivity(Intent(this, ServerListActivity::class.java))
        }
        btnGeminiChat.setOnClickListener {
            // إذا ما فيش مفتاح → يفتح إعداد المفتاح أولاً
            if (GeminiKeyManager.hasKey(this)) {
                showGeminiAssistantDialog()
            } else {
                showGeminiKeySetupDialog()
            }
        }

        // زر إعداد مفتاح Gemini (Long Click على زر Gemini)
        btnGeminiChat.setOnLongClickListener {
            showGeminiKeySetupDialog()
            true
        }

        // ✅ زر فاحص الأكواد الذكي
        findViewById<TextView?>(R.id.btnXtreamTester)?.setOnClickListener {
            startActivity(Intent(this, XtreamTesterActivity::class.java))
        }
    }

    private fun scrollToView(view: View) {
        val scrollBounds = android.graphics.Rect()
        screenDashboard.getDrawingRect(scrollBounds)
        if (!scrollBounds.contains(view.left, view.top)) {
            screenDashboard.smoothScrollTo(0, view.top)
        }
    }


    private fun addSmartUpdateCenter() {
        val container = screenDashboard.getChildAt(0) as? LinearLayout ?: return
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundResource(R.drawable.bg_card_admin)
        }
        container.addView(card, 2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 })

        card.addView(TextView(this).apply {
            text = "🚀 مركز التحديث الذكي للتطبيق"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "القاعدة: Build أولاً، إذا فشل لا يروح للمستخدمين. إذا نجح يخزن APK، ومن بعد تضغط نشر للمستخدمين."
            setTextColor(Color.parseColor("#B8AFC8"))
            textSize = 12f
            setPadding(0, 8, 0, 10)
        })

        txtSmartUpdateStatus = TextView(this).apply {
            text = "الحالة: جاهز. إذا أول مرة، اضغط إعداد Codemagic Token."
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.parseColor("#121228"))
        }
        card.addView(txtSmartUpdateStatus, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 0) }
        card.addView(row1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52))

        val btnToken = smartButton("🔐 إعداد التوكن") { showCodemagicTokenDialog() }
        btnSmartBuild = smartButton("🏗️ بناء نسخة") { startSmartCodemagicBuild() }
        row1.addView(btnToken, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = 6 })
        row1.addView(btnSmartBuild, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 6 })

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 0) }
        card.addView(row2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52))
        btnSmartCheck = smartButton("🔎 فحص آخر Build") { checkLastCodemagicBuild(showDialog = true) }
        btnSmartPublish = smartButton("✅ نشر للمستخدمين") { publishLastSuccessfulBuild() }
        row2.addView(btnSmartCheck, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = 6 })
        row2.addView(btnSmartPublish, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = 6 })

        val full = smartButton("🌐 فتح Codemagic كامل") { startActivity(Intent(this, CodemagicCenterActivity::class.java)) }
        card.addView(full, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 54).apply { topMargin = 12 })
    }

    private fun smartButton(textValue: String, click: () -> Unit): TextView {
        return TextView(this).apply {
            text = textValue
            setTextColor(Color.BLACK)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.bg_btn_gold)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }
    }

    private fun showCodemagicTokenDialog() {
        val prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        val input = EditText(this).apply {
            setText(prefs.getString("codemagic_token", "") ?: "")
            hint = "Codemagic API Token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(20, 10, 20, 10)
        }
        AlertDialog.Builder(this)
            .setTitle("إعداد Codemagic Token")
            .setMessage("يتحفظ محلياً في لوحة التحكم فقط. ما يروحش للمستخدمين.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.edit().putString("codemagic_token", input.text.toString().trim()).apply()
                txtSmartUpdateStatus?.text = "تم حفظ Codemagic Token ✓"
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun codemagicToken(): String = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("codemagic_token", "")?.trim().orEmpty()

    private fun startSmartCodemagicBuild() {
        val token = codemagicToken()
        if (token.isBlank()) {
            showCodemagicTokenDialog()
            return
        }
        val pd = showProgressDialog("بناء نسخة جديدة", "جاري إطلاق Codemagic Build...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject()
                    .put("appId", CODEMAGIC_APP_ID)
                    .put("workflowId", CODEMAGIC_WORKFLOW_ID)
                    .put("branch", "main")
                    .toString()
                val req = Request.Builder()
                    .url("https://api.codemagic.io/builds")
                    .header("x-auth-token", token)
                    .header("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = JSONObject(txt)
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}: $txt")
                    val buildId = json.optString("buildId")
                    if (buildId.isBlank()) throw Exception("لم يرجع buildId: $txt")
                    getSharedPreferences("admin_prefs", MODE_PRIVATE).edit()
                        .putString("last_codemagic_build_id", buildId)
                        .remove("last_success_apk_url")
                        .remove("last_success_version")
                        .remove("last_success_version_code")
                        .apply()
                    withContext(Dispatchers.Main) {
                        pd.dismiss()
                        txtSmartUpdateStatus?.text = "Build بدا ✅\nID: $buildId\nاضغط فحص آخر Build بعد دقائق."
                        logMessage("🚀 تم إطلاق Codemagic Build: $buildId")
                        Toast.makeText(this@AdminActivity, "Build بدا ✓", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    txtSmartUpdateStatus?.text = "فشل إطلاق Build: ${e.localizedMessage}"
                    logMessage("❌ فشل إطلاق Build: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun checkLastCodemagicBuild(showDialog: Boolean) {
        val token = codemagicToken()
        val buildId = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("last_codemagic_build_id", "") ?: ""
        if (token.isBlank()) { showCodemagicTokenDialog(); return }
        if (buildId.isBlank()) { Toast.makeText(this, "ما كاش Build محفوظ. اضغط بناء نسخة أولاً.", Toast.LENGTH_LONG).show(); return }
        val pd = if (showDialog) showProgressDialog("فحص Build", "جاري فحص Codemagic...\n$buildId") else null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url("https://api.codemagic.io/builds/$buildId")
                    .header("x-auth-token", token)
                    .get()
                    .build()
                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}: $txt")
                    val build = JSONObject(txt).optJSONObject("build") ?: JSONObject(txt)
                    val status = build.optString("status")
                    val version = build.optString("version")
                    val msg = build.optString("message")
                    val arts = build.optJSONArray("artefacts") ?: JSONArray()
                    var apkUrl = ""
                    var versionCode = ""
                    for (i in 0 until arts.length()) {
                        val a = arts.optJSONObject(i) ?: continue
                        if (a.optString("type") == "apk" || a.optString("name").endsWith(".apk")) {
                            apkUrl = a.optString("url")
                            versionCode = a.optString("versionCode", a.optString("version_code"))
                            break
                        }
                    }
                    if (status == "finished" && apkUrl.isNotBlank()) {
                        getSharedPreferences("admin_prefs", MODE_PRIVATE).edit()
                            .putString("last_success_apk_url", apkUrl)
                            .putString("last_success_version", version)
                            .putString("last_success_version_code", versionCode)
                            .apply()
                    }
                    withContext(Dispatchers.Main) {
                        pd?.dismiss()
                        val text = when {
                            status == "finished" && apkUrl.isNotBlank() -> "Build ناجح ✅\nVersion: $version\nAPK جاهز للنشر."
                            status == "failed" -> "Build فشل ❌\n$msg\nما راحش نبعثه للمستخدمين."
                            else -> "Build حالياً: $status\nمازال ما كملش."
                        }
                        txtSmartUpdateStatus?.text = text
                        logMessage(text)
                        if (showDialog && status == "finished" && apkUrl.isNotBlank()) {
                            AlertDialog.Builder(this@AdminActivity)
                                .setTitle("Build ناجح ✅")
                                .setMessage("APK جاهز. إذا جربتو وراك راضي، اضغط نشر للمستخدمين.")
                                .setPositiveButton("نشر الآن") { _, _ -> publishLastSuccessfulBuild() }
                                .setNegativeButton("نجربو أولاً", null)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pd?.dismiss()
                    txtSmartUpdateStatus?.text = "فشل فحص Build: ${e.localizedMessage}"
                    logMessage("❌ فشل فحص Build: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun publishLastSuccessfulBuild() {
        val prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        val apkUrl = prefs.getString("last_success_apk_url", "") ?: ""
        val version = prefs.getString("last_success_version", "") ?: ""
        val versionCode = prefs.getString("last_success_version_code", "") ?: ""
        if (apkUrl.isBlank() || versionCode.isBlank()) {
            Toast.makeText(this, "ما كاش APK ناجح محفوظ. افحص Build أولاً.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تأكيد نشر التحديث")
            .setMessage("راح يوصل للمستخدمين:\n$version\n\nهل تنشره؟")
            .setPositiveButton("نشر للمستخدمين") { _, _ -> callSetAppUpdate(versionCode, version, apkUrl) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun callSetAppUpdate(versionCode: String, version: String, apkUrl: String) {
        val apiUrl = saveApiUrl()
        val pd = showProgressDialog("نشر التحديث", "جاري إرسال رابط APK إلى Google Script...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = apiUrl + "?action=set_app_update" +
                    "&secret=" + URLEncoder.encode("LatchiAdmin2026", "UTF-8") +
                    "&version_code=" + URLEncoder.encode(versionCode, "UTF-8") +
                    "&version_name=" + URLEncoder.encode(version, "UTF-8") +
                    "&apk_url=" + URLEncoder.encode(apkUrl, "UTF-8") +
                    "&force_update=false" +
                    "&notes_ar=" + URLEncoder.encode("تحديث جديد من LATCHI IPTV عبر لوحة التحكم الذكية.", "UTF-8")
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("success", false).put("message", txt) }
                    if (!res.isSuccessful || !json.optBoolean("success", false)) throw Exception(json.optString("message", txt))
                    withContext(Dispatchers.Main) {
                        pd.dismiss()
                        txtSmartUpdateStatus?.text = "تم نشر التحديث للمستخدمين ✅\n$version"
                        logMessage("✅ تم نشر تحديث التطبيق للمستخدمين: $version\n$apkUrl")
                        Toast.makeText(this@AdminActivity, "تم نشر التحديث ✓", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    txtSmartUpdateStatus?.text = "فشل نشر التحديث: ${e.localizedMessage}"
                    logMessage("❌ فشل نشر التحديث: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun incrementServerRevision() {
        val apiUrl = saveApiUrl()
        val pd = showProgressDialog("تحديث السيرفر للمستخدمين", "جاري رفع رقم إصدار السيرفر (Server Revision) في Google Script...\nسيقوم تطبيق المشاهدة لدى كل المستخدمين بمسح الكاش وإعادة جلب القنوات تلقائياً ⚡")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")
                val url = "$apiUrl?action=increment_server_revision&secret=$encSecret"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("success", false).put("message", txt) }
                    if (!res.isSuccessful || !json.optBoolean("success", false)) throw Exception(json.optString("message", txt))
                    withContext(Dispatchers.Main) {
                        pd.dismiss()
                        val newRev = json.optLong("newRevision", json.optLong("revision", json.optLong("server_revision", 1)))
                        logMessage("✅ تم رفع إصدار السيرفر بنجاح إلى: $newRev\nسيقوم تطبيق المشاهدة بتطبيق التحديث فوراً عند المستخدمين ✓")
                        Toast.makeText(this@AdminActivity, "تم تعميم تحديث السيرفر (Revision $newRev) ✓", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    logMessage("❌ فشل تحديث إصدار السيرفر: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun saveApiUrl(): String {
        // Force hardcoded API URL - Wizard removed
        val apiUrl = DEFAULT_API_URL
        getSharedPreferences("admin_prefs", MODE_PRIVATE).edit().putString("apiUrl", apiUrl).apply()
        try { inputApiUrl.setText(apiUrl) } catch (_: Exception) {}
        return apiUrl
    }

    private fun setupOtpInputs() {
        val otps = listOf(inputOtp1, inputOtp2, inputOtp3, inputOtp4, inputOtp5, inputOtp6)
        otps.forEachIndexed { i, otp ->
            otp.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!s.isNullOrEmpty() && i < otps.size - 1) {
                        otps[i + 1].requestFocus()
                    }
                    if (!s.isNullOrEmpty() && i == otps.size - 1) {
                        verifyOtp()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    private fun verifyOtp() {
        // OTP bypassed - Wizard removed per client request
        screenAuth.visibility = android.view.View.GONE
        screenDashboard.visibility = android.view.View.VISIBLE
    }

    private fun genRandomCode() {
        val code = (100000..999999).random().toString()
        inputNewCode.setText(code)
        inputUserName.setText("User $code")
    }

    private fun setupDurationButtons() {
        val buttons = listOf(btnTrial to 1, btn1Month to 30, btn3Months to 90, btn1Year to 365)
        buttons.forEach { (btn, days) ->
            btn.setOnClickListener {
                buttons.forEach { (b, _) ->
                    b.setBackgroundColor(Color.parseColor("#3d3d5c"))
                    b.setTextColor(Color.WHITE)
                }
                btn.setBackgroundColor(Color.parseColor("#FFD700"))
                btn.setTextColor(Color.parseColor("#000000"))
                selectedDurationDays = days

                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_MONTH, days)
                selectedExpiryDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                logMessage("تم تحديد الصلاحية: $selectedExpiryDate ($days يوم)")
            }
        }
    }

    private fun setupLanguageButtons() {
        val current = LanguageManager.getLanguage(this)
        val buttons = mapOf(
            "ar" to findViewById<TextView>(R.id.btnLangAr),
            "en" to findViewById<TextView>(R.id.btnLangEn),
            "fr" to findViewById<TextView>(R.id.btnLangFr)
        )
        fun refresh() {
            buttons.forEach { (code, btn) ->
                if (code == LanguageManager.getLanguage(this)) {
                    btn.setBackgroundResource(R.drawable.bg_btn_gold)
                    btn.setTextColor(Color.BLACK)
                } else {
                    btn.setBackgroundResource(R.drawable.bg_purple_panel)
                    btn.setTextColor(Color.WHITE)
                }
            }
        }
        buttons.forEach { (code, btn) ->
            btn.setOnClickListener {
                if (current != code) {
                    LanguageManager.setLanguage(this, code)
                    Toast.makeText(this, LanguageManager.languageLabel(code), Toast.LENGTH_SHORT).show()
                    LanguageManager.restart(this)
                }
            }
        }
        refresh()
    }

    private fun logMessage(msg: String) {
        val current = txtAdminLog.text.toString()
        txtAdminLog.text = "✓ $msg\n--------------------------------\n$current"
    }

    private fun showProgressDialog(title: String, message: String): AlertDialog {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(40, 50, 40, 50)
            setBackgroundColor(Color.parseColor("#1E1E38"))

            addView(ProgressBar(this@AdminActivity).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(100, 100).apply {
                    bottomMargin = 30
                }
            })
            addView(TextView(this@AdminActivity).apply {
                text = message
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            })
        }

        return AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setCancelable(false)
            .show()
    }

    // ⚡ Honest Network Code Verification (Zero fake success)
    private fun submitNewCode() {
        val code = inputNewCode.text.toString().trim()
        val name = inputUserName.text.toString().trim()
        val playlist = inputUserPlaylist.text.toString().replace(" ", "").replace("&amp;", "&").trim()
        val linkExpiry = inputLinkExpiry.text.toString().trim()
        val apiUrl = saveApiUrl()

        if (code.isBlank() || name.isBlank()) {
            Toast.makeText(this, "يرجى ملء الكود والاسم", Toast.LENGTH_SHORT).show()
            return
        }

        val pd = showProgressDialog("توليد وتسجيل كود جديد", "جارِ الاتصال المباشر بـ Google Script...\nيرجى الانتظار ⚡")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encCode = URLEncoder.encode(code, "UTF-8")
                val encName = URLEncoder.encode(name, "UTF-8")
                val encPlaylist = URLEncoder.encode(playlist, "UTF-8")
                val encExpiry = URLEncoder.encode(selectedExpiryDate, "UTF-8")
                val encLinkExpiry = URLEncoder.encode(linkExpiry, "UTF-8")
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")

                val postUrl = "$apiUrl?action=add_code&secret=$encSecret&code=$encCode&name=$encName&playlist_url=$encPlaylist&expires_at=$encExpiry&link_expires_at=$encLinkExpiry&max_devices=1"

                val connection = URL(postUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 25000

                val resCode = connection.responseCode
                val res = connection.inputStream.bufferedReader().readText()

                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (resCode == 200) {
                        try {
                            val json = JSONObject(res)
                            val success = json.optBoolean("success", false)
                            val msg = json.optString("message", res)
                            if (success) {
                                logMessage("✅ نجاح حقيقي (HTTP 200): $msg\nتم تسجيل البيانات في Google Sheet فعلياً ✓")
                                Toast.makeText(this@AdminActivity, "تم تسجيل الحساب في Google Sheets ✓", Toast.LENGTH_LONG).show()
                                showUserCreatedDialog(code, name, selectedExpiryDate, playlist, linkExpiry)
                                genRandomCode()
                            } else {
                                logMessage("❌ فشل التوليد (HTTP 200): السيرفر أرجع [$msg]. السكربت يرفض الإضافة. يرجى مراجعة Google Drive.")
                            }
                        } catch (e: Exception) {
                            logMessage("❌ فشل تحليل الرد (HTTP 200): السيرفر أرجع نصاً غير صريح [$res]. السكربت لم يكتب في Google Sheet.")
                        }
                    } else {
                        logMessage("❌ فشل الاتصال بالسيرفر (HTTP $resCode): رابط Google Script معطوب أو غير منشور كـ Web App.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    logMessage("❌ انقطع الاتصال بالسيرفر الخارجي: ${e.localizedMessage}\nالرابط الخارجي يرفض الطلب. لم يتم التسجيل في Google Sheets.")
                }
            }
        }
    }

    private fun submitMasterUrl() {
        val masterUrl = inputMasterUrl.text.toString().trim()
        val linkExpiry = inputMasterExpiry.text.toString().trim()
        val apiUrl = saveApiUrl()

        if (masterUrl.isBlank()) {
            Toast.makeText(this, "يرجى لصق الرابط الجديد", Toast.LENGTH_SHORT).show()
            return
        }

        val pd = showProgressDialog("تعميم الرابط الموحد", "جارِ الاتصال بـ Google Script...\nيغير الروابط في هواتف كل المستخدمين بصمت ⚡")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encUrl = URLEncoder.encode(masterUrl, "UTF-8")
                val encLinkExpiry = URLEncoder.encode(linkExpiry, "UTF-8")
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")

                val postUrl = "$apiUrl?action=update_master_url&secret=$encSecret&master_url=$encUrl&link_expires_at=$encLinkExpiry"

                val connection = URL(postUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 25000

                val resCode = connection.responseCode
                val res = connection.inputStream.bufferedReader().readText()

                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (resCode == 200) {
                        try {
                            val json = JSONObject(res)
                            val success = json.optBoolean("success", false)
                            val msg = json.optString("message", res)
                            if (success) {
                                logMessage("⚡✅ نجاح حقيقي (HTTP 200): تم تعميم الرابط الموحد على كل المستخدمين بنجاح ✓${if (linkExpiry.isNotBlank()) "\nتاريخ نهاية الرابط: $linkExpiry" else ""}")
                                Toast.makeText(this@AdminActivity, "تم تعميم الرابط ✓", Toast.LENGTH_LONG).show()
                            } else {
                                logMessage("❌ فشل التعميم (HTTP 200): السيرفر أرجع [$msg].")
                            }
                        } catch (e: Exception) {
                            logMessage("❌ فشل تحليل الرد (HTTP 200): [$res].")
                        }
                    } else {
                        logMessage("❌ فشل الاتصال (HTTP $resCode): رابط Google Script معطوب.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    logMessage("❌ انقطع الاتصال بالسيرفر الخارجي: ${e.localizedMessage}")
                }
            }
        }
    }


    private fun showUserCreatedDialog(code: String, name: String, expiry: String, playlist: String, linkExpiry: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 30, 34, 26)
            setBackgroundResource(R.drawable.bg_success_dialog)
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.success_user_created)
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = "\n${getString(R.string.name_label)} $name\n${getString(R.string.code_label)} $code\n${getString(R.string.expiry_label)} $expiry" +
                    (if (linkExpiry.isNotBlank()) "\n${getString(R.string.link_expiry_label)} $linkExpiry" else "") +
                    (if (playlist.isNotBlank()) "\n${getString(R.string.playlist_label)} ${playlist.take(90)}" else "\n${getString(R.string.playlist_label)} ${getString(R.string.master_server)}")
            setTextColor(Color.WHITE)
            textSize = 15f
            setLineSpacing(5f, 1.05f)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER; setPadding(0, 20, 0, 0) }
        val copyButton = TextView(this).apply {
            text = getString(R.string.copy_code)
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_btn_gold)
            gravity = android.view.Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(24, 0, 24, 0)
        }
        val okButton = TextView(this).apply {
            text = getString(R.string.ok_done)
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_purple_panel)
            gravity = android.view.Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(24, 0, 24, 0)
        }
        buttons.addView(copyButton, LinearLayout.LayoutParams(0, 48, 1f).apply { marginEnd = 10 })
        buttons.addView(okButton, LinearLayout.LayoutParams(0, 48, 1f))
        container.addView(buttons)
        val dialog = AlertDialog.Builder(this).setView(container).create()
        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("activation_code", code))
            copyButton.text = getString(R.string.copied)
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
        okButton.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener { container.alpha = 0f; container.animate().alpha(1f).setDuration(220).start() }
        dialog.show()
    }

    // ─── شاشة إعداد مفتاح Gemini ───────────────────────────────────
    private fun showGeminiKeySetupDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundResource(R.drawable.bg_success_dialog)
        }

        container.addView(TextView(this).apply {
            text = "🔑 إعداد مفتاح Gemini API"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })

        container.addView(TextView(this).apply {
            text = "المفتاح يُحفظ على جهازك فقط\nما يظهر في الكود أبداً ✅"
            setTextColor(Color.parseColor("#B8C0E0"))
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        // المفتاح الأول
        val etKey1 = EditText(this).apply {
            hint = "🔑 المفتاح الأول (الرئيسي)"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121228"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isSingleLine = true
            // إظهار المفتاح الحالي إذا موجود
            setText(GeminiKeyManager.getKey1(this@AdminActivity))
        }
        container.addView(etKey1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { bottomMargin = dp(10) })

        // المفتاح الثاني
        val etKey2 = EditText(this).apply {
            hint = "🔑 المفتاح الثاني (احتياطي - اختياري)"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#121228"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isSingleLine = true
            setText(GeminiKeyManager.getKey2(this@AdminActivity))
        }
        container.addView(etKey2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        ).apply { bottomMargin = dp(16) })

        // زر حفظ
        val btnSave = TextView(this).apply {
            text = "💾 حفظ المفاتيح"
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_btn_gold)
            gravity = android.view.Gravity.CENTER
            textSize = 15f
        }
        container.addView(btnSave, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ))

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setNegativeButton("إغلاق", null)
            .create()

        btnSave.setOnClickListener {
            val k1 = etKey1.text.toString().trim()
            val k2 = etKey2.text.toString().trim()
            if (k1.isBlank()) {
                Toast.makeText(this, "❌ أدخل المفتاح الأول على الأقل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            GeminiKeyManager.saveKeys(this, k1, k2)
            dialog.dismiss()
            Toast.makeText(this, "✅ تم حفظ مفاتيح Gemini بأمان", Toast.LENGTH_SHORT).show()
            // افتح Gemini مباشرة بعد الحفظ
            showGeminiAssistantDialog()
        }

        dialog.show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showGeminiAssistantDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 22)
            setBackgroundResource(R.drawable.bg_success_dialog)
        }
        val output = TextView(this).apply {
            text = getString(R.string.gemini_ready)
            setTextColor(Color.WHITE)
            textSize = 14f
            setLineSpacing(4f, 1.05f)
        }
        val input = EditText(this).apply {
            hint = getString(R.string.command_hint)
            setHintTextColor(Color.parseColor("#B8AFC8"))
            setTextColor(Color.WHITE)
            setSingleLine(false)
            minLines = 2
            setBackgroundColor(Color.parseColor("#121228"))
            setPadding(16, 10, 16, 10)
        }
        val voiceButton = TextView(this).apply {
            text = getString(R.string.voice_button_darja)
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_voice_glow)
            gravity = android.view.Gravity.CENTER
            isClickable = true
            isFocusable = true
        }
        val execute = TextView(this).apply {
            text = getString(R.string.execute_command)
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_btn_gold)
            gravity = android.view.Gravity.CENTER
            isClickable = true
            isFocusable = true
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.talk_to_gemini)
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        })
        container.addView(output, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 })
        container.addView(voiceButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 58).apply { topMargin = 16 })
        container.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 })
        container.addView(execute, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52).apply { topMargin = 16 })
        val dialog = AlertDialog.Builder(this).setView(container).setNegativeButton(getString(R.string.close), null).create()
        voiceButton.setOnClickListener {
            output.text = "🎙️ اسمعك... تكلم بالدارجة الجزائرية"
            startAdminVoiceListening(output, dialog)
        }
        execute.setOnClickListener {
            val cmd = input.text.toString().trim()
            if (cmd.isBlank()) return@setOnClickListener
            output.text = getString(R.string.understanding)
            handleAdminAiCommand(cmd, output, dialog)
        }
        dialog.show()
    }

    private fun startAdminVoiceListening(output: TextView, dialog: AlertDialog) {
        pendingVoiceOutput = output
        pendingVoiceDialog = dialog
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 701)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "التعرف على الصوت غير متوفر في هذا الجهاز", Toast.LENGTH_LONG).show()
            return
        }

        adminVoiceOverlay.visibility = View.VISIBLE
        adminVoiceMicIcon.text = "🎙️"
        adminVoiceStatusText.text = "Gemini يسمعك..."
        adminVoiceGlowContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.voice_pulse))

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { adminVoiceStatusText.text = "قول أمرك بالدارجة..." }
                override fun onBeginningOfSpeech() { adminVoiceStatusText.text = "نسمع فيك..." }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { adminVoiceStatusText.text = "Gemini يفهم..." }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partial.isNullOrEmpty()) adminVoiceStatusText.text = partial[0]
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "ما فهمتش، عاود قول الأمر"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ما سمعت حتى صوت"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "مشكلة اتصال في التعرف الصوتي"
                        SpeechRecognizer.ERROR_AUDIO -> "مشكلة في الميكروفون"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية الميكروفون غير ممنوحة"
                        else -> "خطأ صوتي رقم $error"
                    }
                    stopAdminVoiceListening()
                    output.text = "❌ $msg"
                    Toast.makeText(this@AdminActivity, msg, Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    stopAdminVoiceListening()
                    if (spoken.isBlank()) {
                        output.text = "❌ ما سمعتش الأمر، عاود حاول"
                    } else {
                        output.text = "🎙️ سمعت: $spoken\n🧠 Gemini يفهم وينفذ..."
                        handleAdminAiCommand(spoken, output, dialog)
                    }
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-DZ")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلم بالدارجة الجزائرية")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopAdminVoiceListening() {
        adminVoiceGlowContainer.clearAnimation()
        adminVoiceOverlay.visibility = View.GONE
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            val output = pendingVoiceOutput
            val dialog = pendingVoiceDialog
            if (output != null && dialog != null) startAdminVoiceListening(output, dialog)
        } else if (requestCode == 701) {
            Toast.makeText(this, "لازم تسمح بالميكروفون باش تهدر مع Gemini", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        stopAdminVoiceListening()
        super.onDestroy()
    }

    private fun handleAdminAiCommand(command: String, output: TextView, dialog: AlertDialog) {
        val text = command.lowercase(Locale.getDefault())
        when {
            listOf("كود", "code", "مستخدم", "user", "حساب", "مشترك", "abonné", "abonnee", "client").any { text.contains(it) } &&
                    listOf("دير", "ديرلي", "اعمل", "اصنع", "صايب", "خدم", "زيد", "ضيف", "create", "new", "nouveau").any { text.contains(it) } &&
                    !listOf("امسح", "حذف", "delete", "supprimer").any { text.contains(it) } -> {
                genRandomCode()
                if (inputUserName.text.isBlank()) inputUserName.setText("User ${inputNewCode.text}")
                output.text = "✅ فهمتك: نوجد مستخدم/كود جديد\n🔑 الكود: ${inputNewCode.text}\nإذا حبيت نسجلو مباشرة قل: خدم لي مستخدم"
                if (listOf("خدم", "سجل", "دير", "create", "ajoute", "ضيف").any { text.contains(it) }) {
                    dialog.dismiss()
                    submitNewCode()
                }
            }
            listOf("عشوائي", "بدل الكود", "كود جديد", "random", "بدللي").any { text.contains(it) } -> {
                genRandomCode()
                output.text = "✅ بدلتلك الكود: ${inputNewCode.text}"
            }
            listOf("احص", "إحص", "stat", "stats", "statistique", "شحال", "عدد", "rapport").any { text.contains(it) } -> fetchAdminStats(output)
            listOf("امسح", "حذف", "delete", "supprimer", "نحي", "حيد").any { text.contains(it) } -> {
                val code = Regex("\\d{4,8}").find(command)?.value
                if (code.isNullOrBlank()) output.text = "❌ فهمت الحذف، لكن لازم تقول رقم الكود. مثال: امسح الكود 123456"
                else deleteCodeByAi(code, output)
            }
            listOf("المستخدمين", "المستخدمون", "users", "utilisateurs", "السيرفرات", "servers", "serveurs").any { text.contains(it) } &&
                    listOf("افتح", "روح", "وريني", "show", "open", "ouvre").any { text.contains(it) } -> {
                dialog.dismiss()
                saveApiUrl()
                startActivity(Intent(this, ServerListActivity::class.java))
                output.text = "✅ فتحتلك إدارة المستخدمين والسيرفرات"
            }
            listOf("تاريخ", "نهاية", "صلاحية الرابط", "link expiry", "expiration", "expire").any { text.contains(it) } -> {
                inputLinkExpiry.requestFocus()
                inputMasterExpiry.requestFocus()
                output.text = "✅ فهمت: تاريخ نهاية الرابط الحقيقي\nاكتب التاريخ بصيغة 2026-12-30 في خانة نهاية الرابط، وسيتم حفظه وعرضه في التفاصيل والتنبيه قبل 7 أيام."
            }
            listOf("رابط", "link", "server", "serveur", "سيرفر", "m3u", "xtream").any { text.contains(it) } -> {
                inputMasterUrl.requestFocus()
                output.text = "ℹ️ فهمت أمر الرابط/السيرفر. ألصق الرابط في خانة السيرفر الموحد، واكتب تاريخ النهاية إذا موجود، ثم اضغط تعميم."
            }
            listOf("تحديث السيرفر", "revision", "فرض التحديث", "تحديث اجباري", "إجبار", "ميزاجور").any { text.contains(it) } -> {
                dialog.dismiss()
                incrementServerRevision()
                output.text = "✅ جاري فرض تحديث السيرفر على جميع المستخدمين..."
            }
            listOf("غربل", "نقي", "فلتر", "sanitize", "filter", "صفي").any { text.contains(it) } -> {
                inputRawSanitizeUrl.requestFocus()
                output.text = "✅ فهمت: الغربلة الذكية. ألصق رابط M3U/Xtream الخام في خانة الغربال ثم اضغط الغربلة والتعميم."
            }
            listOf("فاحص", "اختبر", "test", "xtream", "جرب الروابط").any { text.contains(it) } -> {
                dialog.dismiss()
                startActivity(Intent(this, XtreamTesterActivity::class.java))
                output.text = "✅ فتحتلك فاحص الأكواد الذكي"
            }
            listOf("ساعدني", "help", "aide", "واش تقدر", "الأوامر", "اوامر").any { text.contains(it) } -> {
                output.text = "🎙️ نقدر نفهم الدارجة الجزائرية في أوامر الإدارة:\n• ديرلي مستخدم جديد\n• كود جديد\n• وريني الإحصائيات\n• امسح الكود 123456\n• افتح المستخدمين\n• نحي/حذف كود\n• رابط جديد / تاريخ نهاية الرابط\n• غربل رابط M3U"
            }
            else -> output.text = "🤔 ما فهمتش الأمر مليح. جرّب بالدارجة: ديرلي مستخدم جديد / وريني الإحصائيات / امسح الكود 123456 / افتح المستخدمين"
        }
    }

    private fun fetchAdminStats(output: TextView) {
        val apiUrl = saveApiUrl()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")
                val res = URL("$apiUrl?action=get_all_users&secret=$encSecret").readText()
                val json = JSONObject(res)
                val arr = json.optJSONArray("users") ?: JSONArray()
                var active = 0
                var expiringLinks = 0
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    if (u.optString("status").equals("Active", true)) active++
                    val linkExpiry = u.optString("linkExpiresAt", "")
                    if (isExpiringSoon(linkExpiry)) expiringLinks++
                }
                withContext(Dispatchers.Main) {
                    output.text = "📊 الإحصائيات\n👥 المستخدمون: ${arr.length()}\n✅ النشطون: $active\n⚠️ روابط تقترب من النهاية: $expiringLinks"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { output.text = "❌ فشل جلب الإحصائيات: ${e.localizedMessage}" }
            }
        }
    }

    private fun deleteCodeByAi(code: String, output: TextView) {
        val apiUrl = saveApiUrl()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")
                val encCode = URLEncoder.encode(code, "UTF-8")
                val res = URL("$apiUrl?action=delete_user&secret=$encSecret&code=$encCode").readText()
                val json = JSONObject(res)
                withContext(Dispatchers.Main) {
                    output.text = if (json.optBoolean("success", false)) "✅ تم حذف الكود $code" else "❌ ${json.optString("message")}"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { output.text = "❌ فشل الحذف: ${e.localizedMessage}" }
            }
        }
    }

    private fun isExpiringSoon(raw: String): Boolean {
        if (raw.isBlank()) return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = sdf.parse(raw.take(10)) ?: return false
            val diffDays = ((date.time - System.currentTimeMillis()) / 86_400_000L).toInt()
            diffDays in 0..7
        } catch (_: Exception) { false }
    }

    // ⚡ Standalone True Local Filter Engine + Direct Google Sheet/Drive Upload
    private fun executeTrueStandaloneSanitize() {
        val rawUrl = inputRawSanitizeUrl.text.toString().trim()
        if (rawUrl.isBlank()) {
            Toast.makeText(this, "يرجى لصق رابط M3U أو Xtream", Toast.LENGTH_SHORT).show()
            return
        }

        val pd = showProgressDialog("الغربال الذكي AI (مستقل 100%)", "جارِ الغربلة ثم رفع الرابط النظيف مباشرة إلى Google Sheet...\nيرجى الانتظار ⚡")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var realUrl = rawUrl.replace("&amp;", "&")
                if (realUrl.contains("get.php") && !realUrl.contains("type=m3u_plus")) {
                    realUrl = if (realUrl.contains("type=m3u")) realUrl.replace("type=m3u", "type=m3u_plus") else "$realUrl&type=m3u_plus"
                    if (!realUrl.contains("output=")) realUrl += "&output=ts"
                }

                val request = Request.Builder()
                    .url(realUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android)")
                    .header("Accept", "*/*")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    val body = response.body?.string()?.replace("\uFEFF", "") ?: throw Exception("Empty stream")
                    if (!body.contains("#EXTINF", ignoreCase = true)) throw Exception("الرابط لا يحتوي على قنوات M3U صالحة")

                    val cleanM3uFile = File(filesDir, "sanitized_master_playlist.m3u")
                    val reportHtmlFile = File(filesDir, "latchi_sanitized_report.html")
                    val jsonSummaryArr = JSONArray()
                    val cleanPairs = mutableListOf<Pair<String, String>>()
                    val allPairs = mutableListOf<Pair<String, String>>()

                    var currentExtinf = ""
                    body.lineSequence().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                            currentExtinf = trimmed
                        } else if (trimmed.isNotBlank() && !trimmed.startsWith("#") && currentExtinf.isNotBlank()) {
                            allPairs.add(currentExtinf to trimmed)
                            val lower = (currentExtinf + " " + trimmed).lowercase()
                            val isWanted = listOf(
                                "ar", "arab", "عرب", "bein", "be in", "ssc", "alkass", "ad sport", "رياض", "sport",
                                "rotana", "mbc", "osn", "art", "nile", "film", "movie", "cinema", "series", "مسلسل", "أفلام", "افلام",
                                "kids", "cartoon", "news", "أخبار", "اخبار", "quran", "islam"
                            ).any { lower.contains(it.lowercase()) }
                            if (isWanted) cleanPairs.add(currentExtinf to trimmed)
                            currentExtinf = ""
                        }
                    }

                    val finalPairs = if (cleanPairs.size >= 10) cleanPairs else allPairs
                    if (finalPairs.isEmpty()) throw Exception("لم يتم استخراج أي قناة من الرابط")

                    cleanM3uFile.bufferedWriter(Charsets.UTF_8).use { w ->
                        w.write("#EXTM3U\n")
                        finalPairs.forEach { (ext, url) ->
                            w.write(ext); w.write("\n"); w.write(url); w.write("\n")
                            if (jsonSummaryArr.length() < 2500) {
                                val name = ext.substringAfterLast(",", "Channel").replace("[", "").replace("]", "").trim()
                                val logo = Regex("tvg-logo=\"([^\"]*)\"").find(ext)?.groupValues?.getOrNull(1) ?: ""
                                val group = Regex("group-title=\"([^\"]*)\"").find(ext)?.groupValues?.getOrNull(1) ?: "Other"
                                jsonSummaryArr.put(JSONObject().put("name", name).put("logo", logo).put("group", group))
                            }
                        }
                    }

                    generateHtmlAuditReport(reportHtmlFile, allPairs.size, finalPairs.size, jsonSummaryArr)
                    val uploadResult = uploadSanitizedM3uToGoogleScript(cleanM3uFile, inputMasterExpiry.text.toString().trim())

                    withContext(Dispatchers.Main) {
                        pd.dismiss()
                        val cleanUrl = uploadResult.optString("playlist_url", uploadResult.optString("drive_url", ""))
                        if (uploadResult.optBoolean("success", false)) {
                            inputMasterUrl.setText(cleanUrl)
                            logMessage("✅ تمت الغربلة والرفع مباشرة بنجاح!\nالقنوات الخام: ${allPairs.size}\nالقنوات في الملف النظيف: ${finalPairs.size}\nالرابط النظيف: $cleanUrl\nتم تحديث Google Sheet لكل المستخدمين ✓")
                            Toast.makeText(this@AdminActivity, "تم رفع الرابط النظيف وتعميمه ✓", Toast.LENGTH_LONG).show()
                            showManagementOptionsDialog(cleanM3uFile, cleanUrl)
                        } else {
                            logMessage("⚠️ تمت الغربلة محلياً لكن فشل الرفع: ${uploadResult.optString("message")}\nيمكنك مشاركة الملف أو مراجعة Google Script.")
                            showManagementOptionsDialog(cleanM3uFile, "")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    logMessage("❌ فشل الغربلة/الرفع: ${e.localizedMessage}")
                    Toast.makeText(this@AdminActivity, "فشل الغربلة: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun uploadSanitizedM3uToGoogleScript(file: File, linkExpiry: String): JSONObject {
        val apiUrl = saveApiUrl()
        val content = file.readText(Charsets.UTF_8)
        val form = FormBody.Builder()
            .add("action", "upload_master_m3u")
            .add("secret", "LatchiAdmin2026")
            .add("filename", "latchi_sanitized_master_playlist.m3u")
            .add("m3u_content", content)
            .add("link_expires_at", linkExpiry)
            .build()
        val req = Request.Builder().url(apiUrl).post(form).build()
        client.newCall(req).execute().use { res ->
            val txt = res.body?.string().orEmpty()
            if (!res.isSuccessful) return JSONObject().put("success", false).put("message", "HTTP ${res.code}: $txt")
            return try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("success", false).put("message", txt) }
        }
    }

    private fun generateHtmlAuditReport(reportFile: File, rawCount: Int, cleanCount: Int, channelsArr: JSONArray) {
        try {
            val html = StringBuilder().apply {
                append("""
                    <!DOCTYPE html>
                    <html dir='rtl' lang='ar'>
                    <head>
                        <meta charset='UTF-8'>
                        <meta name='viewport' content='width=device_width, initial-scale=1.0'>
                        <title>تقرير المراجعة اليدوية للقنوات المصفاة</title>
                        <style>
                            body { font_family: 'Segoe UI', Tahoma, Geneva, Verdana, sans_serif; background_color: #121228; color: white; padding: 20px; margin: 0; }
                            h1 { color: #FFD700; text_align: center; margin_bottom: 5px; }
                            .summary_card { background_color: #1E1E38; border_radius: 12px; padding: 15px; margin_bottom: 20px; text_align: center; border: 1px solid #3d3d5c; }
                            .search_box { width: 100%; padding: 12px; border_radius: 8px; border: 1px solid #00E5FF; background_color: #121228; color: white; margin_bottom: 20px; outline: none; }
                            table { width: 100%; border_collapse: collapse; background_color: #1E1E38; border_radius: 10px; overflow: hidden; }
                            th, td { padding: 12px; text_align: start; border_bottom: 1px solid #2d2d44; }
                            th { background_color: #FFD700; color: black; font_weight: bold; }
                            tr:hover { background_color: #2a2a4a; }
                            .logo_img { width: 40px; height: 40px; object_fit: contain; background_color: black; border_radius: 6px; padding: 2px; }
                            .badge { background_color: #00E5FF; color: black; padding: 4px 8px; border_radius: 12px; font_size: 12px; font_weight: bold; }
                        </style>
                        <script>
                            function filterTable() {
                                val input = document.getElementById('searchInput').value.toLowerCase();
                                val rows = document.querySelectorAll('#channelsTable tbody tr');
                                for (val r of rows) {
                                    val text = r.textContent.toLowerCase();
                                    r.style.display = text.includes(input) ? '' : 'none';
                                }
                            }
                        </script>
                    </head>
                    <body>
                        <h1>المراجعة اليدوية للقنوات المصفاة 👁</h1>
                        <div class='summary_card'>
                            <h3>إحصائيات الغربلة الذكية</h3>
                            <p>القنوات الخام: <b>$rawCount</b> | القنوات العربية والرياضية المصفاة: <b style='color:#4ADE80;'>$cleanCount</b></p>
                        </div>
                        <input id='searchInput' class='search_box' oninput='filterTable()' placeholder='بحث في اسم القناة أو الفئة...'>
                        <table id='channelsTable'>
                            <thead>
                                <tr>
                                    <th>الشعار</th>
                                    <th>اسم القناة</th>
                                    <th>الفئة (Group)</th>
                                </tr\>
                            </thead>
                            <tbody>
                """.trimIndent())

                for (i in 0 until (channelsArr.length()).coerceAtMost(2500)) {
                    val c = channelsArr.getJSONObject(i)
                    append("""
                        <tr>
                            <td><img class='logo_img' src='${c.optString("logo")}' onerror="this.src='https://latchi.dz/logo.png'"></td>
                            <td style='font_weight:bold;'>${c.optString("name")}</td>
                            <td><span class='badge'>${c.optString("group")}</span></td>
                        </tr>
                    """.trimIndent())
                }

                append("""
                            </tbody>
                        </table>
                    </body>
                    </html>
                """.trimIndent())
            }
            reportFile.writeText(html.toString(), Charsets.UTF_8)
        } catch (e: Exception) { Log.e("AdminReport", "Report Error: ${e.message}") }
    }

    private fun showManagementOptionsDialog(m3uFile: File, masterUrl: String) {
        val options = arrayOf(
            "📤 مشاركة / إرسال القائمة الـ M3U النظيفة",
            "🌐 عرض تقرير الـ HTML البصري للمراجعة اليدوية 👁",
            "🚀 إرسال الكود / الرابط النظيف مباشرة لتطبيق المشاهدة"
        )

        AlertDialog.Builder(this)
            .setTitle("خيارات الإدارة والتعميم ✓")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareM3uFile(m3uFile)
                    1 -> startActivity(Intent(this, ReportActivity::class.java))
                    2 -> {
                        val realUrl = masterUrl.ifBlank { "https://latchi_sanitized_master.m3u" }
                        inputMasterUrl.setText(realUrl)
                        submitMasterUrl()

                        val playAppIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("latchiiptv://master?url=" + URLEncoder.encode(realUrl, "UTF-8"))).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        try { startActivity(playAppIntent) } catch (_: Exception) { Toast.makeText(this@AdminActivity, "تم تعميم الرابط بنجاح ✓", Toast.LENGTH_LONG).show() }
                    }
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun shareM3uFile(file: File) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@AdminActivity, "com.latchi.admin.provider", file))
                type = "audio/x-mpegurl"
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة القائمة الـ M3U النظيفة..."))
        } catch (e: Exception) { Toast.makeText(this, "خطأ في المشاركة", Toast.LENGTH_SHORT).show() }
    }
}
