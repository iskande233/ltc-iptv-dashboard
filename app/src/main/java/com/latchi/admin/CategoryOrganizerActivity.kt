package com.latchi.admin

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 🗂️ CategoryOrganizerActivity (محسّن ضد الكراش)
 *
 * واجهة جديدة لتنظيم الفئات لكل سيرفر محفوظ:
 *  - عرض كل المصادر المحفوظة
 *  - الضغط على سيرفر → عرض فئاته
 *  - لكل فئة: إعادة تسمية + تحريك
 *  - حفظ محلي + نشر للمستخدمين
 *
 * 🛡️ v5.2.1: محسّن ضد الكراش:
 *  - try/catch في كل عملية حرجة
 *  - إزالة isClickable من الـ card لمنع propagation conflict
 *  - Toast واضح عند أي خطأ
 */
class CategoryOrganizerActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var content: LinearLayout? = null
    private var currentSource: SavedSource? = null
    private var currentCategories: MutableList<CategoryOverridesPrefs.CategoryOverride> = mutableListOf()
    private var currentChannelCounts: Map<String, Int> = emptyMap()
    private var statusText: TextView? = null

    private val savedSourcesList = mutableListOf<SavedSource>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            VipUiHelper.applyWindowBackground(this)
            AdminFloatingBackHelper.setup(this)
            buildRoot()
            loadSavedSourcesAndShowList()
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "onCreate crash", e)
            showCrashScreen(e)
        }
    }

    /**
     * عرض شاشة خطأ بدلاً من الكراش
     */
    private fun showCrashScreen(e: Throwable) {
        try {
            val errorRoot = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(40), dp(40), dp(40), dp(40))
            }
            val errorTitle = TextView(this)
            errorTitle.text = "ERROR: Failed to load interface"
            errorTitle.setTextColor(Color.parseColor("#FF5577"))
            errorTitle.textSize = 18f
            errorTitle.gravity = Gravity.CENTER
            errorTitle.setTypeface(null, android.graphics.Typeface.BOLD)
            errorRoot.addView(errorTitle)

            val errorMsg = TextView(this)
            val className = e.javaClass.simpleName
            val errMsg = e.message?.take(200) ?: "Unknown error"
            errorMsg.text = "Type: " + className + "\n" + errMsg
            errorMsg.setTextColor(Color.parseColor("#B8C0E0"))
            errorMsg.textSize = 13f
            errorMsg.gravity = Gravity.CENTER
            errorMsg.setPadding(0, dp(16), 0, dp(16))
            errorRoot.addView(errorMsg)

            errorRoot.addView(VipUiHelper.buildMiniButton(this, "Back", VipUiHelper.BtnVariant.GOLD) {
                finish()
            })
            setContentView(errorRoot)
        } catch (_: Exception) {
            Toast.makeText(this, "App error", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun buildRoot() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)
        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "🗂️ منظم الفئات",
            subtitle = "إعادة تسمية + ترتيب الفئات لكل سيرفر",
            onBack = { handleBackPress() }
        ))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun handleBackPress() {
        try {
            if (currentSource != null) {
                loadSavedSourcesAndShowList()
            } else {
                if (!isFinishing) finish()
            }
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "handleBackPress crash", e)
            if (!isFinishing) finish()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // MODE 1: قائمة المصادر المحفوظة
    // ════════════════════════════════════════════════════════════════

    private fun loadSavedSourcesAndShowList() {
        try {
            savedSourcesList.clear()
            savedSourcesList.addAll(SourceBankPrefs.load(this).sortedWith(
                compareByDescending<SavedSource> { it.active }
                    .thenByDescending { it.online }
                    .thenBy { if (it.responseMs > 0L) it.responseMs else Long.MAX_VALUE }
                    .thenByDescending { it.lastCheckedAt }
            ))
            renderSourceList()
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "loadSavedSourcesAndShowList crash", e)
            Toast.makeText(this, "❌ خطأ في تحميل المصادر: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderSourceList() {
        try {
            currentSource = null
            content?.removeAllViews()
            content?.addView(sectionTitle("📡 السيرفرات المحفوظة (${savedSourcesList.size})"))
            content?.addView(buildInfoCard())

            if (savedSourcesList.isEmpty()) {
                content?.addView(buildEmptyState())
                return
            }

            savedSourcesList.forEach { source ->
                content?.addView(buildSourceCard(source))
            }
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "renderSourceList crash", e)
        }
    }

    private fun buildInfoCard(): View {
        return VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(TextView(this@CategoryOrganizerActivity).apply {
                text = "ℹ️ كيف تستخدم هذه الواجهة؟"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@CategoryOrganizerActivity).apply {
                text = buildString {
                    append("1) اختر سيرفراً محفوظاً من الأسفل.\n")
                    append("2) اضغط عليه → تظهر كل فئاته.\n")
                    append("3) أعد تسمية أي فئة + تحكم في ترتيبها.\n")
                    append("4) اضغط 💾 لحفظ محلياً أو 📤 لإرسالها للمستخدمين.\n")
                    append("5) التغييرات تظهر في تطبيق المشاهدة تلقائياً.")
                }
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(4f, 1.05f)
            })
        }
    }

    private fun buildEmptyState(): View {
        return VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            gravity = Gravity.CENTER
            addView(TextView(this@CategoryOrganizerActivity).apply {
                text = "📭"
                textSize = 48f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@CategoryOrganizerActivity).apply {
                text = "لا توجد سيرفرات محفوظة بعد"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
            })
            addView(TextView(this@CategoryOrganizerActivity).apply {
                text = "اذهب إلى واجهة 'بنك المصادر' لإضافة روابط أولاً."
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 12f
                gravity = Gravity.CENTER
            })
        }
    }

    /**
     * 🛡️ v5.2.1: إزالة isClickable من الـ card لمنع propagation conflict
     * الزر الداخلي فقط هو clickable (لتجنب double-click على card + button)
     */
    private fun buildSourceCard(source: SavedSource): View {
        val hasOverrides = CategoryOverridesPrefs.load(this, source.id) != null
        return VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            // ❌ قبل: isClickable = true + setOnClickListener → propagation conflict مع الزر الداخلي
            // ✅ بعد: الزر الداخلي فقط هو clickable

            val titleRow = LinearLayout(this@CategoryOrganizerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(TextView(this@CategoryOrganizerActivity).apply {
                text = (if (source.active) "⭐ " else "📂 ") + source.name
                setTextColor(if (source.active) Color.parseColor("#FFD700") else Color.WHITE)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            titleRow.addView(TextView(this@CategoryOrganizerActivity).apply {
                text = if (hasOverrides) "✅ مخصص" else "— افتراضي"
                setTextColor(if (hasOverrides) Color.parseColor("#39FF8B") else Color.parseColor("#7A82A8"))
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(titleRow)

            addView(TextView(this@CategoryOrganizerActivity).apply {
                text = buildString {
                    append(if (source.online) "✅ شغال" else "⚠️ غير مفحوص")
                    append(" • ${source.type.uppercase()}")
                    if (source.responseMs > 0) append(" • ${source.responseMs}ms")
                    append("\n")
                    append(source.url.take(80))
                }
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 11f
                setPadding(0, dp(6), 0, 0)
            })

            // 🛡️ الزر فقط يستجيب للنقر (لا double-click)
            addView(VipUiHelper.buildMiniButton(
                this@CategoryOrganizerActivity,
                if (hasOverrides) "✏️ تعديل الفئات المخصصة" else "🗂️ تخصيص الفئات",
                if (hasOverrides) VipUiHelper.BtnVariant.NEON_GREEN else VipUiHelper.BtnVariant.GOLD
            ) { openCategoryEditor(source) }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply { topMargin = dp(10) })
        }
    }

    // ════════════════════════════════════════════════════════════════
    // MODE 2: محرر الفئات للسيرفر المحدد
    // ════════════════════════════════════════════════════════════════

    private fun openCategoryEditor(source: SavedSource) {
        try {
            currentSource = source
            currentCategories.clear()
            content?.removeAllViews()

            content?.addView(buildEditorHeader(source))
            content?.addView(buildEditorActions())

            statusText = TextView(this).apply {
                text = "⏳ جاري جلب الفئات من السيرفر..."
                setTextColor(Color.parseColor("#A5B4FC"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
            }
            content?.addView(statusText)

            fetchCategoriesForSource(source)
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "openCategoryEditor crash", e)
            Toast.makeText(this, "❌ خطأ في فتح المحرر: ${e.message}", Toast.LENGTH_LONG).show()
            loadSavedSourcesAndShowList()
        }
    }

    private fun buildEditorHeader(source: SavedSource): View {
        return VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(TextView(this@CategoryOrganizerActivity).apply {
                text = "📡 ${source.name}"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@CategoryOrganizerActivity).apply {
                text = source.url.take(90)
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 11f
                setPadding(0, dp(4), 0, dp(8))
            })

            addView(VipUiHelper.buildMiniButton(
                this@CategoryOrganizerActivity,
                "← العودة لاختيار سيرفر آخر",
                VipUiHelper.BtnVariant.NEON_PURPLE
            ) { loadSavedSourcesAndShowList() }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
            ))
        }
    }

    private fun buildEditorActions(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }

        row.addView(VipUiHelper.buildMiniButton(this, "🔄 إعادة الجلب",
            VipUiHelper.BtnVariant.NEON_PURPLE) {
            currentSource?.let { fetchCategoriesForSource(it, forceReload = true) }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(4) })

        row.addView(VipUiHelper.buildMiniButton(this, "💾 حفظ محلي",
            VipUiHelper.BtnVariant.GOLD) {
            saveOverridesLocally()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginStart = dp(4); marginEnd = dp(4)
        })

        row.addView(VipUiHelper.buildMiniButton(this, "📤 نشر للمستخدمين",
            VipUiHelper.BtnVariant.NEON_GREEN) {
            publishOverridesToScript()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4) })

        return row
    }

    private fun fetchCategoriesForSource(source: SavedSource, forceReload: Boolean = false) {
        try {
            statusText?.text = "⏳ جاري جلب الفئات من السيرفر..."
            currentCategories.clear()

            val existingOverrides = CategoryOverridesPrefs.load(this, source.id)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    var realUrl = source.url.replace("&amp;", "&")
                    if (realUrl.contains("get.php") && !realUrl.contains("type=m3u_plus")) {
                        realUrl = if (realUrl.contains("type=m3u"))
                            realUrl.replace("type=m3u", "type=m3u_plus")
                        else "$realUrl&type=m3u_plus"
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
                        if (!body.contains("#EXTINF", ignoreCase = true)) throw Exception("السيرفر لا يحتوي على قنوات M3U")

                        val countsMap = mutableMapOf<String, Int>()
                        val orderList = mutableListOf<String>()
                        var pendingName = ""
                        var pendingGroup = "Other"
                        body.lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            when {
                                trimmed.startsWith("#EXTINF", ignoreCase = true) -> {
                                    pendingName = trimmed.substringAfterLast(",", "").trim()
                                    pendingGroup = Regex("group-title=\"([^\"]*)\"")
                                        .find(trimmed)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { "Other" } ?: "Other"
                                }
                                trimmed.isNotBlank() && !trimmed.startsWith("#") -> {
                                    // منظم الفئات يجب أن يعرض Live TV فقط، لذلك لا نعدّ VOD/Movies/Series.
                                    if (isLiveTvEntryForOrganizer(pendingGroup, pendingName, trimmed)) {
                                        val cleanGroup = pendingGroup.ifBlank { "Other" }
                                        if (!countsMap.containsKey(cleanGroup)) orderList.add(cleanGroup)
                                        countsMap[cleanGroup] = countsMap.getOrDefault(cleanGroup, 0) + 1
                                    }
                                    pendingName = ""
                                    pendingGroup = "Other"
                                }
                            }
                        }

                        currentChannelCounts = countsMap
                        val originalNames = orderList.toList()

                        currentCategories.clear()
                        originalNames.forEachIndexed { index, original ->
                            val customName = existingOverrides?.customNames?.get(original) ?: original
                            currentCategories.add(
                                CategoryOverridesPrefs.CategoryOverride(
                                    originalName = original,
                                    customName = customName,
                                    position = index
                                )
                            )
                        }

                        withContext(Dispatchers.Main) {
                            statusText?.text = "✅ تم جلب ${currentCategories.size} فئة من السيرفر"
                            renderCategoriesList()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText?.text = "❌ فشل جلب الفئات: ${e.message?.take(80) ?: "خطأ غير معروف"}"
                        statusText?.setTextColor(Color.parseColor("#FF5577"))
                        // إذا فشل الجلب، اعرض المحفوظ محلياً إذا موجود
                        if (existingOverrides != null && existingOverrides.customOrder.isNotEmpty()) {
                            currentCategories.clear()
                            existingOverrides.customOrder.forEachIndexed { index, name ->
                                val original = existingOverrides.customNames.entries
                                    .firstOrNull { it.value == name }?.key ?: name
                                currentCategories.add(
                                    CategoryOverridesPrefs.CategoryOverride(
                                        originalName = original,
                                        customName = name,
                                        position = index
                                    )
                                )
                            }
                            renderCategoriesList()
                        } else {
                            // 🛡️ إصلاح: إذا لا توجد بيانات محفوظة، اعرض رسالة بدل شاشة فارغة
                            withContext(Dispatchers.Main) {
                                content?.removeAllViews()
                                currentSource?.let { content?.addView(buildEditorHeader(it)) }
                                content?.addView(buildEditorActions())
                                statusText?.let { content?.addView(it) }
                                content?.addView(TextView(this@CategoryOrganizerActivity).apply {
                                    text = "⚠️ تعذر جلب الفئات من السيرفر.\n\nالأسباب المحتملة:\n• السيرفر لا يستجيب\n• الرابط يحتاج تسجيل دخول (credentials)\n• خطأ في صيغة الرابط\n\nيمكنك إعادة المحاولة أو العودة لاختيار سيرفر آخر."
                                    setTextColor(Color.parseColor("#FFB347"))
                                    textSize = 13f
                                    setPadding(dp(20), dp(40), dp(20), dp(40))
                                })
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "fetchCategoriesForSource crash", e)
        }
    }

    private fun renderCategoriesList() {
        try {
            content?.removeAllViews()
            currentSource?.let { content?.addView(buildEditorHeader(it)) }
            content?.addView(buildEditorActions())
            statusText?.let { content?.addView(it) }

            content?.addView(sectionTitle("📋 الفئات (${currentCategories.size})"))

            if (currentCategories.isEmpty()) {
                content?.addView(TextView(this).apply {
                    text = "⏳ لا توجد فئات بعد. اضغط 'إعادة الجلب' أولاً."
                    setTextColor(Color.parseColor("#8891B8"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, dp(20))
                })
                return
            }

            currentCategories.forEachIndexed { index, override ->
                content?.addView(buildCategoryRow(override, index))
            }
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "renderCategoriesList crash", e)
        }
    }

    private fun buildCategoryRow(override: CategoryOverridesPrefs.CategoryOverride, displayIndex: Int): View {
        val card = VipUiHelper.buildCard(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row1.addView(TextView(this).apply {
            text = "#${displayIndex + 1}"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, dp(8), 0)
        })

        val originalText = TextView(this).apply {
            text = if (override.originalName == override.customName)
                "📂 ${override.originalName}"
            else
                "📂 ${override.originalName} → ${override.customName}"
            setTextColor(if (override.originalName == override.customName)
                Color.WHITE else Color.parseColor("#39FF8B"))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row1.addView(originalText)

        val moveButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        fun addMoveBtn(symbol: String, enabled: Boolean, onClick: () -> Unit) {
            val btn = TextView(this).apply {
                text = symbol
                setTextColor(if (enabled) Color.parseColor("#7FE6FF") else Color.parseColor("#444466"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#1E1E38"))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                isClickable = enabled
                isFocusable = enabled
                if (enabled) setOnClickListener { onClick() }
            }
            moveButtons.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(2) })
        }

        addMoveBtn("⤒", displayIndex > 0) { moveCategory(displayIndex, 0) }
        addMoveBtn("↑", displayIndex > 0) { moveCategory(displayIndex, displayIndex - 1) }
        addMoveBtn("↓", displayIndex < currentCategories.size - 1) { moveCategory(displayIndex, displayIndex + 1) }
        addMoveBtn("⤓", displayIndex < currentCategories.size - 1) { moveCategory(displayIndex, currentCategories.size - 1) }

        row1.addView(moveButtons)
        card.addView(row1)

        val count = currentChannelCounts[override.originalName] ?: 0
        val customInput = EditText(this).apply {
            setText(override.customName)
            hint = "الاسم المخصص للفئة (يظهر للمستخدم)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#7788AA"))
            textSize = 13f
            setBackgroundResource(R.drawable.bg_vip_input)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    override.customName = s.toString().trim()
                    originalText.text = if (override.originalName == override.customName)
                        "📂 ${override.originalName}"
                    else
                        "📂 ${override.originalName} → ${override.customName}"
                    originalText.setTextColor(if (override.originalName == override.customName)
                        Color.WHITE else Color.parseColor("#39FF8B"))
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        card.addView(customInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        row3.addView(TextView(this).apply {
            text = "📺 $count قناة"
            setTextColor(Color.parseColor("#8891B8"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val resetBtn = TextView(this).apply {
            text = if (override.originalName == override.customName) "—" else "↺ إعادة للاسم الأصلي"
            setTextColor(if (override.originalName == override.customName)
                Color.parseColor("#444466") else Color.parseColor("#7FE6FF"))
            textSize = 11f
            setPadding(dp(6), dp(2), dp(6), dp(2))
            isClickable = override.originalName != override.customName
            if (override.originalName != override.customName) {
                setOnClickListener {
                    override.customName = override.originalName
                    renderCategoriesList()
                }
            }
        }
        row3.addView(resetBtn)
        card.addView(row3)

        return card
    }

    // ════════════════════════════════════════════════════════════════
    // Actions: حفظ محلي + نشر
    // ════════════════════════════════════════════════════════════════

    private fun moveCategory(fromIndex: Int, toIndex: Int) {
        try {
            if (fromIndex == toIndex) return
            if (fromIndex < 0 || fromIndex >= currentCategories.size) return
            if (toIndex < 0 || toIndex >= currentCategories.size) return
            val item = currentCategories.removeAt(fromIndex)
            currentCategories.add(toIndex, item)
            currentCategories.forEachIndexed { i, cat -> cat.position = i }
            renderCategoriesList()
            statusText?.text = "✅ تم تحريك '${item.customName}' إلى الموقع #${toIndex + 1}"
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "moveCategory crash", e)
        }
    }

    private fun saveOverridesLocally(returnToList: Boolean = true) {
        try {
            val source = currentSource ?: return
            val customNames = mutableMapOf<String, String>()
            val customOrder = mutableListOf<String>()
            currentCategories.forEach { cat ->
                if (cat.originalName != cat.customName && cat.customName.isNotBlank()) {
                    customNames[cat.originalName] = cat.customName
                }
                customOrder.add(if (cat.customName.isBlank()) cat.originalName else cat.customName)
            }
            CategoryOverridesPrefs.save(
                this, source.id, source.url, source.name,
                customNames, customOrder
            )
            statusText?.text = "✅ تم حفظ ${customNames.size} تعديل محلياً في الهاتف"
            statusText?.setTextColor(Color.parseColor("#39FF8B"))
            Toast.makeText(this, "✅ تم الحفظ محلياً", Toast.LENGTH_SHORT).show()
            // تحديث القائمة لإظهار حالة "مخصص" عند الحفظ المحلي فقط.
            // أثناء النشر نبقى داخل شاشة الفئات حتى يظهر status النهائي للمستخدم.
            if (returnToList) loadSavedSourcesAndShowList()
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "saveOverridesLocally crash", e)
            Toast.makeText(this, "❌ خطأ في الحفظ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun publishOverridesToScript() {
        try {
            val source = currentSource ?: return
            saveOverridesLocally(returnToList = false)

            statusText?.text = "⏳ جاري نشر التعديلات للمستخدمين عبر action=save_category_overrides..."
            statusText?.setTextColor(Color.parseColor("#A5B4FC"))

            val customNames = mutableMapOf<String, String>()
            val customOrder = mutableListOf<String>()
            currentCategories.forEach { cat ->
                if (cat.originalName != cat.customName && cat.customName.isNotBlank()) {
                    customNames[cat.originalName] = cat.customName
                }
                customOrder.add(if (cat.customName.isBlank()) cat.originalName else cat.customName)
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val apiUrl = getSharedPreferences("admin_prefs", MODE_PRIVATE)
                        .getString("apiUrl", "https://script.google.com/macros/s/AKfycbxThygspXN6eB8cDUfY7XavKmhXZfewEUfQqd3vARScZ5y7adterInsbXshNkgPgfiF/exec") ?: ""

                    val namesJson = org.json.JSONObject(customNames as Map<*, *>).toString()
                    val orderJson = org.json.JSONArray(customOrder).toString()
                    val urlHash = simpleHash_(source.url)

                    val url = buildString {
                        append(apiUrl)
                        append("?action=save_category_overrides")
                        append("&secret=").append(java.net.URLEncoder.encode("LatchiAdmin2026", "UTF-8"))
                        append("&source_id=").append(java.net.URLEncoder.encode(source.id, "UTF-8"))
                        append("&source_url=").append(java.net.URLEncoder.encode(source.url, "UTF-8"))
                        append("&source_name=").append(java.net.URLEncoder.encode(source.name, "UTF-8"))
                        append("&custom_names=").append(java.net.URLEncoder.encode(namesJson, "UTF-8"))
                        append("&custom_order=").append(java.net.URLEncoder.encode(orderJson, "UTF-8"))
                        append("&url_hash=").append(java.net.URLEncoder.encode(urlHash, "UTF-8"))
                    }
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { res ->
                        val body = res.body?.string().orEmpty()
                        if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                        val json = org.json.JSONObject(body)
                        if (!json.optBoolean("success", false)) throw Exception(json.optString("message", body))
                        withContext(Dispatchers.Main) {
                            val rev = json.optLong("server_revision", json.optLong("newRevision", 0L))
                            statusText?.text = "✅ تم نشر ${customNames.size} تعديل + ترتيب ${customOrder.size} فئة للمستخدمين" +
                                (if (rev > 0L) " • Revision: $rev" else "")
                            statusText?.setTextColor(Color.parseColor("#39FF8B"))
                            Toast.makeText(
                                this@CategoryOrganizerActivity,
                                "📤 تم النشر!\n${customNames.size} اسم مخصص + ${customOrder.size} ترتيب\nالتغييرات ستظهر للمستخدمين خلال ثوانٍ",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText?.text = "❌ فشل النشر: ${e.message?.take(80) ?: "خطأ غير معروف"}\n(التعديلات محفوظة محلياً)"
                        statusText?.setTextColor(Color.parseColor("#FF5577"))
                        Toast.makeText(
                            this@CategoryOrganizerActivity,
                            "❌ فشل النشر ولكن التعديلات محفوظة محلياً",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("CategoryOrg", "publishOverridesToScript crash", e)
            Toast.makeText(this, "❌ خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#7FE6FF"))
        textSize = 15f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(dp(4), dp(16), 0, dp(10))
    }

    /** Live-only filter for Category Organizer: يستبعد تلقائياً VOD/Movies/Series من M3U. */
    private fun isLiveTvEntryForOrganizer(group: String, name: String, url: String): Boolean {
        val text = "$group $name $url".lowercase()
        val vodTokens = listOf(
            "/movie/", "/vod/", "/series/", "movie/", "series/",
            "movie", "movies", "vod", "film", "films", "cinema",
            "series", "serie", "séries", "مسلسل", "مسلسلات", "افلام", "أفلام", "فيلم"
        )
        return vodTokens.none { text.contains(it) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /**
     * simpleHash_ — djb2 algorithm
     * (source.url.hashCode() doesn't work in some contexts)
     */
    private fun simpleHash_(str: String): String {
        var hash = 5381L
        val s = str ?: ""
        for (i in s.indices) {
            hash = ((hash shl 5) + hash) + s[i].code
            hash = hash and 0x7FFFFFFFFFFFFFFFL
        }
        return hash.toString().replace("-", "n")
    }
}
