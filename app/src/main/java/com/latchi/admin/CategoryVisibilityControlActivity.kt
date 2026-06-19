package com.latchi.admin

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 👑 CategoryVisibilityControlActivity
 *
 * الشاشة الملكية الحصرية للتحكم في إخفاء وإظهار الفئات (Groups) في السيرفر.
 * تتيح للمدير:
 * 1. جلب السيرفر المعمم أو وضع أي رابط M3U/Xtream.
 * 2. استخراج جميع الفئات بضغطة زر.
 * 3. تبديل (🟢 إظهار / 🔴 إخفاء) لأي فئة، أو للكل دفعة واحدة.
 * 4. حفظ الإعدادات في Google Script + خيار لتوليد وتعميم رابط M3U مفلتر 100%.
 */
class CategoryVisibilityControlActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    private lateinit var btnBack: TextView
    private lateinit var inputServerUrl: EditText
    private lateinit var btnFetchMaster: TextView
    private lateinit var btnPasteUrl: TextView
    private lateinit var btnLoadCurrentConfig: TextView
    private lateinit var inputHiddenCategories: EditText
    private lateinit var inputBeinKeywords: EditText
    private lateinit var inputBeinMaxKeywords: EditText
    private lateinit var inputAlwanKeywords: EditText
    private lateinit var inputSourceName: EditText
    private lateinit var inputSourceUrl: EditText
    private lateinit var btnSaveSource: TextView
    private lateinit var btnTestSavedSources: TextView
    private lateinit var currentPublishedSourceText: TextView
    private lateinit var savedSourcesSummaryText: TextView
    private lateinit var savedSourcesContainer: LinearLayout
    private lateinit var btnExtractGroups: TextView
    private lateinit var quickTogglesRow: LinearLayout
    private lateinit var inputSearchGroup: EditText
    private lateinit var btnShowAll: TextView
    private lateinit var btnHideAll: TextView
    private lateinit var groupsContainerCard: LinearLayout
    private lateinit var groupsSummaryText: TextView
    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var btnSaveAndApply: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // State
    data class GroupItem(val name: String, var channelCount: Int, var isVisible: Boolean)
    private val extractedGroups = mutableListOf<GroupItem>()
    private var displayedGroups = listOf<GroupItem>()
    private lateinit var groupsAdapter: GroupsVisibilityAdapter
    private val savedSources = mutableListOf<SavedSource>()

    companion object {
        private const val DEFAULT_API_URL = "https://script.google.com/macros/s/AKfycbxThygspXN6eB8cDUfY7XavKmhXZfewEUfQqd3vARScZ5y7adterInsbXshNkgPgfiF/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        setContentView(R.layout.activity_category_visibility_control)

        setFindViewById()
        setupListeners()
        loadCurrentRemoteConfig(silent = true)
        loadSavedSources()
        updateCurrentPublishedSourceUi()
    }

    private fun setFindViewById() {
        btnBack = findViewById(R.id.btnBack)
        inputServerUrl = findViewById(R.id.inputServerUrl)
        btnFetchMaster = findViewById(R.id.btnFetchMaster)
        btnPasteUrl = findViewById(R.id.btnPasteUrl)
        btnLoadCurrentConfig = findViewById(R.id.btnLoadCurrentConfig)
        inputHiddenCategories = findViewById(R.id.inputHiddenCategories)
        inputBeinKeywords = findViewById(R.id.inputBeinKeywords)
        inputBeinMaxKeywords = findViewById(R.id.inputBeinMaxKeywords)
        inputAlwanKeywords = findViewById(R.id.inputAlwanKeywords)
        inputSourceName = findViewById(R.id.inputSourceName)
        inputSourceUrl = findViewById(R.id.inputSourceUrl)
        btnSaveSource = findViewById(R.id.btnSaveSource)
        btnTestSavedSources = findViewById(R.id.btnTestSavedSources)
        currentPublishedSourceText = findViewById(R.id.currentPublishedSourceText)
        savedSourcesSummaryText = findViewById(R.id.savedSourcesSummaryText)
        savedSourcesContainer = findViewById(R.id.savedSourcesContainer)
        btnExtractGroups = findViewById(R.id.btnExtractGroups)
        quickTogglesRow = findViewById(R.id.quickTogglesRow)
        inputSearchGroup = findViewById(R.id.inputSearchGroup)
        btnShowAll = findViewById(R.id.btnShowAll)
        btnHideAll = findViewById(R.id.btnHideAll)
        groupsContainerCard = findViewById(R.id.groupsContainerCard)
        groupsSummaryText = findViewById(R.id.groupsSummaryText)
        groupsRecyclerView = findViewById(R.id.groupsRecyclerView)
        btnSaveAndApply = findViewById(R.id.btnSaveAndApply)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        groupsRecyclerView.layoutManager = LinearLayoutManager(this)
        groupsAdapter = GroupsVisibilityAdapter(extractedGroups)
        groupsRecyclerView.adapter = groupsAdapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnPasteUrl.setOnClickListener {
            VipUiHelper.pasteFromClipboard(this) { inputServerUrl.setText(it) }
        }

        btnFetchMaster.setOnClickListener { fetchMasterUrl() }
        btnLoadCurrentConfig.setOnClickListener { loadCurrentRemoteConfig() }
        btnSaveSource.setOnClickListener { saveCurrentSourceToBank() }
        btnTestSavedSources.setOnClickListener { testSavedSources() }

        btnExtractGroups.setOnClickListener { extractGroupsFromUrl() }

        btnShowAll.setOnClickListener {
            extractedGroups.forEach { it.isVisible = true }
            groupsAdapter.notifyDataSetChanged()
            Toast.makeText(this, "🟢 تم تعيين كل الفئات على وضع 'إظهار'", Toast.LENGTH_SHORT).show()
        }

        btnHideAll.setOnClickListener {
            extractedGroups.forEach { it.isVisible = false }
            groupsAdapter.notifyDataSetChanged()
            Toast.makeText(this, "🔴 تم تعيين كل الفئات على وضع 'إخفاء'", Toast.LENGTH_SHORT).show()
        }

        inputSearchGroup.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterDisplayedGroups(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSaveAndApply.setOnClickListener { saveAndApplyVisibilitySettings() }
    }

    private fun apiUrl(): String =
        getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL

    private fun fetchMasterUrl() {
        progressBar.visibility = View.VISIBLE
        statusText.text = "⏳ جاري جلب السيرفر المعمم من Google Script..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "${apiUrl()}?action=get_live_master_state"
                val req = Request.Builder().url(url).get().build()

                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val masterUrl = json.optString(
                        "master_url",
                        json.optString(
                            "masterUrl",
                            json.optString(
                                "default_playlist_url",
                                json.optString("playlist_url", "")
                            )
                        )
                    )

                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        if (masterUrl.isNotBlank()) {
                            inputServerUrl.setText(masterUrl)
                            statusText.text = "✅ تم جلب السيرفر المعمم بنجاح!"
                        } else {
                            statusText.text = "❌ لم يتم العثور على رابط معمم محفوظ."
                            VipUiHelper.showErrorOverlay(this@CategoryVisibilityControlActivity, "❌ السيرفر المعمم غير متوفر أو فارغ.")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "❌ فشل الاتصال: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun loadSavedSources() {
        savedSources.clear()
        savedSources.addAll(SourceBankPrefs.load(this).sortedWith(
            compareByDescending<SavedSource> { it.active }
                .thenByDescending { it.online }
                .thenBy { if (it.responseMs > 0L) it.responseMs else Long.MAX_VALUE }
                .thenByDescending { it.lastCheckedAt }
        ))
        renderSavedSources()
    }

    private fun updateCurrentPublishedSourceUi() {
        val prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        val publishedName = prefs.getString("published_source_name", "")?.trim().orEmpty()
        val publishedUrl = prefs.getString("published_source_url", "")?.trim().orEmpty()
        val publishedExpiry = prefs.getString("published_source_expiry", "")?.trim().orEmpty()
        currentPublishedSourceText.text = when {
            publishedName.isNotBlank() -> buildString {
                append("📡 المصدر المعمم الحالي: ").append(publishedName)
                if (publishedExpiry.isNotBlank()) append("\n").append(publishedExpiry)
                append("\n").append(publishedUrl.take(90))
            }
            publishedUrl.isNotBlank() -> buildString {
                append("📡 المصدر المعمم الحالي:\n")
                if (publishedExpiry.isNotBlank()) append(publishedExpiry).append("\n")
                append(publishedUrl.take(90))
            }
            else -> "📡 المصدر المعمم الحالي: —"
        }
    }

    private fun renderSavedSources() {
        savedSourcesContainer.removeAllViews()
        updateCurrentPublishedSourceUi()
        if (savedSources.isEmpty()) {
            savedSourcesSummaryText.text = "🧾 لا توجد روابط محفوظة بعد"
            return
        }
        val onlineCount = savedSources.count { it.online }
        val active = savedSources.firstOrNull { it.active }
        savedSourcesSummaryText.text = "🧾 ${savedSources.size} رابط محفوظ • ✅ $onlineCount شغال" +
            (if (active != null) " • الرسمي: ${active.name}" else "")

        savedSources.forEach { source ->
            val card = VipUiHelper.buildCard(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val title = TextView(this).apply {
                text = (if (source.active) "⭐ " else "") + source.name
                setTextColor(if (source.active) Color.parseColor("#FFD700") else Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            card.addView(title)
            val subtitle = TextView(this).apply {
                text = buildString {
                    append(if (source.online) "✅ شغال" else "❌ غير مفحوص/متوقف")
                    append(" • ${source.type.uppercase()}")
                    if (source.responseMs > 0) append(" • ${source.responseMs}ms")
                    if (source.expiry.isNotBlank()) append("\n📅 ${source.expiry}")
                    append("\n${source.url.take(85)}")
                }
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 11f
                setPadding(0, dp(6), 0, 0)
            }
            card.addView(subtitle)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            row.addView(VipUiHelper.buildMiniButton(this, "🔎 فحص", VipUiHelper.BtnVariant.NEON_BLUE) {
                testSingleSavedSource(source)
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
            row.addView(VipUiHelper.buildMiniButton(this, "⭐ رسمي", VipUiHelper.BtnVariant.GOLD) {
                SourceBankPrefs.setActive(this@CategoryVisibilityControlActivity, source.id)
                getSharedPreferences("admin_prefs", MODE_PRIVATE).edit()
                    .putString("selected_source_name", source.name)
                    .putString("selected_source_url", source.url)
                    .apply()
                loadSavedSources()
                statusText.text = "⭐ تم تعيين ${source.name} كمصدر محلي رسمي"
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            row.addView(VipUiHelper.buildMiniButton(this, "🚀 تعميم", VipUiHelper.BtnVariant.NEON_GREEN) {
                inputServerUrl.setText(source.url)
                executePublishOnly(source)
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(4) })
            card.addView(row)

            val row2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            row2.addView(VipUiHelper.buildMiniButton(this, "📋 نسخ", VipUiHelper.BtnVariant.NEON_PURPLE) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("source_url", source.url))
                Toast.makeText(this@CategoryVisibilityControlActivity, "✅ تم نسخ الرابط", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) })
            row2.addView(VipUiHelper.buildMiniButton(this, "🗑️ حذف", VipUiHelper.BtnVariant.NEON_PURPLE) {
                SourceBankPrefs.delete(this@CategoryVisibilityControlActivity, source.id)
                loadSavedSources()
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(4) })
            card.addView(row2)

            savedSourcesContainer.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }
    }

    private fun inferSourceType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("get.php") || (lower.contains("username=") && lower.contains("password=")) -> "xtream"
            lower.contains(".m3u") || lower.contains("type=m3u") || lower.contains("type=m3u_plus") -> "m3u"
            else -> "auto"
        }
    }

    private fun saveCurrentSourceToBank() {
        val name = inputSourceName.text.toString().trim()
        val url = inputSourceUrl.text.toString().trim().replace("&amp;", "&")
        if (name.isBlank() || url.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ اكتب اسم المصدر والرابط أولاً")
            return
        }
        val source = SavedSource(
            id = "src_${System.currentTimeMillis()}",
            name = name,
            url = url,
            type = inferSourceType(url)
        )
        SourceBankPrefs.upsert(this, source)
        inputSourceName.setText("")
        inputSourceUrl.setText("")
        loadSavedSources()
        statusText.text = "✅ تم حفظ الرابط محلياً داخل الهاتف"
    }

    private fun testSavedSources() {
        if (savedSources.isEmpty()) {
            VipUiHelper.showErrorOverlay(this, "❌ لا توجد روابط محفوظة لفحصها")
            return
        }
        progressBar.visibility = View.VISIBLE
        statusText.text = "⏳ جاري فحص الروابط المحفوظة..."
        CoroutineScope(Dispatchers.IO).launch {
            savedSources.forEachIndexed { index, source ->
                val tested = testSource(source)
                SourceBankPrefs.upsert(this@CategoryVisibilityControlActivity, tested)
                withContext(Dispatchers.Main) {
                    statusText.text = "⏳ فحص ${index + 1} / ${savedSources.size}: ${source.name}"
                }
            }
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                loadSavedSources()
                statusText.text = "✅ تم فحص كل الروابط المحفوظة وترتيبها"
            }
        }
    }

    private fun testSingleSavedSource(source: SavedSource) {
        progressBar.visibility = View.VISIBLE
        statusText.text = "⏳ جاري فحص ${source.name}..."
        CoroutineScope(Dispatchers.IO).launch {
            val tested = testSource(source)
            SourceBankPrefs.upsert(this@CategoryVisibilityControlActivity, tested)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                loadSavedSources()
                statusText.text = if (tested.online) "✅ ${tested.name} شغال" else "❌ ${tested.name} متوقف أو ضعيف"
            }
        }
    }

    private fun testSource(source: SavedSource): SavedSource {
        val start = System.currentTimeMillis()
        val updated = source.copy()
        return try {
            val xtreamInfo = XtreamMasterInfoHelper.fetchInfo(source.url)
            if (xtreamInfo.success && xtreamInfo.isOnline) {
                updated.online = true
                updated.expiry = if (xtreamInfo.expiryDate == "—") "" else xtreamInfo.expiryDate
                updated.responseMs = System.currentTimeMillis() - start
                updated.lastCheckedAt = System.currentTimeMillis()
                updated.note = xtreamInfo.status
                updated.type = "xtream"
                return updated
            }

            val req = Request.Builder()
                .url(source.url.replace("&amp;", "&"))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android)")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                updated.online = res.isSuccessful && body.contains("#EXTINF", ignoreCase = true)
                updated.expiry = ""
                updated.responseMs = System.currentTimeMillis() - start
                updated.lastCheckedAt = System.currentTimeMillis()
                updated.note = if (updated.online) "M3U صالح" else "HTTP ${res.code}"
                updated.type = if (updated.online) "m3u" else updated.type
            }
            updated
        } catch (e: Exception) {
            updated.online = false
            updated.responseMs = System.currentTimeMillis() - start
            updated.lastCheckedAt = System.currentTimeMillis()
            updated.note = e.localizedMessage ?: "failed"
            updated
        }
    }

    private fun executePublishOnly(source: SavedSource) {
        progressBar.visibility = View.VISIBLE
        statusText.text = "⏳ جاري تجهيز البيانات السريعة + تعميم ${source.name}..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hiddenSet = parseCsvSet(inputHiddenCategories.text?.toString().orEmpty())
                val beinKeywords = inputBeinKeywords.text?.toString().orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }
                val beinMaxKeywords = inputBeinMaxKeywords.text?.toString().orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }
                val alwanKeywords = inputAlwanKeywords.text?.toString().orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }

                withContext(Dispatchers.Main) { statusText.text = "⏳ تجهيز Live و beIN بسرعة..." }
                val catalogs = PreparedCatalogBuilder.buildFastForTv(
                    source.url,
                    hiddenSet,
                    beinKeywords,
                    beinMaxKeywords,
                    alwanKeywords
                )

                withContext(Dispatchers.Main) { statusText.text = "⏳ رفع كتالوج Live..." }
                uploadPreparedCatalog("live", catalogs.liveJson)
                withContext(Dispatchers.Main) { statusText.text = "⏳ رفع كتالوج beIN..." }
                uploadPreparedCatalog("bein", catalogs.beinJson)

                val broadcastUrl = "${apiUrl()}?action=update_master_url&secret=${enc(SECRET)}&master_url=${enc(source.url)}"
                val response = client.newCall(Request.Builder().url(broadcastUrl).get().build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                    JSONObject(body)
                }
                if (!response.optBoolean("success", false)) throw Exception(response.optString("message", "فشل التعميم"))
                getSharedPreferences("admin_prefs", MODE_PRIVATE).edit()
                    .putString("published_source_name", source.name)
                    .putString("published_source_url", source.url)
                    .putString("published_source_expiry", source.expiry)
                    .apply()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    updateCurrentPublishedSourceUi()
                    statusText.text = "✅ تم تعميم ${source.name} وتجهيز Live و beIN بسرعة"
                    VipUiHelper.showSuccessOverlay(
                        this@CategoryVisibilityControlActivity,
                        "🚀 تم تعميم المصدر الرسمي",
                        "المصدر: ${source.name}\nLive: ${catalogs.liveCount}\nbeIN: ${catalogs.beinCount}\nتم تفعيل التسريع الجذري للتلفاز في البث المباشر و beIN.",
                        "حسناً",
                        {}
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "❌ فشل التعميم السريع: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun uploadPreparedCatalog(type: String, jsonContent: String) {
        val form = okhttp3.FormBody.Builder()
            .add("action", "upload_prepared_catalog")
            .add("secret", SECRET)
            .add("catalog_type", type)
            .add("json_content", jsonContent)
            .build()
        val request = Request.Builder().url(apiUrl()).post(form).build()
        client.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) throw Exception(json.optString("message", "failed upload $type"))
        }
    }

    private fun loadCurrentRemoteConfig(silent: Boolean = false) {
        if (!silent) {
            progressBar.visibility = View.VISIBLE
            statusText.text = "⏳ جاري تحميل إعدادات الفلترة الحالية..."
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder().url("${apiUrl()}?action=get_view_config").get().build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    val json = JSONObject(body)
                    if (!json.optBoolean("success", false)) throw Exception(json.optString("message", "تعذر تحميل الإعدادات"))

                    val hidden = json.optString("hidden_categories", json.optString("hiddenCategories", ""))
                    val bein = json.optString("bein_keywords", json.optString("beinKeywords", ""))
                    val beinMax = json.optString("bein_max_keywords", json.optString("beinMaxKeywords", ""))
                    val alwan = json.optString("alwan_keywords", json.optString("alwanKeywords", ""))
                    val masterUrl = json.optString("master_url", json.optString("default_playlist_url", ""))

                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        if (masterUrl.isNotBlank() && inputServerUrl.text.isNullOrBlank()) inputServerUrl.setText(masterUrl)
                        inputHiddenCategories.setText(hidden)
                        inputBeinKeywords.setText(bein)
                        inputBeinMaxKeywords.setText(beinMax)
                        inputAlwanKeywords.setText(alwan)
                        if (!silent) statusText.text = "✅ تم تحميل إعدادات الفلترة الحالية من السكريبت"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (!silent) {
                        statusText.text = "❌ فشل تحميل الإعدادات: ${e.localizedMessage}"
                        VipUiHelper.showErrorOverlay(this@CategoryVisibilityControlActivity, "❌ تعذر تحميل إعدادات الفلترة:\n${e.localizedMessage}")
                    }
                }
            }
        }
    }

    private fun extractGroupsFromUrl() {
        val rawUrl = inputServerUrl.text.toString().trim()
        if (rawUrl.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ أدخل رابط السيرفر أولاً")
            return
        }

        progressBar.visibility = View.VISIBLE
        statusText.text = "⚡ جاري الاتصال بالسيرفر واستخراج جميع الفئات..."
        extractedGroups.clear()
        displayedGroups = emptyList()
        quickTogglesRow.visibility = View.GONE
        groupsContainerCard.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var realUrl = rawUrl.replace("&amp;", "&")
                if (realUrl.contains("get.php") && !realUrl.contains("type=m3u_plus")) {
                    realUrl = if (realUrl.contains("type=m3u")) realUrl.replace("type=m3u", "type=m3u_plus") else "$realUrl&type=m3u_plus"
                    if (!realUrl.contains("output=")) realUrl += "&output=ts"
                }

                val req = Request.Builder()
                    .url(realUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android)")
                    .get()
                    .build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                    val body = res.body?.string()?.replace("\uFEFF", "") ?: throw Exception("تيار فارغ")
                    if (!body.contains("#EXTINF", ignoreCase = true)) throw Exception("الرابط لا يحتوي على قنوات M3U صالحة")

                    // استخراج الفئات وعد القنوات
                    val countsMap = mutableMapOf<String, Int>()
                    body.lineSequence().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("#EXTINF", ignoreCase = true)) {
                            val group = Regex("group-title=\"([^\"]*)\"").find(trimmed)?.groupValues?.getOrNull(1) ?: "Other"
                            val cleanGroup = group.ifBlank { "Other" }
                            countsMap[cleanGroup] = countsMap.getOrDefault(cleanGroup, 0) + 1
                        }
                    }

                    val oldHidden = parseCsvSet(inputHiddenCategories.text?.toString().orEmpty())

                    val items = countsMap.map { (name, count) ->
                        GroupItem(name, count, !oldHidden.contains(name.trim()))
                    }.sortedBy { it.name }

                    extractedGroups.addAll(items)

                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        if (extractedGroups.isEmpty()) throw Exception("لم يتم استخراج أي فئة")
                        
                        statusText.text = "✅ تم استخراج ${extractedGroups.size} فئة بنجاح!"
                        quickTogglesRow.visibility = View.VISIBLE
                        groupsContainerCard.visibility = View.VISIBLE
                        groupsSummaryText.text = "📋 الفئات المستخرجة (${extractedGroups.size} فئة)"

                        filterDisplayedGroups("")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "❌ فشل الاستخراج: ${e.localizedMessage}"
                    VipUiHelper.showErrorOverlay(this@CategoryVisibilityControlActivity, "❌ حدث خطأ أثناء قراءة الرابط:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun filterDisplayedGroups(query: String) {
        val q = query.trim().lowercase()
        displayedGroups = if (q.isBlank()) {
            extractedGroups
        } else {
            extractedGroups.filter { it.name.lowercase().contains(q) }
        }
        groupsAdapter.update(displayedGroups)
    }

    private fun saveAndApplyVisibilitySettings() {
        val hiddenGroups = if (extractedGroups.isNotEmpty()) {
            extractedGroups.filter { !it.isVisible }.map { it.name.trim() }
        } else {
            parseCsvSet(inputHiddenCategories.text?.toString().orEmpty()).toList()
        }
        val hiddenStr = hiddenGroups.joinToString(",")
        inputHiddenCategories.setText(hiddenStr)

        val beinKeywords = inputBeinKeywords.text?.toString().orEmpty().trim()
        val beinMaxKeywords = inputBeinMaxKeywords.text?.toString().orEmpty().trim()
        val alwanKeywords = inputAlwanKeywords.text?.toString().orEmpty().trim()

        if (beinKeywords.isBlank() || beinMaxKeywords.isBlank() || alwanKeywords.isBlank()) {
            VipUiHelper.showErrorOverlay(this, "❌ أكمل كلمات beIN / beIN MAX / ALWAN قبل الحفظ")
            return
        }

        val msg = buildString {
            append("سيتم حفظ إعدادات الفلترة الذكية في السكريبت.\n\n")
            append("الفئات المخفية: ").append(hiddenGroups.size).append("\n")
            if (extractedGroups.isNotEmpty()) {
                append("الفئات الظاهرة: ").append(extractedGroups.size - hiddenGroups.size).append("\n")
            }
            append("\nثم سيُرفع server_revision لكي يتحدث تطبيق الهاتف والتلفاز مباشرة.")
        }

        AlertDialog.Builder(this)
            .setTitle("🚀 حفظ إعدادات الفلترة")
            .setMessage(msg)
            .setPositiveButton("💾 حفظ + تعميم") { _, _ ->
                executeFullRoyalVisibilityBroadcast(hiddenGroups.toSet(), generateCleanM3u = extractedGroups.isNotEmpty())
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /**
     * التنفيذ الملكي الشامل للتعميم:
     */
    private fun executeFullRoyalVisibilityBroadcast(hiddenSet: Set<String>, generateCleanM3u: Boolean) {
        progressBar.visibility = View.VISIBLE
        statusText.text = "⏳ جاري حفظ إعدادات الفلترة في Google Script..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = apiUrl()
                val encSecret = enc(SECRET)
                val hiddenParam = enc(hiddenSet.joinToString(","))
                val beinParam = enc(inputBeinKeywords.text?.toString().orEmpty().trim())
                val beinMaxParam = enc(inputBeinMaxKeywords.text?.toString().orEmpty().trim())
                val alwanParam = enc(inputAlwanKeywords.text?.toString().orEmpty().trim())

                val saveUrl = buildString {
                    append(apiUrl)
                    append("?action=save_view_config")
                    append("&secret=").append(encSecret)
                    append("&hidden_categories=").append(hiddenParam)
                    append("&bein_keywords=").append(beinParam)
                    append("&bein_max_keywords=").append(beinMaxParam)
                    append("&alwan_keywords=").append(alwanParam)
                }

                val saveRes = client.newCall(Request.Builder().url(saveUrl).get().build()).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                    JSONObject(body)
                }

                if (!saveRes.optBoolean("success", false)) {
                    throw Exception(saveRes.optString("message", "فشل حفظ الإعدادات"))
                }

                if (generateCleanM3u) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "⏳ تم حفظ الفلاتر. جاري تحديث الرابط المعمم أيضاً..."
                    }
                    val rawUrl = inputServerUrl.text.toString().trim()
                    if (rawUrl.isNotBlank()) {
                        val broadcastUrl = "$apiUrl?action=update_master_url&secret=$encSecret&master_url=${enc(rawUrl)}"
                        try {
                            client.newCall(Request.Builder().url(broadcastUrl).get().build()).execute().close()
                        } catch (_: Exception) {}
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val successMsg = "✅ تم حفظ الفئات المخفية وكلمات beIN / ALWAN في السكريبت، ورفع server_revision بنجاح.\nالتطبيق سيتحدث مباشرة على الهاتف والتلفاز."
                    statusText.text = successMsg
                    VipUiHelper.showSuccessOverlay(
                        this@CategoryVisibilityControlActivity,
                        "⚙️ تم تحديث الفلاتر الذكية",
                        successMsg,
                        "رائع! 🎉",
                        {}
                    )
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "❌ فشل التطبيق: ${e.localizedMessage}"
                    VipUiHelper.showErrorOverlay(this@CategoryVisibilityControlActivity, "❌ حدث خطأ:\n${e.localizedMessage}")
                }
            }
        }
    }

    private fun parseCsvSet(value: String): Set<String> =
        value.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ─────────────────────────────────────────────────────────────
    // محول الفئات
    // ─────────────────────────────────────────────────────────────
    private inner class GroupsVisibilityAdapter(
        private var items: List<GroupItem>
    ) : RecyclerView.Adapter<GroupsVisibilityAdapter.VH>() {

        fun update(newList: List<GroupItem>) {
            this.items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_visibility_card, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameText: TextView = itemView.findViewById(R.id.catNameText)
            private val countText: TextView = itemView.findViewById(R.id.catCountText)
            private val switchBtn: Switch = itemView.findViewById(R.id.catVisibilitySwitch)

            fun bind(item: GroupItem) {
                nameText.text = item.name
                countText.text = "${item.channelCount} قناة"
                
                // فصل المستمع لتفادي التضارب
                switchBtn.setOnCheckedChangeListener(null)
                switchBtn.isChecked = item.isVisible
                switchBtn.text = if (item.isVisible) "🟢 إظهار" else "🔴 إخفاء"
                switchBtn.setTextColor(if (item.isVisible) Color.parseColor("#39FF8B") else Color.parseColor("#FF6B6B"))

                switchBtn.setOnCheckedChangeListener { _, isChecked ->
                    item.isVisible = isChecked
                    switchBtn.text = if (isChecked) "🟢 إظهار" else "🔴 إخفاء"
                    switchBtn.setTextColor(if (isChecked) Color.parseColor("#39FF8B") else Color.parseColor("#FF6B6B"))
                }

                itemView.setOnClickListener {
                    switchBtn.isChecked = !switchBtn.isChecked
                }
            }
        }
    }
}
