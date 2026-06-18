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

    companion object {
        private const val DEFAULT_API_URL = "https://script.google.com/macros/s/AKfycbwoxD7eNi6AVvhw9l_hPzaUkVt1F9U6trUXs28QYuNld_Ip15ZoefcTAdkd4B_DqoGO/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        setContentView(R.layout.activity_category_visibility_control)

        setFindViewById()
        setupListeners()
    }

    private fun setFindViewById() {
        btnBack = findViewById(R.id.btnBack)
        inputServerUrl = findViewById(R.id.inputServerUrl)
        btnFetchMaster = findViewById(R.id.btnFetchMaster)
        btnPasteUrl = findViewById(R.id.btnPasteUrl)
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

    private fun fetchMasterUrl() {
        progressBar.visibility = View.VISIBLE
        statusText.text = "⏳ جاري جلب السيرفر المعمم من Google Script..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val url = "$apiUrl?action=get_live_master_state"
                val req = Request.Builder().url(url).get().build()

                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val masterUrl = json.optString("master_url", json.optString("masterUrl", ""))

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

                    // قراءة الفئات المخفية القديمة من SharedPreferences إذا موجودة
                    val hiddenPrefs = getSharedPreferences("latchi_hidden_groups_db", MODE_PRIVATE)
                    val oldHidden = (hiddenPrefs.getString("hidden_list", "") ?: "").split(",").map { it.trim() }.toSet()

                    val items = countsMap.map { (name, count) ->
                        GroupItem(name, count, !oldHidden.contains(name))
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
        if (extractedGroups.isEmpty()) {
            VipUiHelper.showErrorOverlay(this, "❌ استخرج الفئات أولاً")
            return
        }

        val hiddenGroups = extractedGroups.filter { !it.isVisible }.map { it.name }
        val hiddenStr = hiddenGroups.joinToString(",")

        // حفظ محلياً في لوحة التحكم
        getSharedPreferences("latchi_hidden_groups_db", MODE_PRIVATE).edit()
            .putString("hidden_list", hiddenStr).apply()

        // 💡 إظهار حوار تعميم الخيارات
        val msg = buildString {
            append("تم تحديد ").append(hiddenGroups.size).append(" فئات للإخفاء 🔴\n")
            append("و ").append(extractedGroups.size - hiddenGroups.size).append(" فئات للإظهار 🟢\n\n")
            append("خيارات التعميم الملكية:\n")
            append("1. إرسال قائمة الإخفاء إلى Google Script (ليقرأها تطبيق المشاهدة).\n")
            append("2. الخيار الأسطوري: إنشاء وتعميم رابط M3U جديد يحتوي فقط على الفئات الظاهرة (تطبيق الإخفاء 100% من المصدر).")
        }

        AlertDialog.Builder(this)
            .setTitle("🚀 تأكيد حفظ وتعميم الإخفاء")
            .setMessage(msg)
            .setPositiveButton("💾 حفظ في السكريبت + ⚡ توليد M3U مفلتر") { _, _ ->
                executeFullRoyalVisibilityBroadcast(hiddenGroups.toSet(), true)
            }
            .setNeutralButton("💾 حفظ في السكريبت فقط") { _, _ ->
                executeFullRoyalVisibilityBroadcast(hiddenGroups.toSet(), false)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /**
     * التنفيذ الملكي الشامل للتعميم:
     */
    private fun executeFullRoyalVisibilityBroadcast(hiddenSet: Set<String>, generateCleanM3u: Boolean) {
        progressBar.visibility = View.VISIBLE
        statusText.text = "⏳ جاري إرسال إعدادات الإخفاء إلى Google Script..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE).getString("apiUrl", DEFAULT_API_URL) ?: DEFAULT_API_URL
                val encSecret = enc(SECRET)
                val hiddenParam = enc(hiddenSet.joinToString(","))

                // 1. إرسال Action إلى سكريبت جوجل
                val saveUrl = "$apiUrl?action=save_hidden_categories&secret=$encSecret&hidden_categories=$hiddenParam"
                try {
                    client.newCall(Request.Builder().url(saveUrl).get().build()).execute().close()
                } catch (_: Exception) {}

                // 2. إذا اختار توليد M3U نظيف
                var cleanMasterUrl = ""
                if (generateCleanM3u) {
                    withContext(Dispatchers.Main) { statusText.text = "⏳ جاري كتابة وتوليد ملف M3U الصافي بدون الفئات المخفية..." }
                    
                    val rawUrl = inputServerUrl.text.toString().trim()
                    val req = Request.Builder().url(rawUrl).get().build()
                    client.newCall(req).execute().use { res ->
                        val body = res.body?.string() ?: throw Exception("تيار فارغ")
                        
                        val cleanM3uFile = File(filesDir, "latchi_remote_visible_master.m3u")
                        val pairs = mutableListOf<Pair<String, String>>()
                        var currentExt = ""

                        body.lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("#EXTINF", true)) {
                                currentExt = trimmed
                            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#") && currentExt.isNotBlank()) {
                                val group = Regex("group-title=\"([^\"]*)\"").find(currentExt)?.groupValues?.getOrNull(1) ?: "Other"
                                if (!hiddenSet.contains(group.ifBlank { "Other" })) {
                                    pairs.add(currentExt to trimmed)
                                }
                                currentExt = ""
                            }
                        }

                        if (pairs.isEmpty()) throw Exception("الرابط المفلتر فارغ تماماً (كل الفئات مخفية!)")

                        // كتابة الملف
                        cleanM3uFile.bufferedWriter(Charsets.UTF_8).use { w ->
                            w.write("#EXTM3U\n")
                            pairs.forEach { (ext, url) ->
                                w.write(ext); w.write("\n"); w.write(url); w.write("\n")
                            }
                        }

                        withContext(Dispatchers.Main) { statusText.text = "⏳ جاري رفع الرابط النظيف إلى Google Drive/Script..." }
                        val uploadRes = uploadSanitizedM3uToScript(apiUrl, cleanM3uFile)
                        cleanMasterUrl = uploadRes.optString("playlist_url", uploadRes.optString("drive_url", ""))
                        if (cleanMasterUrl.isBlank()) throw Exception("فشل رفع الملف إلى Google Drive: ${uploadRes.optString("message")}")

                        // تعميم الرابط النظيف الموحد
                        val broadcastUrl = "$apiUrl?action=update_master_url&secret=$encSecret&master_url=${enc(cleanMasterUrl)}"
                        client.newCall(Request.Builder().url(broadcastUrl).get().build()).execute().close()
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    val successMsg = if (generateCleanM3u) {
                        "✅ تم حفظ الإخفاء وتعميم الرابط النظيف بنجاح!\nالرابط النهائي:\n$cleanMasterUrl"
                    } else {
                        "✅ تم حفظ إعدادات إخفاء ${hiddenSet.size} فئات في Google Script ✓"
                    }
                    statusText.text = successMsg
                    VipUiHelper.showSuccessOverlay(
                        this@CategoryVisibilityControlActivity,
                        "👁️ تم تطبيق الإخفاء",
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

    private fun uploadSanitizedM3uToScript(apiUrl: String, file: File): JSONObject {
        val content = file.readText(Charsets.UTF_8)
        val form = FormBody.Builder()
            .add("action", "upload_master_m3u")
            .add("secret", SECRET)
            .add("filename", "latchi_remote_visible_master.m3u")
            .add("m3u_content", content)
            .build()
        val req = Request.Builder().url(apiUrl).post(form).build()
        client.newCall(req).execute().use { res ->
            val txt = res.body?.string().orEmpty()
            if (!res.isSuccessful) return JSONObject().put("success", false).put("message", "HTTP ${res.code}: $txt")
            return try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("success", false).put("message", txt) }
        }
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

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
