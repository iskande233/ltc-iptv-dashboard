package com.latchi.admin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedSource(
    val id: String,
    var name: String,
    var url: String,
    var type: String = "auto",
    var active: Boolean = false,
    var online: Boolean = false,
    var expiry: String = "",
    var responseMs: Long = 0L,
    var lastCheckedAt: Long = 0L,
    var note: String = ""
)

object SourceBankPrefs {
    private const val PREFS = "source_bank_prefs"
    private const val KEY = "saved_sources_json"

    fun load(context: Context): MutableList<SavedSource> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val out = mutableListOf<SavedSource>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                SavedSource(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    type = o.optString("type", "auto"),
                    active = o.optBoolean("active", false),
                    online = o.optBoolean("online", false),
                    expiry = o.optString("expiry", ""),
                    responseMs = o.optLong("responseMs", 0L),
                    lastCheckedAt = o.optLong("lastCheckedAt", 0L),
                    note = o.optString("note", "")
                )
            )
        }
        return out
    }

    fun save(context: Context, items: List<SavedSource>) {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("type", s.type)
                put("active", s.active)
                put("online", s.online)
                put("expiry", s.expiry)
                put("responseMs", s.responseMs)
                put("lastCheckedAt", s.lastCheckedAt)
                put("note", s.note)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(context: Context, source: SavedSource) {
        val items = load(context)
        val index = items.indexOfFirst { it.id == source.id }
        if (index >= 0) items[index] = source else items.add(0, source)
        save(context, items)
    }

    fun delete(context: Context, id: String) {
        val items = load(context).filter { it.id != id }
        save(context, items)
    }

    fun setActive(context: Context, id: String) {
        val items = load(context)
        items.forEach { it.active = it.id == id }
        save(context, items)
    }
}
