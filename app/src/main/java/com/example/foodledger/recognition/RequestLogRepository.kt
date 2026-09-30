package com.example.foodledger.recognition

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ModelRequestLog(
    val id: Long,
    val timestamp: Long,
    val provider: String,
    val model: String,
    val imageCount: Int,
    val prompt: String,
    val response: String,
    val error: String,
    val durationMs: Long
)

class RequestLogRepository(context: Context) {
    private val prefs = context.getSharedPreferences("model_request_logs", Context.MODE_PRIVATE)

    fun load(): List<ModelRequestLog> = runCatching {
        val array = JSONArray(prefs.getString("logs", "[]"))
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            ModelRequestLog(
                id = item.getLong("id"), timestamp = item.getLong("timestamp"),
                provider = item.getString("provider"), model = item.getString("model"),
                imageCount = item.getInt("imageCount"), prompt = item.getString("prompt"),
                response = item.optString("response"), error = item.optString("error"),
                durationMs = item.optLong("durationMs")
            )
        }
    }.getOrDefault(emptyList())

    fun add(log: ModelRequestLog): List<ModelRequestLog> {
        val updated = (listOf(log) + load()).take(50)
        val array = JSONArray()
        updated.forEach { item -> array.put(JSONObject()
            .put("id", item.id).put("timestamp", item.timestamp).put("provider", item.provider)
            .put("model", item.model).put("imageCount", item.imageCount).put("prompt", item.prompt)
            .put("response", item.response).put("error", item.error).put("durationMs", item.durationMs)) }
        prefs.edit().putString("logs", array.toString()).apply()
        return updated
    }

    fun clear() { prefs.edit().remove("logs").apply() }
}
