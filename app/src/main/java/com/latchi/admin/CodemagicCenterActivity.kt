package com.latchi.admin

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Codemagic Smart Dashboard — محدثة بنمط VIP Royal Dark.
 *
 * المنطق الكامل محفوظ، مع تحديث:
 * 1. خلفية تدرج Royal Blue
 * 2. TopBar موحّد بزر رجوع
 * 3. أزرار Neon (Gold/Blue/Green/Purple)
 * 4. بطاقات VIP مع حدود زرقاء نيون
 * 5. Overlay dialogs بدل Toast
 */
class CodemagicCenterActivity : AppCompatActivity() {

    companion object {
        private const val API_BASE = "https://api.codemagic.io"
        private const val GOOGLE_SCRIPT = "https://script.google.com/macros/s/AKfycbycNO9V5P4jbHQFNDZeQM0FJwqhSlCJMxXV3mCzqrJXM3hYG9JCtUk0tow6bm6Ijsv8/exec"
        private const val SECRET = "LatchiAdmin2026"
        private const val PREFS_KEY_CM_TOKEN = "codemagic_token"
    }

    private val prefs by lazy { getSharedPreferences("admin_prefs", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var progressOverlay: View
    private lateinit var progressStatus: TextView

    private var appsJson = JSONArray()
    private var buildsJson = JSONArray()
    private var selectedAppId: String = ""
    private var selectedAppName: String = ""

    private fun cmToken(): String = prefs.getString(PREFS_KEY_CM_TOKEN, "9OVMA35F09K3nv1djPFqSnQIQCKkq_b4_twyExdllp4")?.trim().orEmpty()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)

        if (cmToken().isBlank()) {
            prefs.edit().putString(PREFS_KEY_CM_TOKEN, "9OVMA35F09K3nv1djPFqSnQIQCKkq_b4_twyExdllp4").apply()
        }
        buildUi()
        buildProgressOverlay()
        if (cmToken().isBlank()) {
            showTokenDialog()
        } else {
            loadDashboard()
        }
    }

    // ==================== UI BUILDERS ====================

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)

        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "🧙 Codemagic الذكي",
            subtitle = "Build Center • VIP",
            onBack = { finish() }
        ))

        // Tools row
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(tools, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        tools.addView(VipUiHelper.buildMiniButton(this, "🔐 CM Token", VipUiHelper.BtnVariant.NEON_PURPLE) {
            showTokenDialog()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        tools.addView(VipUiHelper.buildMiniButton(this, "🔄 تحديث", VipUiHelper.BtnVariant.NEON_BLUE) {
            loadDashboard()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        tools.addView(VipUiHelper.buildMiniButton(this, "🏗️ Build", VipUiHelper.BtnVariant.GOLD) {
            startBuildForSelected()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))

        // Status text
        statusText = TextView(this).apply {
            text = "⏳ جاهز"
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 12f
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundResource(R.drawable.bg_vip_pill_blue)
            gravity = Gravity.CENTER
        }
        root.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Scroll content
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun buildProgressOverlay() {
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
            text = "جارِ المعالجة..."
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        inner.addView(progressStatus)
        overlay.addView(inner)
        (findViewById<View>(android.R.id.content) as android.view.ViewGroup).addView(overlay, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        progressOverlay = overlay
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

    private fun showTokenDialog() {
        val input = EditText(this).apply {
            setText(cmToken())
            hint = "Codemagic API Token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(Color.parseColor("#F2F4FF"))
            setHintTextColor(Color.parseColor("#7A82A8"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(22))
            setBackgroundResource(R.drawable.bg_vip_overlay)
        }
        container.addView(TextView(this).apply {
            text = "🔐 Codemagic Token"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = "حط التوكن مرة وحدة. يتحفظ غير داخل الجهاز."
            setTextColor(Color.parseColor("#B8C0E0"))
            textSize = 12f
            setPadding(0, dp(8), 0, dp(12))
            gravity = Gravity.CENTER
        })
        val inputCard = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        container.addView(inputCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        var dialogRef: AlertDialog? = null
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        row.addView(VipUiHelper.buildMiniButton(this, "إلغاء", VipUiHelper.BtnVariant.NEON_PURPLE) {
            dialogRef?.dismiss()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        row.addView(VipUiHelper.buildMiniButton(this, "حفظ", VipUiHelper.BtnVariant.GOLD) {
            prefs.edit().putString(PREFS_KEY_CM_TOKEN, input.text.toString().trim()).apply()
            status("✅ تم حفظ التوكن")
            loadDashboard()
            dialogRef?.dismiss()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        container.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        dialogRef = AlertDialog.Builder(this).setView(container).create()
        dialogRef!!.setOnShowListener {
            container.alpha = 0f
            container.scaleX = 0.94f; container.scaleY = 0.94f
            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start()
        }
        dialogRef!!.show()
    }

    // ==================== API: LOAD DASHBOARD ====================

    private fun loadDashboard() {
        if (cmToken().isBlank()) {
            content.removeAllViews()
            addInfoCard("🔐 لازم التوكن", "اضغط 🔐 CM Token، دخل Codemagic API Token، ومن بعد التطبيقات والبيلدات يخرجو هنا.")
            showTokenDialog()
            return
        }

        showProgress("⏳ جاري تحميل التطبيقات...")
        status("جاري تحميل التطبيقات...")
        content.removeAllViews()
        addInfoCard("⏳ جاري التحميل", "نتصل بـ Codemagic API...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appsTxt = cmGet("$API_BASE/apps")
                val appsObj = JSONObject(appsTxt)
                appsJson = appsObj.optJSONArray("applications") ?: JSONArray()

                val buildsTxt = cmGet("$API_BASE/builds?limit=80")
                val buildsObj = JSONObject(buildsTxt)
                buildsJson = buildsObj.optJSONArray("builds") ?: JSONArray()

                withContext(Dispatchers.Main) {
                    hideProgress()
                    status("✅ تم تحميل ${appsJson.length()} تطبيق و ${buildsJson.length()} Build")
                    renderApps()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    status("❌ فشل التحميل: ${e.localizedMessage}")
                    content.removeAllViews()
                    addInfoCard("❌ خطأ في الاتصال", "${e.localizedMessage ?: "Unknown"}\n\nتأكد من:\n1. التوكن صحيح\n2. الإنترنت شغال\n3. API Base: $API_BASE")
                }
            }
        }
    }

    // ==================== RENDER APPS ====================

    private fun renderApps() {
        content.removeAllViews()
        addInfoCard("📱 التطبيقات", "اختار تطبيق باش تشوف صفحة Builds تاعو.\nToken: ${cmToken().take(8)}...")
        if (appsJson.length() == 0) {
            addInfoCard("لا توجد تطبيقات", "لا يوجد تطبيقات مرتبطة بهذا الحساب. تأكد من التوكن.")
            return
        }
        for (i in 0 until appsJson.length()) {
            val app = appsJson.optJSONObject(i) ?: continue
            val id = app.optString("_id")
            val name = app.optString("appName", "App")
            val repo = app.optJSONObject("repository")?.optString("htmlUrl") ?: ""
            val last = app.optString("lastBuildId")
            addAppCard(name, id, repo, last)
        }
    }

    private fun renderBuilds(appId: String, appName: String) {
        selectedAppId = appId
        selectedAppName = appName
        content.removeAllViews()
        addInfoCard("🏗️ Builds: $appName", "اختر نسخة. إذا فيها APK تقدر تحملها، وبعد التحميل يظهر نشر للمستخدمين.")

        val list = mutableListOf<JSONObject>()
        for (i in 0 until buildsJson.length()) {
            val b = buildsJson.optJSONObject(i) ?: continue
            if (b.optString("appId") == appId) list.add(b)
        }
        if (list.isEmpty()) {
            addInfoCard("ما كاش Builds", "اضغط زر Build من الأعلى لإنشاء نسخة جديدة لهذا التطبيق.")
            return
        }
        list.forEach { addBuildCard(it, appName) }
    }

    // ==================== CARDS ====================

    private fun addAppCard(name: String, id: String, repo: String, last: String) {
        val card = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(TextView(this@CodemagicCenterActivity).apply {
                text = "📦 $name"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@CodemagicCenterActivity).apply {
                text = "ID: ${id.take(16)}...\nRepo: ${repo.take(60)}\nLast Build: ${last.ifBlank { "--" }}"
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
                setLineSpacing(4f, 1.05f)
            })
            val row = LinearLayout(this@CodemagicCenterActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            row.addView(VipUiHelper.buildMiniButton(this@CodemagicCenterActivity, "فتح Builds", VipUiHelper.BtnVariant.NEON_BLUE) {
                renderBuilds(id, name)
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
            row.addView(VipUiHelper.buildMiniButton(this@CodemagicCenterActivity, "🏗️ Build", VipUiHelper.BtnVariant.GOLD) {
                selectedAppId = id; selectedAppName = name; startBuildForSelected()
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
            addView(row)
        }
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
    }

    private fun addBuildCard(build: JSONObject, appName: String) {
        val id = build.optString("_id")
        val status = build.optString("status")
        val version = build.optString("version")
        val branch = build.optString("branch")
        val finished = build.optString("finishedAt")
        val commit = build.optJSONObject("commit")?.optString("commitMessage") ?: ""

        val artefacts = build.optJSONArray("artefacts") ?: JSONArray()
        val apk = firstApk(artefacts)
        val apkUrl = apk.optString("url")
        val versionCode = apk.optString("versionCode", apk.optString("version_code", ""))

        val isPublished = prefs.getString("published_build_id", "") == id
        val isDownloaded = prefs.getString("downloaded_build_id", "") == id

        val card = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val icon = when (status) {
                "finished" -> "✅"
                "failed" -> "❌"
                "building", "in-progress", "processing" -> "⏳"
                else -> "ℹ️"
            }
            addView(TextView(this@CodemagicCenterActivity).apply {
                text = "$icon ${version.ifBlank { id.takeLast(8) }}"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@CodemagicCenterActivity).apply {
                text = "Status: $status\nBranch: $branch\nFinished: ${finished.take(16)}\nCommit: ${commit.take(40)}"
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
                setLineSpacing(4f, 1.05f)
            })

            val row1 = LinearLayout(this@CodemagicCenterActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            row1.addView(VipUiHelper.buildMiniButton(this@CodemagicCenterActivity, "🔎 فحص", VipUiHelper.BtnVariant.NEON_BLUE) {
                checkBuild(id)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
            addView(row1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            if (apkUrl.isNotBlank()) {
                val row2 = LinearLayout(this@CodemagicCenterActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(8), 0, 0)
                }
                row2.addView(
                    VipUiHelper.buildMiniButton(
                        this@CodemagicCenterActivity,
                        if (isDownloaded) "✅ محملة" else "⬇️ تحميل",
                        if (isDownloaded) VipUiHelper.BtnVariant.NEON_GREEN else VipUiHelper.BtnVariant.NEON_GREEN
                    ) {
                        downloadApk(id, appName, version, versionCode, apkUrl)
                    },
                    LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) }
                )
                row2.addView(
                    VipUiHelper.buildMiniButton(
                        this@CodemagicCenterActivity,
                        "📋 نسخ",
                        VipUiHelper.BtnVariant.NEON_PURPLE
                    ) {
                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("codemagic_apk_url", apkUrl))
                        Toast.makeText(this@CodemagicCenterActivity, "تم نسخ رابط الـ APK المباشر إلى الحافظة ✓", Toast.LENGTH_SHORT).show()
                    },
                    LinearLayout.LayoutParams(0, dp(48), 1.1f).apply { marginStart = dp(4); marginEnd = dp(4) }
                )
                row2.addView(
                    VipUiHelper.buildMiniButton(
                        this@CodemagicCenterActivity,
                        if (isPublished) "✅ منشورة" else "🚀 نشر",
                        if (isPublished) VipUiHelper.BtnVariant.NEON_BLUE else VipUiHelper.BtnVariant.GOLD
                    ) {
                        publishBuild(id, version, versionCode, apkUrl)
                    },
                    LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(4) }
                )
                addView(row2)
                addView(TextView(this@CodemagicCenterActivity).apply {
                    text = "⬇️ تحميل = APK للآدمين للتجربة\n📋 نسخ = نسخ الرابط الخارجي المباشر للحافظة\n🚀 نشر = يرسل الرابط للمستخدمين"
                    setTextColor(Color.parseColor("#7A82A8"))
                    textSize = 11f
                    setPadding(0, dp(8), 0, 0)
                })
            } else {
                val emptyRow = LinearLayout(this@CodemagicCenterActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(8), 0, 0)
                }
                emptyRow.addView(VipUiHelper.buildMiniButton(this@CodemagicCenterActivity, "⚠️ لا يوجد APK", VipUiHelper.BtnVariant.NEON_PURPLE) {
                    VipUiHelper.showWarningOverlay(this@CodemagicCenterActivity, "⚠️ لا APK", "هذه النسخة لا تحتوي على ملف APK.", "حسناً", {}, null, null)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
                addView(emptyRow)
            }
        }
        content.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
    }

    private fun firstApk(arr: JSONArray): JSONObject {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("type") == "apk" || o.optString("name", "").endsWith(".apk", true)) return o
        }
        return JSONObject()
    }

    // ==================== ACTIONS ====================

    private fun startBuildForSelected() {
        if (selectedAppId.isBlank()) {
            VipUiHelper.showWarningOverlay(this, "⚠️ اختر تطبيقاً أولاً", "اضغط 'فتح Builds' على أحد التطبيقات أولاً.", "حسناً", {}, null, null)
            return
        }
        val wf = if (selectedAppName.contains("dashboard", true)) "android-admin-dashboard" else "android-debug-build-from-zip"
        showProgress("⏳ جاري إطلاق Build لـ $selectedAppName...")
        status("جاري إطلاق Build لـ $selectedAppName...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject().apply {
                    put("appId", selectedAppId)
                    put("workflowId", wf)
                    put("branch", "main")
                }.toString()
                val txt = cmPostJson("$API_BASE/builds", body)
                val buildId = JSONObject(txt).optString("buildId")
                if (buildId.isBlank()) throw Exception("No buildId in response: $txt")
                prefs.edit().putString("last_codemagic_build_id", buildId).apply()
                withContext(Dispatchers.Main) {
                    hideProgress()
                    status("✅ Build بدا: ID $buildId")
                    VipUiHelper.showSuccessOverlay(
                        this@CodemagicCenterActivity,
                        title = "🏗️ Build بدا ✅",
                        message = "استنى شوية ومن بعد اضغط تحديث أو فحص.\n\nBuild ID:\n$buildId",
                        primaryText = "OK",
                        onPrimary = {}
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    status("❌ فشل إطلاق Build: ${e.localizedMessage}")
                    VipUiHelper.showErrorOverlay(this@CodemagicCenterActivity, "❌ فشل إطلاق Build:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun checkBuild(buildId: String) {
        showProgress("⏳ جاري فحص Build...")
        status("جاري فحص Build $buildId...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val txt = cmGet("$API_BASE/builds/$buildId")
                val b = JSONObject(txt).optJSONObject("build") ?: JSONObject(txt)
                val st = b.optString("status")
                val artefacts = b.optJSONArray("artefacts") ?: JSONArray()
                val apk = firstApk(artefacts)
                withContext(Dispatchers.Main) {
                    hideProgress()
                    status("Build: $st / APK: ${if (apk.optString("url").isNotBlank()) "موجود" else "غير موجود"}")
                    VipUiHelper.showSuccessOverlay(
                        this@CodemagicCenterActivity,
                        title = "🔎 فحص Build",
                        message = "Status: $st\nVersion: ${b.optString("version")}\nBranch: ${b.optString("branch")}\nAPK: ${apk.optString("url").ifBlank { "--" }}",
                        primaryText = "OK",
                        onPrimary = {}
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    status("❌ فشل الفحص: ${e.localizedMessage}")
                    VipUiHelper.showErrorOverlay(this@CodemagicCenterActivity, "❌ فشل الفحص:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun downloadApk(buildId: String, appName: String, version: String, versionCode: String, apkUrl: String) {
        val directUrl = apkUrl.trim()
        val safeVersion = version.ifBlank { buildId.takeLast(6) }.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val fileName = "${appName.ifBlank { "LATCHI" }}-$safeVersion.apk".replace("/", "-").replace(" ", "_")
        
        showProgress("جاري تحميل الـ APK من Codemagic... 0%")
        status("⬇️ بدأ تحميل الـ APK... $fileName")

        Thread {
            try {
                val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val file = File(dir, fileName).apply { parentFile?.mkdirs(); if (exists()) delete() }
                
                val req = Request.Builder()
                    .url(directUrl)
                    .header("x-auth-token", cmToken())
                    .header("Accept", "application/octet-stream")
                    .get()
                    .build()

                val okClient = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                okClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    val body = response.body ?: throw Exception("Empty body")
                    val total = body.contentLength()
                    val input = body.byteStream()
                    val output = file.outputStream()

                    var copied = 0L
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    var lastUpdate = System.currentTimeMillis()

                    input.use { inStream ->
                        output.use { outStream ->
                            while (inStream.read(buffer).also { read = it } >= 0) {
                                outStream.write(buffer, 0, read)
                                copied += read
                                
                                val now = System.currentTimeMillis()
                                if (total > 0 && now - lastUpdate > 300) {
                                    lastUpdate = now
                                    val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                                    runOnUiThread {
                                        progressStatus.text = "جاري تحميل الـ APK... $percent%"
                                    }
                                }
                            }
                        }
                    }
                }

                prefs.edit()
                    .putString("downloaded_build_id", buildId)
                    .putString("downloaded_app_name", appName)
                    .putString("downloaded_version", version)
                    .putString("downloaded_version_code", versionCode)
                    .putString("downloaded_apk_url", directUrl)
                    .apply()

                runOnUiThread {
                    hideProgress()
                    status("✅ تم تحميل الـ APK بنجاح")
                    
                    val uri = FileProvider.getUriForFile(this@CodemagicCenterActivity, "com.latchi.admin.provider", file)
                    VipUiHelper.showSuccessOverlay(
                        this@CodemagicCenterActivity,
                        title = "✅ اكتمل التحميل 100%",
                        message = "تم تخزين التطبيق مباشرة عندك:\n$fileName\n\nيمكنك تثبيته مباشرة للتجربة أو نشره للمستخدمين.",
                        primaryText = "📲 تثبيت الآن",
                        onPrimary = {
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(installIntent)
                        },
                        secondaryText = "🚀 نشر للمستخدمين",
                        onSecondary = {
                            publishBuild(buildId, version, versionCode, directUrl)
                        }
                    )
                    if (selectedAppId.isNotBlank()) renderBuilds(selectedAppId, selectedAppName)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideProgress()
                    status("❌ فشل التحميل: ${e.localizedMessage}")
                    VipUiHelper.showErrorOverlay(this@CodemagicCenterActivity, "❌ فشل تحميل الـ APK:\n${e.localizedMessage}")
                }
            }
        }.start()
    }

    private fun publishBuild(buildId: String, version: String, versionCode: String, apkUrl: String) {
        if (apkUrl.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ لا يمكن النشر: رابط APK ناقص")
            return
        }
        val finalVersionCode = versionCode.ifBlank { (System.currentTimeMillis() / 1000L).toString() }
        val finalVersion = version.ifBlank { "LATCHI IPTV b${finalVersionCode.takeLast(4)}" }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(22))
            setBackgroundResource(R.drawable.bg_vip_overlay)
        }
        container.addView(TextView(this@CodemagicCenterActivity).apply {
            text = "🚀 نشر الرابط للمستخدمين؟"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this@CodemagicCenterActivity).apply {
            text = "لن يتم تحميل الـ APK داخل لوحة التحكم.\nسيتم إرسال رابط Codemagic مباشرة.\n\nالنسخة: $finalVersion\nVersionCode: $finalVersionCode"
            setTextColor(Color.parseColor("#F2F4FF"))
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
            setLineSpacing(4f, 1.05f)
            gravity = Gravity.CENTER
        })
        var dialogRef: AlertDialog? = null
        val row = LinearLayout(this@CodemagicCenterActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        row.addView(VipUiHelper.buildMiniButton(this@CodemagicCenterActivity, "إلغاء", VipUiHelper.BtnVariant.NEON_PURPLE) {
            dialogRef?.dismiss()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        row.addView(VipUiHelper.buildMiniButton(this@CodemagicCenterActivity, "🚀 نشر", VipUiHelper.BtnVariant.GOLD) {
            doPublish(buildId, finalVersion, finalVersionCode, apkUrl)
            dialogRef?.dismiss()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        container.addView(row)

        dialogRef = AlertDialog.Builder(this).setView(container).create()
        dialogRef!!.setOnShowListener {
            container.alpha = 0f
            container.scaleX = 0.94f; container.scaleY = 0.94f
            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start()
        }
        dialogRef!!.show()
    }

    private fun doPublish(buildId: String, version: String, versionCode: String, apkUrl: String) {
        showProgress("⏳ جاري نشر التحديث...")
        status("جاري نشر التحديث للمستخدمين...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = buildString {
                    append(GOOGLE_SCRIPT)
                    append("?action=set_app_update")
                    append("&secret=").append(enc(SECRET))
                    append("&version_code=").append(enc(versionCode))
                    append("&version_name=").append(enc(version))
                    append("&apk_url=").append(enc(apkUrl))
                    append("&force_update=false")
                    append("&notes_ar=").append(enc("تحديث جديد من LATCHI IPTV عبر لوحة التحكم الذكية."))
                }
                val txt = simpleGet(url)
                val js = JSONObject(txt)
                if (!js.optBoolean("success", false)) throw Exception(js.optString("message", txt))
                prefs.edit()
                    .putString("published_build_id", buildId)
                    .putString("downloaded_build_id", buildId)
                    .putString("downloaded_version", version)
                    .putString("downloaded_version_code", versionCode)
                    .putString("downloaded_apk_url", apkUrl)
                    .apply()
                withContext(Dispatchers.Main) {
                    hideProgress()
                    status("✅ تم نشر التحديث للمستخدمين")
                    VipUiHelper.showSuccessOverlay(
                        this@CodemagicCenterActivity,
                        title = "🚀 تم النشر بنجاح",
                        message = "النسخة: $version\nVersionCode: $versionCode\nتم إخطار المستخدمين في تطبيق المشاهدة ✓",
                        primaryText = "OK",
                        onPrimary = {}
                    )
                    if (selectedAppId.isNotBlank()) renderBuilds(selectedAppId, selectedAppName)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideProgress()
                    status("❌ فشل النشر: ${e.localizedMessage}")
                    VipUiHelper.showErrorOverlay(this@CodemagicCenterActivity, "❌ فشل النشر:\n${e.localizedMessage}")
                }
            }
        }
    }

    // ==================== HTTP HELPERS ====================

    private fun cmGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("x-auth-token", cmToken())
            .get()
            .build()
        return client.newCall(request).execute().use { res ->
            val txt = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw Exception("Codemagic HTTP ${res.code}: $txt")
            }
            txt
        }
    }

    private fun cmPostJson(url: String, body: String): String {
        val request = Request.Builder()
            .url(url)
            .header("x-auth-token", cmToken())
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(request).execute().use { res ->
            val txt = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw Exception("Codemagic HTTP ${res.code}: $txt")
            }
            txt
        }
    }

    private fun simpleGet(url: String): String {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { res ->
            val txt = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw Exception("HTTP ${res.code}: $txt")
            txt
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    // ==================== UI HELPERS ====================

    private fun addInfoCard(t: String, d: String) {
        val c = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(TextView(this@CodemagicCenterActivity).apply {
                text = t
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@CodemagicCenterActivity).apply {
                text = d
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(4f, 1.05f)
            })
        }
        content.addView(c, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })
    }

    private fun status(t: String) { statusText.text = t }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
