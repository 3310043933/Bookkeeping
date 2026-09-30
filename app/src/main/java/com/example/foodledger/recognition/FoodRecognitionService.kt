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

data class RecognitionResult(
    val merchant: String,
    val product: String,
    val description: String,
    val amount: Double,
    val date: String,
    val primaryCategory: String,
    val secondaryCategory: String,
    val rawResponse: String
) {
    val title: String get() = if (merchant.isBlank()) product else "$merchant-$product"
}

class FoodRecognitionService(private val context: Context) {
    suspend fun recognize(images: List<Uri>, provider: ModelProvider, apiKey: String, customPrompt: String, categories: Map<String,List<String>>): RecognitionResult =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "请先在左上角菜单中填写 ${provider.displayName} Key" }
            val encoded = images.map(::encodeImage)
            val categoryRules = categories.entries.joinToString("；") { (primary, secondary) -> "$primary：${secondary.joinToString("、")}" }
            val prompt = "$customPrompt\n可用分类树如下，只能从中选择：$categoryRules"
            val response = when (provider) {
                ModelProvider.OPENAI -> callOpenAiCompatible("https://api.openai.com/v1/chat/completions", provider.modelName, apiKey, encoded, prompt)
                ModelProvider.DEEPSEEK -> callOpenAiCompatible("https://api.deepseek.com/chat/completions", provider.modelName, apiKey, encoded, prompt)
                ModelProvider.QWEN -> callOpenAiCompatible("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", provider.modelName, apiKey, encoded, prompt)
                ModelProvider.GEMINI -> callGemini(provider.modelName, apiKey, encoded, prompt)
                ModelProvider.ANTHROPIC -> callAnthropic(provider.modelName, apiKey, encoded, prompt)
            }
            parseResult(response, categories)
        }

    private fun callOpenAiCompatible(url: String, model: String, key: String, images: List<String>, customPrompt: String): String {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", finalPrompt(customPrompt)))
        images.forEach { data ->
            content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$data")))
        }
        val body = JSONObject().put("model", model).put("temperature", 0.1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val json = post(url, body, mapOf("Authorization" to "Bearer $key"))
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun callGemini(model: String, key: String, images: List<String>, customPrompt: String): String {
        val parts = JSONArray().put(JSONObject().put("text", finalPrompt(customPrompt)))
        images.forEach { data ->
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", data)))
        }
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        val json = post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key", body)
        return json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun callAnthropic(model: String, key: String, images: List<String>, customPrompt: String): String {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", finalPrompt(customPrompt)))
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

    private fun parseResult(raw: String, categories: Map<String,List<String>>): RecognitionResult {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(cleaned)
        val requestedPrimary=json.optString("一级分类").trim()
        val primary=requestedPrimary.takeIf{it in categories} ?: categories.keys.firstOrNull().orEmpty()
        val requestedSecondary=json.optString("二级分类").trim()
        val secondary=requestedSecondary.takeIf{it in categories[primary].orEmpty()} ?: categories[primary]?.firstOrNull().orEmpty()
        return RecognitionResult(
            merchant = json.optString("店家").trim(),
            product = json.optString("商品", "识别到的商品").trim(),
            description = json.optString("主要描述").trim(),
            primaryCategory = primary,
            secondaryCategory = secondary,
            amount = json.optDouble("金额", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            date = json.optString("日期").trim(),
            rawResponse = raw
        )
    }

    private fun finalPrompt(customPrompt: String, categories: Map<String,List<String>> = emptyMap()) = """$customPrompt
必须只返回一个合法JSON对象，不得包含Markdown或额外文字。字段和格式必须严格为：
{"店家":"店家名称，没有则为空字符串","商品":"商品或餐食名称","主要描述":"商品明细、规格或识别说明","金额":0.00,"日期":"2026-09-30","一级分类":"餐饮","二级分类":"外卖"}
金额必须是JSON数字。“一级分类”和“二级分类”必须同时返回，且二级分类必须属于一级分类。"""

    private companion object {
    }
}
