package com.ai.rankboard.ui.trend

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Preserve every character; avoid splitting UTF-16 surrogate pairs at a chunk boundary. */
fun translationChunks(text: String, maxChars: Int = 500): List<String> {
    require(maxChars >= 2)
    val result = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + maxChars, text.length)
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        result += text.substring(start, end)
        start = end
    }
    return result
}

fun parseTranslationResponse(json: String): String {
    val root = JsonParser.parseString(json).asJsonObject
    check(root.get("responseStatus")?.asInt == 200) { "翻译服务暂不可用或已达免费额度，请稍后重试" }
    return root.getAsJsonObject("responseData")?.get("translatedText")?.asString
        ?.takeIf { it.isNotBlank() } ?: error("翻译服务未返回正文")
}

/** Bounded process-memory-only cache; no article or translation is persisted to disk. */
object ArticleTranslation {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val cache = LinkedHashMap<String, String>()
    suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[text] }?.let { return@withContext it }
        val result = translationChunks(text).map { chunk ->
            coroutineContext.ensureActive()
            val url = "https://api.mymemory.translated.net/get".toHttpUrl().newBuilder()
                .addQueryParameter("q", chunk).addQueryParameter("langpair", "en|zh-CN").build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "翻译请求失败（HTTP ${response.code}），请重试" }
                parseTranslationResponse(response.body?.string() ?: error("翻译返回为空"))
            }
        }.joinToString("")
        synchronized(cache) {
            cache[text] = result
            while (cache.size > 100) cache.remove(cache.keys.first())
        }
        result
    }
}
