package com.latchi.admin

import android.content.Context
import java.io.File

object PreparedLocalCatalogStore {
    private const val DIR = "prepared_catalogs"

    private fun file(context: Context, sourceId: String, type: String): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        return File(dir, "${sourceId}_${type}.json")
    }

    fun save(context: Context, sourceId: String, type: String, json: String) {
        file(context, sourceId, type).writeText(json, Charsets.UTF_8)
    }

    fun read(context: Context, sourceId: String, type: String): String? {
        val f = file(context, sourceId, type)
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    fun exists(context: Context, sourceId: String, type: String): Boolean = file(context, sourceId, type).exists()

    fun deleteType(context: Context, sourceId: String, type: String) {
        file(context, sourceId, type).delete()
    }

    fun deleteAllForSource(context: Context, sourceId: String) {
        listOf("live", "bein", "movies", "series").forEach { deleteType(context, sourceId, it) }
    }
}
