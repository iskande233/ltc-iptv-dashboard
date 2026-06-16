package com.latchi.admin

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * واجهة الغربال الذكي (Sanitizer) — صفحة مستقلة تماماً.
 *
 * الإصلاح الكارثي: كانت سابقاً تفتح نفس صفحة AdminActivity مع باقي الأدوات،
 * الآن هي شاشة مخصصة بالكامل بزر رجوع وفواصل واضحة.
 */
class SanitizerActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_API_URL = "https://script.google.com/macros/s/AKfycbycNO9V5P4jbHQFNDZeQM0FJwqhSlCJMxXV3mCzqrJXM3hYG9JCtUk0tow6bm6Ijsv8/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    private lateinit var inputRawSanitizeUrl: EditText
    private lateinit var inputMasterExpiry: EditText
    private lateinit var txtLog: TextView
    private lateinit var progressOverlay: View
    private lateinit var progressStatus: TextView

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(root)

        // ===== Top bar with back button =====
        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "🧹 الغربال الذكي",
            subtitle = "AI Channel Sanitizer",
            onBack = { finish() }
        ))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        // ===== Description card =====
        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(TextView(this@SanitizerActivity).apply {
                text = "🧹 الغربال الملكي الذكي ومصفي القنوات"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@SanitizerActivity).apply {
                text = "يراقب ويغربل أي رابط M3U، ينقي القنوات الأجنبية بالذكاء الاصطناعي، ويرتب الرياضة في الصدارة، ثم يرسله مباشرة إلى تطبيقك iptv latchi"
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            })
        }, cardLp())

        // ===== Input: Raw URL with paste =====
        content.addView(VipUiHelper.buildInputLabel(this, "📥 ألصق رابط M3U أو Xtream الخام"))

        val urlContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        inputRawSanitizeUrl = EditText(this).apply {
            hint = "https://server.com/get.php?username=xxx&password=xxx"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            minLines = 2
            setSingleLine(false)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isLongClickable = true
        }
        urlContainer.addView(inputRawSanitizeUrl, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val pasteRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        pasteRow.addView(VipUiHelper.buildMiniButton(
            this, "📋 لصق", VipUiHelper.BtnVariant.NEON_BLUE
        ) {
            VipUiHelper.pasteFromClipboard(this@SanitizerActivity) { txt ->
                inputRawSanitizeUrl.setText(txt)
                inputRawSanitizeUrl.setSelection(txt.length)
            }
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })

        pasteRow.addView(VipUiHelper.buildMiniButton(
            this, "🧹 مسح", VipUiHelper.BtnVariant.NEON_PURPLE
        ) {
            inputRawSanitizeUrl.setText("")
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })

        urlContainer.addView(pasteRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        content.addView(urlContainer, cardLp())

        // ===== Input: Link expiry =====
        content.addView(VipUiHelper.buildInputLabel(this, "⏳ تاريخ نهاية الرابط (اختياري)"))
        val expiryContainer = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        inputMasterExpiry = EditText(this).apply {
            hint = "2027-12-30"
            setHintTextColor(Color.parseColor("#7A82A8"))
            setTextColor(Color.parseColor("#F2F4FF"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            inputType = android.text.InputType.TYPE_CLASS_DATETIME
            setSingleLine(true)
        }
        expiryContainer.addView(inputMasterExpiry, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(expiryContainer, cardLp())

        // ===== Submit button (gold neon) =====
        content.addView(VipUiHelper.buildPrimaryButton(
            this, "⚡ قم بالغربلة والتعميم", VipUiHelper.BtnVariant.GOLD
        ) {
            executeSanitize()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            topMargin = dp(8)
        })

        // ===== Progress overlay (initially gone) =====
        buildProgressOverlay(root)

        // ===== Log card =====
        content.addView(VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(TextView(this@SanitizerActivity).apply {
                text = "📜 سجل العملية"
                setTextColor(Color.parseColor("#7FE6FF"))
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            txtLog = TextView(this@SanitizerActivity).apply {
                text = "⏳ في انتظار رابط M3U..."
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
            setPadding(dp(32), dp(32), dp(32), dp(32))
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
            text = "جارِ المعالجة..."
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

    private fun executeSanitize() {
        val rawUrl = inputRawSanitizeUrl.text.toString().trim()
        if (rawUrl.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ يرجى لصق رابط M3U أو Xtream أولاً")
            return
        }

        showProgress("جارِ الغربلة الذكية ورفع الرابط إلى Google Sheet...")
        appendLog("⏳ بدأت عملية الغربلة...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var realUrl = rawUrl.replace("&amp;", "&")
                if (realUrl.contains("get.php") && !realUrl.contains("type=m3u_plus")) {
                    realUrl = if (realUrl.contains("type=m3u")) {
                        realUrl.replace("type=m3u", "type=m3u_plus")
                    } else "$realUrl&type=m3u_plus"
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
                    if (!body.contains("#EXTINF", ignoreCase = true)) {
                        throw Exception("الرابط لا يحتوي على قنوات M3U صالحة")
                    }

                    val cleanM3uFile = File(filesDir, "sanitized_master_playlist.m3u")
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

                    val uploadResult = uploadToScript(cleanM3uFile, inputMasterExpiry.text.toString().trim())

                    withContext(Dispatchers.Main) {
                        hideProgress()
                        val cleanUrl = uploadResult.optString("playlist_url", uploadResult.optString("drive_url", ""))
                        if (uploadResult.optBoolean("success", false)) {
                            appendLog("✅ تمت الغربلة والرفع بنجاح\nالقنوات الخام: ${allPairs.size}\nالقنوات في الملف النظيف: ${finalPairs.size}\nالرابط النظيف: $cleanUrl")
                            VipUiHelper.showSuccessOverlay(
                                this@SanitizerActivity,
                                title = "✨ اكتملت الغربلة الذكية",
                                message = "القنوات الخام: ${allPairs.size}\nالقنوات النظيفة: ${finalPairs.size}\nتم تعميم الرابط على Google Sheet للمستخدمين ✓\n\nالرابط:\n$cleanUrl",
                                primaryText = "🚀 فتح التقرير البصري",
                                onPrimary = { startActivity(Intent(this@SanitizerActivity, ReportActivity::class.java)) },
                                secondaryText = "📤 مشاركة الملف",
                                onSecondary = { shareM3uFile(cleanM3uFile) }
                            )
                        } else {
                            appendLog("⚠️ تمت الغربلة محلياً لكن فشل الرفع: ${uploadResult.optString("message")}")
                            VipUiHelper.showWarningOverlay(
                                this@SanitizerActivity,
                                title = "⚠️ تمت الغربلة محلياً",
                                message = "لكن فشل رفع الملف إلى Google Sheet:\n${uploadResult.optString("message")}\nيمكنك مشاركة الملف يدوياً.",
                                primaryText = "📤 مشاركة الملف",
                                onPrimary = { shareM3uFile(cleanM3uFile) },
                                secondaryText = "إغلاق",
                                onSecondary = {}
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    appendLog("❌ فشل الغربلة/الرفع: ${e.localizedMessage}")
                    VipUiHelper.showErrorOverlay(this@SanitizerActivity, "❌ فشل الغربلة:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun uploadToScript(file: File, linkExpiry: String): JSONObject {
        val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
        val content = file.readText(Charsets.UTF_8)
        val form = FormBody.Builder()
            .add("action", "upload_master_m3u")
            .add("secret", SECRET)
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

    private fun shareM3uFile(file: File) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@SanitizerActivity, "com.latchi.admin.provider", file))
                type = "audio/x-mpegurl"
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة القائمة النظيفة..."))
        } catch (e: Exception) {
            Log.e("SanitizerActivity", "share error: ${e.message}")
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
