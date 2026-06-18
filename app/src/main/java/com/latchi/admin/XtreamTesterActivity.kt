package com.latchi.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 🔍 XtreamTesterActivity
 *
 * شاشة اختبار أكواد Xtream/M3U بالذكاء الاصطناعي (Gemini)
 *
 * المميزات:
 * 1. اختبار كود Xtream أو رابط M3U — يعرض: ✅/❌ + عدد القنوات + تاريخ الانتهاء
 * 2. اختبار دُفعة كاملة (100 رابط مرة واحدة) — Gemini يفرز ويرتب
 * 3. تعميم بضغطة واحدة — أي كود شغال يُعمَّم فوراً لكل المستخدمين
 * 4. Gemini يساعد في الفرز والتنظيم والتحليل بأوامر طبيعية
 */
class XtreamTesterActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_API_URL =
            "https://script.google.com/macros/s/AKfycbwoxD7eNi6AVvhw9l_hPzaUkVt1F9U6trUXs28QYuNld_Ip15ZoefcTAdkd4B_DqoGO/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    // ── Data class لكل نتيجة اختبار ──────────────────────────────
    data class TestResult(
        val url: String,
        val online: Boolean,
        val channelCount: Int = 0,
        val expiry: String = "",
        val username: String = "",
        val server: String = "",
        val responseMs: Long = 0L,
        val error: String = ""
    )

    // ── State ────────────────────────────────────────────────────
    private val results = mutableListOf<TestResult>()
    private var isTestingBatch = false

    // ── UI refs ──────────────────────────────────────────────────
    private lateinit var etBatchInput: EditText
    private lateinit var btnTestAll: TextView
    private lateinit var btnGeminiAnalyze: TextView
    private lateinit var tvGeminiOutput: TextView
    private lateinit var llResultsContainer: LinearLayout
    private lateinit var tvSummary: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressStatus: TextView
    private lateinit var progressOverlay: View
    private lateinit var etGeminiCmd: EditText
    private lateinit var scrollView: ScrollView

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        buildUi()
    }

    // ─────────────────────────────────────────────────────────────
    // UI Builder
    // ─────────────────────────────────────────────────────────────
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)

        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "🔍 فاحص الأكواد الذكي",
            subtitle = "Xtream & M3U Tester + Gemini AI",
            onBack = { finish() }
        ))

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(content)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        // ── بطاقة الشرح ──────────────────────────────────────────
        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(TextView(this@XtreamTesterActivity).apply {
                text = "🔍 فاحص الأكواد الذكي"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@XtreamTesterActivity).apply {
                text = "الصق رابط واحد أو 100 رابط — سيفحص كل رابط ويعطيك:\n✅ شغال / ❌ ميت  |  عدد القنوات  |  تاريخ الانتهاء\nثم اختار أفضل رابط وعمّمه بضغطة واحدة 🚀"
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
                setLineSpacing(4f, 1.1f)
            })
        }, cardLp())

        // ── منطقة إدخال الروابط ──────────────────────────────────
        content.addView(VipUiHelper.buildInputLabel(this, "📋 الصق الروابط هنا (رابط في كل سطر)"))

        val inputCard = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        etBatchInput = EditText(this).apply {
            hint = "http://server.com:8080/get.php?username=xxx&password=xxx\nأو:\nhttp://server2.com:8080 | user | pass\nأو:\nXXXXXX (كود تفعيل مباشر)"
            setHintTextColor(Color.parseColor("#555A7A"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            minLines = 5
            maxLines = 12
            setSingleLine(false)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            textSize = 12f
        }
        inputCard.addView(etBatchInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // أزرار لصق / مسح
        val btnRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        btnRow1.addView(VipUiHelper.buildMiniButton(this, "📋 لصق", VipUiHelper.BtnVariant.NEON_BLUE) {
            VipUiHelper.pasteFromClipboard(this) { etBatchInput.setText(it) }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })
        btnRow1.addView(VipUiHelper.buildMiniButton(this, "🧹 مسح", VipUiHelper.BtnVariant.NEON_PURPLE) {
            etBatchInput.setText("")
            results.clear()
            llResultsContainer.removeAllViews()
            tvSummary.text = ""
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        inputCard.addView(btnRow1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(inputCard, cardLp())

        // ── زر اختبار الكل ──────────────────────────────────────
        btnTestAll = VipUiHelper.buildPrimaryButton(this, "⚡ اختبر كل الروابط", VipUiHelper.BtnVariant.GOLD) {
            startBatchTest()
        }
        content.addView(btnTestAll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(4) })

        // ── ملخص النتائج ────────────────────────────────────────
        tvSummary = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        content.addView(tvSummary)

        // ── نتائج الاختبار ───────────────────────────────────────
        llResultsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(llResultsContainer)

        // ── قسم Gemini ──────────────────────────────────────────
        content.addView(buildGeminiSection())

        // ── Progress overlay ─────────────────────────────────────
        buildProgressOverlay(root)
    }

    private fun buildGeminiSection(): LinearLayout {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Divider
        section.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#2A2F50"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(16); bottomMargin = dp(12) }
        })

        section.addView(TextView(this).apply {
            text = "🧠 Gemini — مساعد الفرز والتحليل"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        section.addView(TextView(this).apply {
            text = "قل لـ Gemini: \"رتب النتائج\" / \"وريني الشغالين فقط\" / \"عمّم أحسن رابط\" / \"احذف الميتين\""
            setTextColor(Color.parseColor("#8891B8"))
            textSize = 11f
            setLineSpacing(3f, 1.1f)
            setPadding(0, 0, 0, dp(10))
        })

        // خانة أمر Gemini
        val geminiInputCard = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        etGeminiCmd = EditText(this).apply {
            hint = "مثال: وريني الشغالين فقط ورتبهم حسب عدد القنوات"
            setHintTextColor(Color.parseColor("#555A7A"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            minLines = 2
            setSingleLine(false)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            textSize = 12f
        }
        geminiInputCard.addView(etGeminiCmd, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        section.addView(geminiInputCard, cardLp())

        // زر تنفيذ Gemini
        btnGeminiAnalyze = VipUiHelper.buildPrimaryButton(
            this, "🧠 نفّذ الأمر مع Gemini", VipUiHelper.BtnVariant.NEON_BLUE
        ) { runGeminiCommand() }
        section.addView(btnGeminiAnalyze, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(4) })

        // خرج Gemini
        val geminiOutputCard = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        geminiOutputCard.addView(TextView(this).apply {
            text = "🧠 رد Gemini"
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        })
        tvGeminiOutput = TextView(this).apply {
            text = "⏳ في انتظار أمرك..."
            setTextColor(Color.parseColor("#B8C0E0"))
            textSize = 12f
            setLineSpacing(5f, 1.1f)
            setPadding(0, dp(8), 0, 0)
        }
        geminiOutputCard.addView(tvGeminiOutput)
        section.addView(geminiOutputCard, cardLp().apply { topMargin = dp(8) })

        return section
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
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60)).apply { bottomMargin = dp(14) }
        }
        inner.addView(progressBar)
        progressStatus = TextView(this).apply {
            text = "جارِ الاختبار..."
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
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

    // ─────────────────────────────────────────────────────────────
    // Batch Test Logic
    // ─────────────────────────────────────────────────────────────
    private fun startBatchTest() {
        val raw = etBatchInput.text.toString().trim()
        if (raw.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ الصق رابطاً واحداً على الأقل")
            return
        }

        val urls = parseUrls(raw)
        if (urls.isEmpty()) {
            VipUiHelper.showErrorOverlay(this, "❌ لم أجد روابط صالحة\nتأكد من الصيغة")
            return
        }

        results.clear()
        llResultsContainer.removeAllViews()
        tvSummary.text = "⏳ جاري اختبار ${urls.size} رابط..."
        showProgress("0 / ${urls.size}")
        isTestingBatch = true

        CoroutineScope(Dispatchers.IO).launch {
            urls.forEachIndexed { index, url ->
                withContext(Dispatchers.Main) {
                    progressStatus.text = "اختبار ${index + 1} / ${urls.size}\n${url.take(50)}..."
                }
                val result = testUrl(url)
                results.add(result)
                withContext(Dispatchers.Main) {
                    addResultCard(result, index)
                }
            }

            withContext(Dispatchers.Main) {
                hideProgress()
                isTestingBatch = false
                val online = results.count { it.online }
                val dead = results.size - online
                tvSummary.text = "✅ شغال: $online  |  ❌ ميت: $dead  |  المجموع: ${results.size}"
                // نطلع لأعلى لنشوف الملخص
                scrollView.post { scrollView.smoothScrollTo(0, 0) }

                // Gemini يحلل تلقائياً بعد الاختبار
                if (results.isNotEmpty()) {
                    autoGeminiSummary()
                }
            }
        }
    }

    /**
     * تحليل سطر واحد وتحويله لرابط M3U قابل للاختبار
     * يقبل:
     * - http://server/get.php?username=x&password=y
     * - http://server:8080 | user | pass
     * - http://server:8080 user pass
     * - server:8080 | user | pass (بدون http)
     */
    private fun parseUrls(raw: String): List<String> {
        val urls = mutableListOf<String>()
        raw.lines().forEach { line ->
            val l = line.trim()
            if (l.isBlank() || l.startsWith("#")) return@forEach

            when {
                // رابط M3U كامل
                l.contains("get.php") && l.contains("username") -> {
                    urls.add(normalizeM3uUrl(l))
                }
                // صيغة: server | user | pass
                l.contains("|") -> {
                    val parts = l.split("|").map { it.trim() }
                    if (parts.size >= 3) {
                        val server = normalizeServer(parts[0])
                        val user = parts[1]
                        val pass = parts[2]
                        if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                            urls.add(buildM3uUrl(server, user, pass))
                        }
                    }
                }
                // صيغة: server user pass (مسافة)
                l.startsWith("http") && l.split(" ").size >= 3 -> {
                    val parts = l.split(" ").map { it.trim() }.filter { it.isNotBlank() }
                    if (parts.size >= 3) {
                        val server = normalizeServer(parts[0])
                        urls.add(buildM3uUrl(server, parts[1], parts[2]))
                    }
                }
                // رابط M3U عادي
                l.startsWith("http") -> {
                    urls.add(l.replace("&amp;", "&"))
                }
            }
        }
        return urls.distinct()
    }

    private fun normalizeServer(s: String): String {
        var server = s.trim().replace("&amp;", "&")
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            server = "http://$server"
        }
        return server.trimEnd('/')
    }

    private fun normalizeM3uUrl(url: String): String {
        var u = url.replace("&amp;", "&").trim()
        if (!u.contains("type=")) u += "&type=m3u_plus"
        if (!u.contains("output=")) u += "&output=ts"
        return u
    }

    private fun buildM3uUrl(server: String, user: String, pass: String): String =
        "$server/get.php?username=${URLEncoder.encode(user, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}&type=m3u_plus&output=ts"

    // ─────────────────────────────────────────────────────────────
    // Test Single URL
    // ─────────────────────────────────────────────────────────────
    private fun testUrl(url: String): TestResult {
        val start = System.currentTimeMillis()
        return try {
            // 1. نجرب player_api.php لجلب معلومات الحساب (Xtream)
            val xtreamInfo = tryXtreamApi(url)
            if (xtreamInfo != null) {
                return xtreamInfo.copy(responseMs = System.currentTimeMillis() - start)
            }

            // 2. نجرب M3U مباشر — نعد القنوات
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
            conn.connect()

            val code = conn.responseCode
            if (code !in 200..299) {
                return TestResult(url, false, error = "HTTP $code")
            }

            val body = conn.inputStream.bufferedReader().readText(Charsets.UTF_8)
            if (!body.contains("#EXTINF", ignoreCase = true)) {
                return TestResult(url, false, error = "ليس M3U صالح")
            }

            val count = body.lines().count {
                it.trim().startsWith("#EXTINF", ignoreCase = true)
            }

            TestResult(
                url = url,
                online = true,
                channelCount = count,
                responseMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            TestResult(url, false, error = e.message?.take(60) ?: "خطأ اتصال")
        }
    }

    private fun tryXtreamApi(m3uUrl: String): TestResult? {
        return try {
            // نستخرج server/user/pass من رابط M3U
            val uri = java.net.URI(m3uUrl)
            val params = (uri.rawQuery ?: "").split("&").mapNotNull {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else null
            }.toMap()

            val username = params["username"] ?: return null
            val password = params["password"] ?: return null
            val server = "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"

            val apiUrl = "$server/player_api.php?username=${URLEncoder.encode(username, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()

            if (conn.responseCode !in 200..299) return null

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val userInfo = json.optJSONObject("user_info") ?: return null

            val status = userInfo.optString("status", "")
            if (!status.equals("Active", ignoreCase = true) && status.isNotBlank()) {
                return TestResult(m3uUrl, false, error = "الحساب: $status", username = username, server = server)
            }

            // تاريخ الانتهاء
            val expRaw = userInfo.optString("exp_date", "")
            val expiry = if (expRaw.isNotBlank() && expRaw != "null") {
                try {
                    val ts = expRaw.toLong()
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(ts * 1000))
                } catch (_: Exception) { expRaw }
            } else "بدون انتهاء ♾️"

            // عدد القنوات — نجلب من live_streams_count إذا موجود
            val maxCon = userInfo.optString("max_connections", "")
            val activeCon = userInfo.optString("active_cons", "")

            TestResult(
                url = m3uUrl,
                online = true,
                channelCount = -1, // -1 = نجيبه لاحقاً
                expiry = expiry,
                username = username,
                server = server
            )
        } catch (_: Exception) { null }
    }

    // ─────────────────────────────────────────────────────────────
    // Result Card UI
    // ─────────────────────────────────────────────────────────────
    private fun addResultCard(result: TestResult, index: Int) {
        val card = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        // ── السطر الأول: أيقونة + رابط مختصر ──
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(this).apply {
            text = if (result.online) "✅" else "❌"
            textSize = 20f
            setPadding(0, 0, dp(10), 0)
        })
        headerRow.addView(TextView(this).apply {
            text = shortUrl(result.url)
            setTextColor(if (result.online) Color.parseColor("#7FE6FF") else Color.parseColor("#FF6B6B"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(headerRow)

        // ── التفاصيل ──
        if (result.online) {
            val details = buildString {
                if (result.username.isNotBlank()) append("👤 ${result.username}  ")
                if (result.channelCount > 0) append("📺 ${result.channelCount} قناة  ")
                if (result.expiry.isNotBlank()) append("📅 ${result.expiry}")
                if (result.responseMs > 0) append("  ⚡ ${result.responseMs}ms")
            }
            if (details.isNotBlank()) {
                card.addView(TextView(this).apply {
                    text = details
                    setTextColor(Color.parseColor("#A5B4FC"))
                    textSize = 11f
                    setPadding(0, dp(4), 0, 0)
                })
            }
        } else {
            card.addView(TextView(this).apply {
                text = "❌ ${result.error}"
                setTextColor(Color.parseColor("#FF6B6B"))
                textSize = 11f
                setPadding(0, dp(4), 0, 0)
            })
        }

        // ── أزرار الإجراء (فقط للشغالين) ──
        if (result.online) {
            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }

            // زر نسخ الرابط
            btnRow.addView(VipUiHelper.buildMiniButton(this, "📋 نسخ", VipUiHelper.BtnVariant.NEON_BLUE) {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("url", result.url))
                Toast.makeText(this, "✅ تم نسخ الرابط", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })

            // زر تعميم فوري
            btnRow.addView(VipUiHelper.buildMiniButton(this, "🚀 عمّم", VipUiHelper.BtnVariant.GOLD) {
                confirmAndBroadcast(result)
            }, LinearLayout.LayoutParams(0, dp(38), 1f))

            card.addView(btnRow)
        }

        // ── حدود ملونة حسب الحالة ──
        card.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#0F1428"))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(2), if (result.online) Color.parseColor("#1A6B3A") else Color.parseColor("#6B1A1A"))
        }

        val lp = cardLp().apply { topMargin = dp(8) }
        llResultsContainer.addView(card, lp)
    }

    private fun shortUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val params = (uri.rawQuery ?: "").split("&").mapNotNull {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else null
            }.toMap()
            val user = params["username"] ?: ""
            val host = "${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
            if (user.isNotBlank()) "$host  |  👤$user" else host
        } catch (_: Exception) {
            url.take(55)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Broadcast
    // ─────────────────────────────────────────────────────────────
    private fun confirmAndBroadcast(result: TestResult) {
        val msg = "هل تريد تعميم هذا الرابط على كل المستخدمين؟\n\n${shortUrl(result.url)}" +
                (if (result.expiry.isNotBlank()) "\n📅 الانتهاء: ${result.expiry}" else "")

        VipUiHelper.showSuccessOverlay(
            this,
            title = "🚀 تأكيد التعميم",
            message = msg,
            primaryText = "✅ عمّم الآن",
            onPrimary = { doBroadcast(result.url) },
            secondaryText = "إلغاء",
            onSecondary = {}
        )
    }

    private fun doBroadcast(url: String) {
        showProgress("جارِ التعميم على كل المستخدمين...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
                    .getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val ts = System.currentTimeMillis()
                val reqUrl = "$apiUrl?action=update_master_url&secret=${URLEncoder.encode(SECRET, "UTF-8")}&master_url=${URLEncoder.encode(url, "UTF-8")}&_t=$ts"
                val conn = URL(reqUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)

                withContext(Dispatchers.Main) {
                    hideProgress()
                    if (json.optBoolean("success", false)) {
                        val updated = json.optInt("cleared_users", json.optInt("updated_users", 0))
                        val rev = json.optInt("server_revision", 0)
                        VipUiHelper.showSuccessOverlay(
                            this@XtreamTesterActivity,
                            title = "✅ تم التعميم بنجاح!",
                            message = "تم تحديث الرابط الموحد\n👥 المستخدمون: $updated\n🔄 Revision: $rev\n\nسيتلقى كل المستخدمين الرابط الجديد فوراً",
                            primaryText = "رائع! 🎉",
                            onPrimary = {},
                            secondaryText = null,
                            onSecondary = null
                        )
                        tvGeminiOutput.text = "✅ تم تعميم الرابط بنجاح!\n👥 $updated مستخدم\n🔄 Revision: $rev"
                    } else {
                        VipUiHelper.showErrorOverlay(this@XtreamTesterActivity, "❌ فشل التعميم:\n${json.optString("message")}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    VipUiHelper.showErrorOverlay(this@XtreamTesterActivity, "❌ فشل الاتصال:\n${e.localizedMessage}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Gemini Integration
    // ─────────────────────────────────────────────────────────────
    private fun autoGeminiSummary() {
        val online = results.filter { it.online }
        val dead = results.filter { !it.online }

        val summary = buildString {
            append("📊 نتائج الاختبار التلقائي:\n\n")
            append("✅ شغال: ${online.size}\n")
            online.take(5).forEach { r ->
                append("  • ${shortUrl(r.url)}")
                if (r.expiry.isNotBlank()) append(" | 📅 ${r.expiry}")
                if (r.channelCount > 0) append(" | 📺 ${r.channelCount}")
                append("\n")
            }
            if (online.size > 5) append("  ...و ${online.size - 5} آخرين\n")
            append("\n❌ ميت: ${dead.size}\n")

            if (online.isNotEmpty()) {
                append("\n💡 توصية Gemini:\n")
                val best = online.maxByOrNull { it.channelCount }
                if (best != null) {
                    append("أفضل رابط: ${shortUrl(best.url)}")
                    if (best.expiry.isNotBlank()) append(" (ينتهي: ${best.expiry})")
                    append("\nاضغط '🚀 عمّم' بجانبه لتعميمه فوراً")
                }
            }
        }
        tvGeminiOutput.text = summary
    }

    private fun runGeminiCommand() {
        val cmd = etGeminiCmd.text.toString().trim()
        if (cmd.isBlank()) {
            tvGeminiOutput.text = "❌ اكتب أمراً أولاً"
            return
        }

        if (results.isEmpty()) {
            tvGeminiOutput.text = "⚠️ لا توجد نتائج بعد — اضغط '⚡ اختبر كل الروابط' أولاً"
            return
        }

        tvGeminiOutput.text = "🧠 Gemini يفكر..."
        val text = cmd.lowercase(java.util.Locale.getDefault())

        when {

            // ══════════════════════════════════════════════
            // 1) عرض الشغالين فقط
            // ══════════════════════════════════════════════
            listOf("شغال", "online", "يشتغل", "actif", "working", "تيسري").any { text.contains(it) } &&
            listOf("وريني", "عرض", "فقط", "show", "filter", "سير").any { text.contains(it) } -> {
                val online = results.filter { it.online }
                if (online.isEmpty()) {
                    tvGeminiOutput.text = "😔 والله ما كاين رابط شغال!"
                } else {
                    llResultsContainer.removeAllViews()
                    online.forEachIndexed { i, r -> addResultCard(r, i) }
                    tvGeminiOutput.text = "✅ عرضت ${online.size} رابط شغال فقط"
                }
            }

            // ══════════════════════════════════════════════
            // 2) "سير تيسريهم قاع" — فحص كامل للكل
            // ══════════════════════════════════════════════
            listOf("سير تيسري", "تيسريهم", "فحص الكل", "check all", "اختبر الكل").any { text.contains(it) } -> {
                tvGeminiOutput.text = "⚡ جاري فحص كل الروابط من جديد..."
                startBatchTest()
            }

            // ══════════════════════════════════════════════
            // 3) عرض الميتين / المتبلوكين
            // ══════════════════════════════════════════════
            listOf("ميت", "dead", "offline", "مات", "فاشل", "تبلوك", "blocked", "عالق").any { text.contains(it) } -> {
                val dead = results.filter { !it.online }
                if (dead.isEmpty()) {
                    tvGeminiOutput.text = "🎉 والله ما كاين رابط ميت! كلهم شغالين"
                } else {
                    llResultsContainer.removeAllViews()
                    dead.forEachIndexed { i, r -> addResultCard(r, i) }
                    tvGeminiOutput.text = "❌ عرضت ${dead.size} رابط ميت / متبلوك"
                }
            }

            // ══════════════════════════════════════════════
            // 4) "طيرلي قاع الروابط الميتة" — حذفهم
            // ══════════════════════════════════════════════
            listOf("طيرلي", "احذف", "حذف", "امسح", "نحي", "remove", "طير").any { text.contains(it) } &&
            listOf("ميت", "dead", "offline", "فاشل", "قاع").any { text.contains(it) } -> {
                val before = results.size
                results.removeAll { !it.online }
                llResultsContainer.removeAllViews()
                results.forEachIndexed { i, r -> addResultCard(r, i) }
                val removed = before - results.size
                tvGeminiOutput.text = "🗑️ طرت $removed رابط ميت\nتبقى ${results.size} رابط شغال ✅"
            }

            // ══════════════════════════════════════════════
            // 5) "افرزلي" / رتب حسب القنوات أو البورت
            // ══════════════════════════════════════════════
            listOf("افرزلي", "فرز", "رتب", "ترتيب", "sort", "trier").any { text.contains(it) } -> {
                when {
                    listOf("بورت", "port", "منفذ").any { text.contains(it) } -> {
                        // ترتيب حسب البورت
                        val sorted = results.sortedWith(
                            compareByDescending<TestResult> { it.online }
                                .thenBy { extractPort(it.url) }
                        )
                        llResultsContainer.removeAllViews()
                        sorted.forEachIndexed { i, r -> addResultCard(r, i) }
                        tvGeminiOutput.text = "✅ مفروزين حسب البورت\n:80 → :8080 → :25461..."
                    }
                    listOf("سرعة", "speed", "وقت", "أسرع").any { text.contains(it) } -> {
                        val sorted = results.sortedWith(
                            compareByDescending<TestResult> { it.online }
                                .thenBy { if (it.responseMs > 0) it.responseMs else Long.MAX_VALUE }
                        )
                        llResultsContainer.removeAllViews()
                        sorted.forEachIndexed { i, r -> addResultCard(r, i) }
                        tvGeminiOutput.text = "✅ مفروزين حسب السرعة — الأسرع أول ⚡"
                    }
                    else -> {
                        // افتراضي: حسب القنوات
                        val sorted = results.sortedWith(
                            compareByDescending<TestResult> { it.online }
                                .thenByDescending { it.channelCount }
                        )
                        llResultsContainer.removeAllViews()
                        sorted.forEachIndexed { i, r -> addResultCard(r, i) }
                        tvGeminiOutput.text = "✅ مفروزين — الشغالين أول ثم حسب عدد القنوات 📺"
                    }
                }
            }

            // ══════════════════════════════════════════════
            // 6) "امشي دير هذا هو السيرفر الرئيسي"
            //    / "عمّم أحسن رابط"
            // ══════════════════════════════════════════════
            listOf("عمم", "عمّم", "امشي دير", "السيرفر الرئيسي", "broadcast",
                   "أحسن", "الأفضل", "best", "الرئيسي").any { text.contains(it) } -> {
                val best = results.filter { it.online }
                    .maxWithOrNull(compareBy({ it.channelCount }, { -it.responseMs }))
                if (best == null) {
                    tvGeminiOutput.text = "😔 ما كاين رابط شغال للتعميم!"
                } else {
                    tvGeminiOutput.text = "🎯 أحسن رابط:\n${shortUrl(best.url)}" +
                        (if (best.expiry.isNotBlank()) "\n📅 ${best.expiry}" else "") +
                        (if (best.channelCount > 0) "\n📺 ${best.channelCount} قناة" else "") +
                        "\n\nنعمّمه الحين..."
                    confirmAndBroadcast(best)
                }
            }

            // ══════════════════════════════════════════════
            // 7) "جبدلي اليوزر والباص" — استخراج بيانات
            // ══════════════════════════════════════════════
            listOf("جبدلي", "استخرج", "اليوزر", "الباص", "username", "password",
                   "extract", "بيانات").any { text.contains(it) } -> {
                val sb = StringBuilder("📋 بيانات الروابط الشغالة:\n\n")
                results.filter { it.online }.forEach { r ->
                    try {
                        val uri = java.net.URI(r.url)
                        val params = (uri.rawQuery ?: "").split("&").mapNotNull {
                            val kv = it.split("=", limit = 2)
                            if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else null
                        }.toMap()
                        val user = params["username"] ?: "—"
                        val pass = params["password"] ?: "—"
                        val host = "${uri.host}:${if (uri.port > 0) uri.port else 80}"
                        sb.append("🌐 $host\n👤 $user\n🔐 $pass\n")
                        if (r.expiry.isNotBlank()) sb.append("📅 ${r.expiry}\n")
                        sb.append("─────────────\n")
                    } catch (_: Exception) {}
                }
                tvGeminiOutput.text = if (results.any { it.online }) sb.toString()
                                      else "😔 ما كاين رابط شغال لاستخراج البيانات"
            }

            // ══════════════════════════════════════════════
            // 8) "خرجلي برك الهوست" — Host فقط
            // ══════════════════════════════════════════════
            listOf("خرجلي", "الهوست", "host", "الدومين", "domain", "الآيبي", "ip").any { text.contains(it) } -> {
                val hosts = results.filter { it.online }.mapNotNull {
                    try {
                        val uri = java.net.URI(it.url)
                        "${uri.host}:${if (uri.port > 0) uri.port else 80}"
                    } catch (_: Exception) { null }
                }.distinct()
                if (hosts.isEmpty()) {
                    tvGeminiOutput.text = "😔 ما كاين رابط شغال"
                } else {
                    tvGeminiOutput.text = "🌐 الهوستات الشغالة:\n\n" + hosts.joinToString("\n")
                }
            }

            // ══════════════════════════════════════════════
            // 9) "شكون اللي يموتوا هذا الشهر؟"
            // ══════════════════════════════════════════════
            listOf("شكون", "يموتوا", "هذا الشهر", "ينتهوا", "expire", "تنتهي",
                   "قريب", "ينتهي", "قريبة").any { text.contains(it) } -> {
                val days = when {
                    listOf("اليوم", "today").any { text.contains(it) } -> 1
                    listOf("أسبوع", "week", "7").any { text.contains(it) } -> 7
                    listOf("شهر", "month", "30").any { text.contains(it) } -> 30
                    else -> 7
                }
                val expiring = results.filter { r ->
                    r.online && r.expiry.isNotBlank() && isExpiringInDays(r.expiry, days)
                }
                if (expiring.isEmpty()) {
                    tvGeminiOutput.text = "✅ ما كاين رابط ينتهي خلال $days يوم"
                } else {
                    llResultsContainer.removeAllViews()
                    expiring.forEachIndexed { i, r -> addResultCard(r, i) }
                    tvGeminiOutput.text = "⚠️ ${expiring.size} رابط ينتهيوا خلال $days يوم!"
                }
            }

            // ══════════════════════════════════════════════
            // 10) "كاين رابط مكرر؟" — إزالة المكررات
            // ══════════════════════════════════════════════
            listOf("مكرر", "duplicate", "كاش", "مكررات", "متشابه").any { text.contains(it) } -> {
                val before = results.size
                val seen = mutableSetOf<String>()
                val unique = results.filter { r ->
                    val key = try {
                        val uri = java.net.URI(r.url)
                        val params = (uri.rawQuery ?: "").split("&").mapNotNull {
                            val kv = it.split("=", limit = 2)
                            if (kv.size == 2) kv[0] to kv[1] else null
                        }.toMap()
                        "${uri.host}:${uri.port}|${params["username"]}"
                    } catch (_: Exception) { r.url }
                    seen.add(key)
                }
                results.clear()
                results.addAll(unique)
                llResultsContainer.removeAllViews()
                results.forEachIndexed { i, r -> addResultCard(r, i) }
                tvGeminiOutput.text = "🧹 نظفت ${before - results.size} رابط مكرر\nتبقى ${results.size} رابط فريد ✅"
            }

            // ══════════════════════════════════════════════
            // 11) "أعطيني الخلاص الكاملا" — ملخص شامل
            // ══════════════════════════════════════════════
            listOf("خلاص", "ملخص", "إحصاء", "إحصائيات", "stats",
                   "summary", "rapport", "كامل").any { text.contains(it) } -> {
                autoGeminiSummary()
            }

            // ══════════════════════════════════════════════
            // 12) "قلب هذا الرابط لـ Xtream" — تحويل
            // ══════════════════════════════════════════════
            listOf("قلب", "حول", "convert", "xtream", "لوحة التحكم").any { text.contains(it) } -> {
                val sb = StringBuilder("🔄 الروابط بصيغة Xtream:\n\n")
                results.filter { it.online }.forEach { r ->
                    try {
                        val uri = java.net.URI(r.url)
                        val params = (uri.rawQuery ?: "").split("&").mapNotNull {
                            val kv = it.split("=", limit = 2)
                            if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else null
                        }.toMap()
                        sb.append("🌐 Server: ${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else 80}\n")
                        sb.append("👤 User: ${params["username"] ?: "—"}\n")
                        sb.append("🔐 Pass: ${params["password"] ?: "—"}\n")
                        sb.append("─────────────\n")
                    } catch (_: Exception) {}
                }
                tvGeminiOutput.text = sb.toString()
            }

            // ══════════════════════════════════════════════
            // 13) وريني الكل
            // ══════════════════════════════════════════════
            listOf("الكل", "كل", "all", "tout", "قاع").any { text.contains(it) } -> {
                llResultsContainer.removeAllViews()
                results.forEachIndexed { i, r -> addResultCard(r, i) }
                tvGeminiOutput.text = "✅ عرضت كل النتائج (${results.size})"
            }

            // ══════════════════════════════════════════════
            // 14) مساعدة / الأوامر
            // ══════════════════════════════════════════════
            listOf("ساعد", "help", "aide", "أوامر", "واش", "شنو").any { text.contains(it) } -> {
                tvGeminiOutput.text = """
🧠 أوامر Gemini — بالدارجة الجزائرية:

🔍 فحص وعرض:
• "سير تيسريهم قاع" — فحص كل الروابط
• "وريني الشغالين فقط"
• "وريني الميتين / المتبلوكين"
• "وريني الكل"

🗂️ ترتيب وتصفية:
• "افرزلي على حساب القنوات"
• "افرزلي على حساب البورت"
• "افرزلي على حساب السرعة"
• "كاين رابط مكرر؟" — ينظف المكررات

📋 استخراج بيانات:
• "جبدلي اليوزر والباص"
• "خرجلي برك الهوست"
• "قلب هذا الرابط لـ Xtream"

⏳ الصلاحية:
• "شكون اللي يموتوا هذا الشهر؟"
• "شكون اللي يموتوا هذا الأسبوع؟"

🚀 إجراءات سريعة:
• "امشي دير هذا هو السيرفر الرئيسي"
• "طيرلي قاع الروابط الميتة"
• "أعطيني الخلاص الكاملا"
                """.trimIndent()
            }

            else -> {
                tvGeminiOutput.text = "🤔 ما فهمتش الأمر.\n\nقول مثلاً:\n• وريني الشغالين فقط\n• افرزلي على حساب القنوات\n• امشي دير هذا هو السيرفر الرئيسي\n• ساعدني — لعرض كل الأوامر"
            }
        }

        etGeminiCmd.setText("")
    }

    // استخراج البورت من الرابط
    private fun extractPort(url: String): Int {
        return try {
            val uri = java.net.URI(url)
            if (uri.port > 0) uri.port else 80
        } catch (_: Exception) { 80 }
    }

    // فحص الانتهاء خلال X أيام
    private fun isExpiringInDays(expiry: String, days: Int): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = sdf.parse(expiry.take(10)) ?: return false
            val diffDays = ((date.time - System.currentTimeMillis()) / 86_400_000L)
            diffDays in 0..days
        } catch (_: Exception) { false }
    }

    private fun isExpiringSoon(expiry: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val date = sdf.parse(expiry.take(10)) ?: return false
            val diffDays = ((date.time - System.currentTimeMillis()) / 86_400_000L)
            diffDays in 0..7
        } catch (_: Exception) { false }
    }

    // ─────────────────────────────────────────────────────────────
    // Progress helpers
    // ─────────────────────────────────────────────────────────────
    private fun showProgress(msg: String) {
        runOnUiThread {
            progressStatus.text = msg
            progressOverlay.visibility = View.VISIBLE
        }
    }

    private fun hideProgress() {
        runOnUiThread { progressOverlay.visibility = View.GONE }
    }

    // ─────────────────────────────────────────────────────────────
    // Layout helpers
    // ─────────────────────────────────────────────────────────────
    private fun cardLp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(10) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
