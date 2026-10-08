package com.janreins.audiobook.data

import android.content.Context
import org.json.JSONObject

class DurationCache(context: Context) {
    private val prefs = context.getSharedPreferences("duration_cache", Context.MODE_PRIVATE)
    private val entries = deserialize(prefs.getString("entries", "{}").orEmpty()).toMutableMap()

    fun get(key: String): Long? = entries[key]
    fun put(key: String, durationMs: Long) { entries[key] = durationMs }
    fun save(seen: Set<String>) {
        if (entries.size > 5000) entries.keys.retainAll(seen)
        prefs.edit().putString("entries", serialize(entries)).apply()
    }

    companion object {
        fun key(documentId: String, size: Long, lastModified: Long) = "$documentId|$size|$lastModified"
        fun serialize(map: Map<String, Long>): String = JSONObject(map).toString()
        fun deserialize(json: String): Map<String, Long> = try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getLong(it) }
        } catch (_: Exception) { emptyMap() }
    }
}
