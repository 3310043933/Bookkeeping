package com.example.foodledger.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LedgerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("food_ledger", Context.MODE_PRIVATE)

    fun load(): List<LedgerEntry> = runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val photos = item.optJSONArray("imageUris") ?: JSONArray()
            LedgerEntry(
                id = item.getLong("id"),
                createdAt = item.getLong("createdAt"),
                meal = item.getString("meal"),
                amount = item.getDouble("amount"),
                category = item.getString("category"),
                note = item.optString("note"),
                imageUris = List(photos.length()) { photos.getString(it) }
            )
        }
    }.getOrDefault(emptyList())

    fun save(entries: List<LedgerEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("createdAt", entry.createdAt)
                put("meal", entry.meal)
                put("amount", entry.amount)
                put("category", entry.category)
                put("note", entry.note)
                put("imageUris", JSONArray(entry.imageUris))
            })
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }
}
