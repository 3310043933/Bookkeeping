package com.example.foodledger.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class RecognitionResult(val meal: String, val category: String, val suggestedAmount: Double)

class FoodRecognitionService(private val context: Context) {
    suspend fun recognize(images: List<Uri>, provider: ModelProvider, apiKey: String): RecognitionResult =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "请先在左上角菜单中填写 ${provider.displayName} Key" }
            val encoded = images.map(::encodeImage)
            val response = when (provider) {
                ModelProvider.OPENAI -> callOpenAiCompatible("https://api.openai.com/v1/chat/completions", provider.modelName, apiKey, encoded)
                ModelProvider.DEEPSEEK -> callOpenAiCompatible("https://api.deepseek.com/chat/completions", provider.modelName, apiKey, encoded)
                ModelProvider.QWEN -> callOpenAiCompatible("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", provider.modelName, apiKey, encoded)
                ModelProvider.GEMINI -> callGemini(provider.modelName, apiKey, encoded)
                ModelProvider.ANTHROPIC -> callAnthropic(provider.modelName, apiKey, encoded)
            }
            parseResult(response)
        }

    private fun callOpenAiCompatible(url: String, model: String, key: String, images: List<String>): String {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", PROMPT))
        images.forEach { data ->
            content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$data")))
        }
        val body = JSONObject().put("model", model).put("temperature", 0.1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val json = post(url, body, mapOf("Authorization" to "Bearer $key"))
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun callGemini(model: String, key: String, images: List<String>): String {
        val parts = JSONArray().put(JSONObject().put("text", PROMPT))
        images.forEach { data ->
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", data)))
        }
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        val json = post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key", body)
        return json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun callAnthropic(model: String, key: String, images: List<String>): String {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", PROMPT))
        images.forEach { data ->
            content.put(JSONObject().put("type", "image").put("source", JSONObject()
                .put("type", "base64").put("media_type", "image/jpeg").put("data", data)))
        }
        val body = JSONObject().put("model", model).put("max_tokens", 500)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val json = post("https://api.anthropic.com/v1/messages", body, mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
        return json.getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "接口请求失败（${connection.responseCode}）")
            }
            JSONObject(text)
        } finally { connection.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun encodeImage(uri: Uri): String {
        val original = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        val ratio = minOf(1f, 1280f / maxOf(original.width, original.height))
        val bitmap = if (ratio < 1f) Bitmap.createScaledBitmap(
            original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true
        ) else original
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun parseResult(raw: String): RecognitionResult {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(cleaned)
        return RecognitionResult(
            meal = json.optString("meal", "识别到的餐食"),
            category = json.optString("category", "餐饮").takeIf { it in CATEGORIES } ?: "餐饮",
            suggestedAmount = json.optDouble("suggestedAmount", 0.0).takeIf { !it.isNaN() } ?: 0.0
        )
    }

    private companion object {
        val CATEGORIES = setOf("餐饮", "水果", "零食", "饮品", "买菜", "其他")
        const val PROMPT = """请识别所有图片里的食物或饮品，合并成简洁中文名称。只返回JSON，不要Markdown：{"meal":"食物名称","category":"餐饮/水果/零食/饮品/买菜/其他六选一","suggestedAmount":0}。无法从图片确定实际消费金额时，suggestedAmount必须为0。"""
    }
}
