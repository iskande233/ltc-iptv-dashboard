package com.latchi.admin

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * ✦ GeminiAssistantActivity — المساعد الذكي لـ LATCHI IPTV Admin
 *
 * شاشة VIP كاملة تتيح للمدير التحدث مع الذكاء الاصطناعي بالدارجة الجزائرية 🇩🇿
 * لإدارة التطبيق بالكامل:
 * 1. أوامر صوتية ونصية.
 * 2. جلب الإحصائيات الحقيقية.
 * 3. توليد وتسجيل أكواد جديدة.
 * 4. حذف مستخدمين.
 * 5. تعميم روابط أو فرض تحديث.
 * 6. إدارة مفاتيح Gemini بأمان.
 */
class GeminiAssistantActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_API_URL = "https://script.google.com/macros/s/AKfycbwoxD7eNi6AVvhw9l_hPzaUkVt1F9U6trUXs28QYuNld_Ip15ZoefcTAdkd4B_DqoGO/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    private lateinit var etInput: EditText
    private lateinit var tvChatLog: TextView
    private lateinit var llVoiceOverlay: LinearLayout
    private lateinit var tvVoiceStatus: TextView
    private lateinit var flGlowContainer: FrameLayout
    private lateinit var scrollView: ScrollView

    private var speechRecognizer: SpeechRecognizer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)

        buildUi()

        // التحقق من وجود مفتاح Gemini
        if (!GeminiKeyManager.hasKey(this)) {
            showGeminiKeySetupDialog(cancelable = false)
        } else {
            appendLog("✦ أهلاً بك يا مدير! أنا Gemini، مساعدك الذكي.")
            appendLog("💡 تكلم معايا بالدارجة الجزائرية: 'ديرلي مستخدم جديد' / 'وريني الإحصائيات' / 'امسح الكود 123456' / 'وريني الأوامر'.")
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)

        // TopBar
        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "✦ المساعد الذكي Gemini",
            subtitle = "AI Royal Admin Assistant • 🇩🇿",
            onBack = { finish() }
        ))

        val mainFrame = FrameLayout(this)
        root.addView(mainFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        // Content
        val innerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        // أزرار سريعة علوية (مفاتيح Gemini + الأوامر)
        val headerBtns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        headerBtns.addView(VipUiHelper.buildMiniButton(this, "🔑 إعداد مفاتيح Gemini", VipUiHelper.BtnVariant.GOLD) {
            showGeminiKeySetupDialog(cancelable = true)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })

        headerBtns.addView(VipUiHelper.buildMiniButton(this, "💡 الأوامر المتاحة", VipUiHelper.BtnVariant.NEON_BLUE) {
            showHelpDialog()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })

        innerContent.addView(headerBtns)

        // سجل المحادثة (Chat Log)
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundResource(R.drawable.bg_card_admin)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        tvChatLog = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#F2F4FF"))
            textSize = 14f
            setLineSpacing(6f, 1.1f)
        }
        scrollView.addView(tvChatLog)

        innerContent.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply { bottomMargin = dp(16) })

        // أدوات الإدخال (نص + صوت + إرسال)
        val inputCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_admin)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        etInput = EditText(this).apply {
            hint = "اكتب أمرك هنا... (أو اضغط الميكروفون)"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_vip_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            textSize = 14f
            maxLines = 3
        }
        inputRow.addView(etInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

        // زر الميكروفون (دارجة)
        val btnMic = TextView(this).apply {
            text = "🎙️"
            textSize = 24f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_voice_glow)
            isClickable = true
            isFocusable = true
            setOnClickListener { startVoiceRecognition() }
        }
        inputRow.addView(btnMic, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(8) })

        // زر الإرسال
        val btnSend = TextView(this).apply {
            text = "🚀"
            textSize = 24f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_btn_gold)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val cmd = etInput.text.toString().trim()
                if (cmd.isNotBlank()) {
                    etInput.setText("")
                    executeAdminCommand(cmd)
                }
            }
        }
        inputRow.addView(btnSend, LinearLayout.LayoutParams(dp(54), dp(54)))

        inputCard.addView(inputRow)
        innerContent.addView(inputCard)

        mainFrame.addView(innerContent)

        // Overlay الصوت
        buildVoiceOverlay(mainFrame)
    }

    private fun buildVoiceOverlay(parent: FrameLayout) {
        llVoiceOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#DC000000"))
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener { stopVoiceRecognition() }
        }

        flGlowContainer = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_voice_glow)
        }
        val innerGlow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        innerGlow.addView(TextView(this).apply { text = "🎙️"; textSize = 38f; marginEnd = dp(14) })
        tvVoiceStatus = TextView(this).apply {
            text = "اسمعك... تكلم بالدارجة"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        }
        innerGlow.addView(tvVoiceStatus)
        flGlowContainer.addView(innerGlow)

        llVoiceOverlay.addView(flGlowContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })

        llVoiceOverlay.addView(TextView(this).apply {
            text = "انقر في أي مكان للإلغاء"
            setTextColor(Color.parseColor("#B8C0E0"))
            textSize = 13f
        })

        parent.addView(llVoiceOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val current = tvChatLog.text.toString()
        val newEntry = "[$time] $msg\n\n"
        tvChatLog.text = newEntry + current
    }

    // ─────────────────────────────────────────────────────────────
    // إعداد المفاتيح
    // ─────────────────────────────────────────────────────────────
    private fun showGeminiKeySetupDialog(cancelable: Boolean) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundResource(R.drawable.bg_vip_card)
        }

        container.addView(TextView(this).apply {
            text = "🔑 إعداد مفاتيح Gemini API"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })

        container.addView(TextView(this).apply {
            text = "أدخل مفتاحك الرئيسي (ومفتاح احتياطي إن أردت).\nالمفاتيح مشفرة ومحفوظة في هاتفك فقط 🔒"
            setTextColor(Color.parseColor("#B8C0E0"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        val etKey1 = EditText(this).apply {
            hint = "🔑 المفتاح الأول (إجباري)"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_vip_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = true
            setText(GeminiKeyManager.getKey1(this@GeminiAssistantActivity))
        }
        container.addView(etKey1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        val etKey2 = EditText(this).apply {
            hint = "🔑 المفتاح الثاني (احتياطي - اختياري)"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_vip_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isSingleLine = true
            setText(GeminiKeyManager.getKey2(this@GeminiAssistantActivity))
        }
        container.addView(etKey2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        var dialogRef: AlertDialog? = null

        if (cancelable) {
            btnRow.addView(VipUiHelper.buildMiniButton(this, "إلغاء", VipUiHelper.BtnVariant.NEON_PURPLE) {
                dialogRef?.dismiss()
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        }

        btnRow.addView(VipUiHelper.buildMiniButton(this, "💾 حفظ", VipUiHelper.BtnVariant.GOLD) {
            val k1 = etKey1.text.toString().trim()
            val k2 = etKey2.text.toString().trim()
            if (k1.isBlank()) {
                VipUiHelper.showErrorOverlay(this, "❌ أدخل المفتاح الأول على الأقل")
                return@buildMiniButton
            }
            GeminiKeyManager.saveKeys(this, k1, k2)
            dialogRef?.dismiss()
            VipUiHelper.showSuccessOverlay(this, "✅ تم الحفظ", "تم حفظ مفاتيح Gemini بنجاح ✓", "حسناً", {})
            appendLog("✦ تم حفظ مفاتيح Gemini بنجاح. أنا جاهز لتلقي أوامرك!")
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = if (cancelable) dp(6) else 0 })

        container.addView(btnRow)

        dialogRef = AlertDialog.Builder(this)
            .setView(container)
            .setCancelable(cancelable)
            .create()
        dialogRef.show()
    }

    private fun showHelpDialog() {
        val text = """
🧠 الأوامر المدعومة بالدارجة الجزائرية 🇩🇿:

👤 إنشاء المستخدمين:
• "ديرلي مستخدم جديد" → يولد كود عشوائي ويسجله
• "خدم لي مستخدم لمدة شهر" → صلاحية 30 يوم
• "خدم لي مستخدم لمدة عام" → صلاحية 365 يوم

📊 الإحصائيات والتقارير:
• "وريني الإحصائيات" → يجلب عدد المستخدمين والنشطين
• "شكون اللي يموتوا هذا الشهر" → الروابط التي تنتهي قريباً

🗑️ الحذف والتنظيف:
• "امسح الكود 123456" → يحذف المستخدم فورا
• "طيرلي هذا الحساب 998877"

⚡ التحديثات والتعميم:
• "تحديث السيرفر" → يفرض تحديث القنوات على كل الهواتف
• "رابط جديد" → لتعميم سيرفر موحد

🧰 التنقل السريع:
• "افتح فاحص الأكواد" → يفتح Xtream Tester
• "روح للمستخدمين" → يفتح إدارة المستخدمين
• "افتح الغربال" → يفتح M3U Sanitizer
        """.trimIndent()

        VipUiHelper.showSuccessOverlay(
            this,
            title = "💡 دليل أوامر Gemini",
            message = text,
            primaryText = "فهمت ✓",
            onPrimary = {},
            secondaryText = null,
            onSecondary = null
        )
    }

    // ─────────────────────────────────────────────────────────────
    // التعرف على الصوت
    // ─────────────────────────────────────────────────────────────
    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 702)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            VipUiHelper.showErrorOverlay(this, "❌ ميزة التعرف على الصوت غير متوفرة في هاتفك")
            return
        }

        llVoiceOverlay.visibility = View.VISIBLE
        tvVoiceStatus.text = "Gemini يسمعك... تكلم بالدارجة"
        flGlowContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.voice_pulse))

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { tvVoiceStatus.text = "قول أمرك بالدارجة..." }
                override fun onBeginningOfSpeech() { tvVoiceStatus.text = "نسمع فيك..." }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { tvVoiceStatus.text = "Gemini يحلل..." }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partial.isNullOrEmpty()) tvVoiceStatus.text = partial[0]
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "ما فهمتش، عاود قول الأمر"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ما سمعت حتى صوت"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "مشكلة اتصال"
                        SpeechRecognizer.ERROR_AUDIO -> "مشكلة في الميكروفون"
                        else -> "خطأ صوتي رقم $error"
                    }
                    stopVoiceRecognition()
                    appendLog("❌ خطأ صوتي: $msg")
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    stopVoiceRecognition()
                    if (spoken.isBlank()) {
                        appendLog("❌ لم أسمع أمراً واضحاً. حاول مرة أخرى.")
                    } else {
                        appendLog("👤 أنت (صوتي): $spoken")
                        executeAdminCommand(spoken)
                    }
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-DZ")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلم بالدارجة الجزائرية")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopVoiceRecognition() {
        flGlowContainer.clearAnimation()
        llVoiceOverlay.visibility = View.GONE
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 702 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        } else if (requestCode == 702) {
            VipUiHelper.showErrorOverlay(this, "❌ يجب منح صلاحية الميكروفون لاستخدام الأوامر الصوتية")
        }
    }

    override fun onDestroy() {
        stopVoiceRecognition()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────
    // AI Command Executor (Honest Google Script integration)
    // ─────────────────────────────────────────────────────────────
    private fun executeAdminCommand(command: String) {
        appendLog("👤 أنت: $command")
        val text = command.lowercase(Locale.getDefault())

        when {

            // ══════════════════════════════════════════════
            // 1) إنشاء مستخدم جديد
            // ══════════════════════════════════════════════
            listOf("كود", "مستخدم", "حساب", "client", "abonné", "abonnee", "user").any { text.contains(it) } &&
            listOf("دير", "ديرلي", "خدم", "اصنع", "زيد", "ضيف", "create", "new", "nouveau", "صايب", "سجل").any { text.contains(it) } &&
            !listOf("امسح", "حذف", "delete", "supprimer", "نحي").any { text.contains(it) } -> {
                val days = when {
                    listOf("عام", "سنة", "year", "an", "annee", "365").any { text.contains(it) } -> 365
                    listOf("ثلاث", "3", "trois").any { text.contains(it) } && listOf("اشهر", "شهور", "months", "mois").any { text.contains(it) } -> 90
                    listOf("شهر", "month", "mois", "30").any { text.contains(it) } -> 30
                    listOf("تجريب", "نهار", "يوم", "trial", "jour").any { text.contains(it) } -> 1
                    else -> 30
                }
                createNewUserByAi(days)
            }

            // ══════════════════════════════════════════════
            // 2) جلب الإحصائيات
            // ══════════════════════════════════════════════
            listOf("احص", "إحص", "stat", "stats", "statistique", "شحال", "عدد", "rapport", "ملخص").any { text.contains(it) } -> {
                fetchAdminStats()
            }

            // ══════════════════════════════════════════════
            // 3) حذف مستخدم (مثال: امسح الكود 123456)
            // ══════════════════════════════════════════════
            listOf("امسح", "حذف", "delete", "supprimer", "نحي", "حيد", "طير").any { text.contains(it) } -> {
                val code = Regex("\\d{4,8}").find(command)?.value
                if (code.isNullOrBlank()) {
                    appendLog("✦ Gemini: ❌ فهمت أنك تريد الحذف، لكن يجب أن تذكر رقم الكود في أمرك. مثال: 'امسح الكود 123456'.")
                } else {
                    deleteUserByAi(code)
                }
            }

            // ══════════════════════════════════════════════
            // 4) فرض تحديث السيرفر (Server Revision)
            // ══════════════════════════════════════════════
            listOf("تحديث السيرفر", "revision", "فرض التحديث", "تحديث اجباري", "إجبار", "ميزاجور", "تحديث القنوات").any { text.contains(it) } -> {
                incrementServerRevisionByAi()
            }

            // ══════════════════════════════════════════════
            // 5) الروابط التي تنتهي قريباً
            // ══════════════════════════════════════════════
            listOf("يموتوا", "ينتهوا", "expire", "تنتهي", "قريب", "ينتهي", "قريبة", "هذا الشهر", "هذا الاسبوع").any { text.contains(it) } -> {
                fetchExpiringUsersByAi()
            }

            // ══════════════════════════════════════════════
            // 6) التنقل للشاشات الإدارية
            // ══════════════════════════════════════════════
            listOf("فاحص", "اختبر", "test", "xtream", "جرب الروابط").any { text.contains(it) } -> {
                appendLog("✦ Gemini: ✅ أوامر مطاعة! فتحت لك شاشة فاحص الأكواد الذكي 🔍.")
                startActivity(Intent(this, XtreamTesterActivity::class.java))
            }
            listOf("المستخدمين", "users", "utilisateurs", "السيرفرات", "servers").any { text.contains(it) } &&
            listOf("افتح", "روح", "وريني", "show", "open").any { text.contains(it) } -> {
                appendLog("✦ Gemini: ✅ فتحت لك شاشة إدارة المستخدمين والسيرفرات 👥.")
                startActivity(Intent(this, ServerListActivity::class.java))
            }
            listOf("غربل", "نقي", "فلتر", "sanitize", "الغربال").any { text.contains(it) } -> {
                appendLog("✦ Gemini: ✅ فتحت لك شاشة واجهة الغربال 🧹.")
                startActivity(Intent(this, SanitizerActivity::class.java))
            }
            listOf("كود ماجيك", "codemagic", "بناء", "build").any { text.contains(it) } -> {
                appendLog("✦ Gemini: ✅ فتحت لك مركز Codemagic 🧙.")
                startActivity(Intent(this, CodemagicCenterActivity::class.java))
            }

            // ══════════════════════════════════════════════
            // 7) المساعدة
            // ══════════════════════════════════════════════
            listOf("ساعد", "help", "aide", "واش تقدر", "الأوامر", "اوامر").any { text.contains(it) } -> {
                showHelpDialog()
            }

            else -> {
                appendLog("✦ Gemini: 🤔 ما فهمتش الأمر مليح. جرب بالدارجة الجزائرية الواضحة:\n• 'ديرلي مستخدم جديد لمدة عام'\n• 'وريني الإحصائيات'\n• 'امسح الكود 123456'\n• 'افتح فاحص الأكواد'\n• أو اضغط زر '💡 الأوامر المتاحة' في الأعلى.")
            }
        }
    }

    // ── Helper API Executors ─────────────────────────────────────
    private fun createNewUserByAi(durationDays: Int) {
        val code = (100000..999999).random().toString()
        val name = "VIP $code"

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, durationDays)
        val expiresAt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        appendLog("✦ Gemini: ⏳ جاري توليد وتسجيل المستخدم الجديد...\nالكود: $code\nالاسم: $name\nالصلاحية: $expiresAt ($durationDays يوم) ⚡")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val encSecret = enc(SECRET)
                val encCode = enc(code)
                val encName = enc(name)
                val encPlaylist = enc("http://master_server_placeholder")
                val encExpiry = enc(expiresAt)

                val url = "$apiUrl?action=add_code&secret=$encSecret&code=$encCode&name=$encName&playlist_url=$encPlaylist&expires_at=$encExpiry&link_expires_at=$encExpiry&max_devices=1"
                val req = Request.Builder().url(url).get().build()

                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("success", false).put("message", txt) }

                    withContext(Dispatchers.Main) {
                        if (res.isSuccessful && json.optBoolean("success", false)) {
                            appendLog("✦ Gemini: ✅ تم تسجيل الحساب بنجاح في Google Sheets!\n\n👑 الكود: $code\n👤 الاسم: $name\n📅 الصلاحية: $expiresAt")
                            showUserCreatedAiDialog(code, name, expiresAt)
                        } else {
                            appendLog("✦ Gemini: ❌ فشل التسجيل: ${json.optString("message", txt)}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("✦ Gemini: ❌ خطأ في الاتصال: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun showUserCreatedAiDialog(code: String, name: String, expiry: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundResource(R.drawable.bg_success_dialog)
        }
        container.addView(TextView(this).apply {
            text = "🎉 تم إنشاء المستخدم بنجاح!"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = "\n👤 الاسم: $name\n👑 الكود: $code\n📅 الصلاحية: $expiry"
            setTextColor(Color.WHITE)
            textSize = 16f
            setLineSpacing(5f, 1.1f)
            setPadding(0, dp(8), 0, dp(16))
        })

        val btnCopy = VipUiHelper.buildPrimaryButton(this, "📋 نسخ الكود ومشاركة", VipUiHelper.BtnVariant.GOLD) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
            Toast.makeText(this, "✅ تم نسخ الكود", Toast.LENGTH_SHORT).show()
        }
        container.addView(btnCopy)

        val dialog = AlertDialog.Builder(this).setView(container).create()
        dialog.show()
    }

    private fun fetchAdminStats() {
        appendLog("✦ Gemini: ⏳ جاري جلب الإحصائيات من Google Sheets...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val encSecret = enc(SECRET)
                val url = "$apiUrl?action=get_all_users&secret=$encSecret"
                val req = Request.Builder().url(url).get().build()

                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (_: Exception) { JSONObject() }
                    val arr = json.optJSONArray("users") ?: JSONArray()

                    var active = 0
                    var expiring = 0
                    for (i in 0 until arr.length()) {
                        val u = arr.getJSONObject(i)
                        if (u.optString("status").equals("Active", true)) active++
                        val exp = u.optString("expiresAt", "")
                        if (isExpiringWithinDays(exp, 7)) expiring++
                    }

                    withContext(Dispatchers.Main) {
                        appendLog("✦ Gemini: 📊 الإحصائيات الشاملة:\n👥 إجمالي الحسابات: ${arr.length()}\n🟢 الحسابات النشطة: $active\n🔴 الحسابات المنتهية/المتوقفة: ${arr.length() - active}\n⚠️ حسابات تنتهي خلال أسبوع: $expiring")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("✦ Gemini: ❌ فشل جلب الإحصائيات: ${e.localizedMessage}") }
            }
        }
    }

    private fun deleteUserByAi(code: String) {
        appendLog("✦ Gemini: ⏳ جاري حذف الكود $code من السيرفر...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val encSecret = enc(SECRET)
                val encCode = enc(code)
                val url = "$apiUrl?action=delete_user&secret=$encSecret&code=$encCode"
                val req = Request.Builder().url(url).get().build()

                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("success", false).put("message", txt) }

                    withContext(Dispatchers.Main) {
                        if (json.optBoolean("success", false)) {
                            appendLog("✦ Gemini: ✅ تم حذف الكود $code بنجاح من قاعدة البيانات ✓")
                        } else {
                            appendLog("✦ Gemini: ❌ فشل الحذف: ${json.optString("message", txt)}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("✦ Gemini: ❌ فشل الاتصال: ${e.localizedMessage}") }
            }
        }
    }

    private fun incrementServerRevisionByAi() {
        appendLog("✦ Gemini: ⏳ جاري فرض تحديث السيرفر (Server Revision) على كل الهواتف...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val encSecret = enc(SECRET)
                val url = "$apiUrl?action=increment_server_revision&secret=$encSecret"
                val req = Request.Builder().url(url).get().build()

                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("success", false).put("message", txt) }

                    withContext(Dispatchers.Main) {
                        if (res.isSuccessful && json.optBoolean("success", false)) {
                            val newRev = json.optLong("newRevision", 2)
                            appendLog("✦ Gemini: ✅ تم تعميم تحديث السيرفر بنجاح (Revision $newRev) 🎉!\nكل تطبيقات المشاهدة ستقوم بمسح الكاش وجلب القنوات الجديدة تلقائياً ✓")
                        } else {
                            appendLog("✦ Gemini: ❌ فشل التحديث: ${json.optString("message", txt)}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("✦ Gemini: ❌ فشل الاتصال: ${e.localizedMessage}") }
            }
        }
    }

    private fun fetchExpiringUsersByAi() {
        appendLog("✦ Gemini: ⏳ جاري البحث عن الحسابات التي تنتهي قريباً...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val encSecret = enc(SECRET)
                val url = "$apiUrl?action=get_all_users&secret=$encSecret"
                val req = Request.Builder().url(url).get().build()

                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    val json = try { JSONObject(txt) } catch (_: Exception) { JSONObject() }
                    val arr = json.optJSONArray("users") ?: JSONArray()

                    val expiring = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val u = arr.getJSONObject(i)
                        val exp = u.optString("expiresAt", "")
                        if (isExpiringWithinDays(exp, 15)) {
                            expiring.add("• الكود: ${u.optString("code")} | الاسم: ${u.optString("name")} | ينتهي: $exp")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (expiring.isEmpty()) {
                            appendLog("✦ Gemini: ✅ لا توجد أي حسابات تنتهي خلال 15 يوماً القادمة.")
                        } else {
                            appendLog("✦ Gemini: ⚠️ عثرت على ${expiring.size} حسابات تنتهي قريباً:\n\n" + expiring.joinToString("\n"))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLog("✦ Gemini: ❌ خطأ: ${e.localizedMessage}") }
            }
        }
    }

    private fun isExpiringWithinDays(dateStr: String, days: Int): Boolean {
        if (dateStr.isBlank()) return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr.take(10)) ?: return false
            val diff = ((date.time - System.currentTimeMillis()) / 86_400_000L).toInt()
            diff in 0..days
        } catch (_: Exception) { false }
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
