package com.latchi.admin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🛡️ CategoryOverridesPrefs
 *
 * تخزين محلي لتعديلات الفئات لكل مصدر محفوظ:
 *  - customNames: Map من (الاسم الأصلي) -> (الاسم المخصص)
 *  - customOrder: قائمة مرتبة بأسماء الفئات (المخصصة أو الأصلية)
 *
 * مثال:
 *   Source "Atlas 1" → customNames: {"Sport AR" -> "الرياضية العربية", ...}
 *                      customOrder: ["الرياضية العربية", "Movies", ...]
 */
object CategoryOverridesPrefs {
    private const val PREFS = "category_overrides_prefs"
    private const val KEY_PREFIX = "overrides_"

    /**
     * تعديلات فئة واحدة
     */
    data class CategoryOverride(
        val originalName: String,
        var customName: String,
        var position: Int
    )

    /**
     * تعديلات مصدر كامل
     */
    data class SourceOverrides(
        val sourceId: String,
        val sourceUrl: String,
        val sourceName: String,
        val customNames: MutableMap<String, String>,
        val customOrder: MutableList<String>
    )

    fun save(context: Context, sourceId: String, sourceUrl: String, sourceName: String,
             customNames: Map<String, String>, customOrder: List<String>) {
        val json = JSONObject().apply {
            put("sourceId", sourceId)
            put("sourceUrl", sourceUrl)
            put("sourceName", sourceName)
            put("customNames", JSONObject(customNames))
            put("customOrder", JSONArray(customOrder))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + sourceId, json.toString())
            .apply()
    }

    fun load(context: Context, sourceId: String): SourceOverrides? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + sourceId, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val namesJson = json.optJSONObject("customNames") ?: JSONObject()
            val orderJson = json.optJSONArray("customOrder") ?: JSONArray()
            val customNames = mutableMapOf<String, String>()
            namesJson.keys().forEach { key ->
                customNames[key] = namesJson.optString(key, key)
            }
            val customOrder = mutableListOf<String>()
            for (i in 0 until orderJson.length()) {
                customOrder.add(orderJson.optString(i, ""))
            }
            SourceOverrides(
                sourceId = json.optString("sourceId", sourceId),
                sourceUrl = json.optString("sourceUrl", ""),
                sourceName = json.optString("sourceName", ""),
                customNames = customNames,
                customOrder = customOrder
            )
        }.getOrNull()
    }

    fun loadForUrl(context: Context, sourceUrl: String): SourceOverrides? {
        // Find any source overrides matching this URL
        val all = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
        for ((key, value) in all) {
            if (key.startsWith(KEY_PREFIX) && value is String) {
                runCatching {
                    val json = JSONObject(value)
                    if (json.optString("sourceUrl", "") == sourceUrl.trim()) {
                        return load(context, json.optString("sourceId", ""))
                    }
                }
            }
        }
        return null
    }

    fun delete(context: Context, sourceId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PREFIX + sourceId)
            .apply()
    }

    fun listAllSourcesWithOverrides(context: Context): List<String> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .map { it.removePrefix(KEY_PREFIX) }
    }

    /**
     * يُرجع الاسم المخصص إذا موجود، وإلا الاسم الأصلي
     */
    fun getDisplayName(overrides: SourceOverrides?, originalName: String): String {
        if (overrides == null) return originalName
        return overrides.customNames[originalName] ?: originalName
    }

    /**
     * يُرجع القائمة المرتبة (مخصصة أو أصلية)
     * إذا customOrder فارغ → يرجع alphabetical sort
     */
    fun getOrderedCategories(overrides: SourceOverrides?, originalCategories: List<String>): List<String> {
        if (overrides == null || overrides.customOrder.isEmpty()) {
            return originalCategories.sortedBy { getDisplayName(null, it).lowercase() }
        }
        // ترتيب حسب customOrder مع الحفاظ على الفئات الجديدة في النهاية
        val orderSet = overrides.customOrder.toSet()
        val ordered = mutableListOf<String>()
        val nameMap = overrides.customNames
        for (name in overrides.customOrder) {
            // ابحث عن الـ original name المرتبط
            val original = nameMap.entries.firstOrNull { it.value == name }?.key ?: name
            if (originalCategories.contains(original)) {
                ordered.add(name)
            }
        }
        // إضافة الفئات الجديدة (غير موجودة في customOrder)
        for (cat in originalCategories) {
            val displayName = nameMap[cat] ?: cat
            if (!ordered.contains(displayName)) {
                ordered.add(displayName)
            }
        }
        return ordered
    }
}
