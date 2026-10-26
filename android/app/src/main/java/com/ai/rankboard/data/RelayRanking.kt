package com.ai.rankboard.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

enum class RelayMatchQuality(val label: String) {
    EXACT("已匹配"),
    INFERRED("推断匹配"),
    NONE("未匹配"),
}

data class RelayRemoteModel(
    val id: String,
    val ownedBy: String? = null,
    val groupName: String? = null,
    val rateMultiplier: Double? = null,
    val actualPrice: RelayPrice? = null,
    val officialPrice: RelayPrice? = null,
)

data class RelayPrice(
    val input: Double? = null,
    val output: Double? = null,
    val cacheWrite: Double? = null,
    val cacheRead: Double? = null,
    val currency: String? = null,
)

data class RelayModelRank(
    val remoteId: String,
    val displayName: String,
    val vendor: String?,
    val relayRank: Int,
    val globalRank: Int?,
    val score: Double?,
    val matchQuality: RelayMatchQuality,
    val matchedSlug: String? = null,
    val groupName: String? = null,
    val rateMultiplier: Double? = null,
    val actualPrice: RelayPrice? = null,
    val officialPrice: RelayPrice? = null,
    val priceMultiplier: Double? = null,
)

data class RelayRanking(
    val url: String,
    val snapshotGeneratedAt: String,
    val models: List<RelayModelRank>,
    val total: Int,
    val matched: Int,
) {
    val inferred: Int get() = models.count { it.matchQuality == RelayMatchQuality.INFERRED }
}

class RelayModelClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetchModels(url: String): List<RelayRemoteModel> =
        withContext(Dispatchers.IO) {
            val endpoints = endpointCandidates(url)
            var lastError: Exception? = null
            var authenticationRequired = false
            for (endpoint in endpoints) {
                coroutineContext.ensureActive()
                try {
                    val models = execute(endpoint)
                    if (models.isNotEmpty()) return@withContext models
                    lastError = IllegalStateException("接口返回成功，但没有找到模型列表")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: RelayHttpException) {
                    authenticationRequired = authenticationRequired || error.status == 401 || error.status == 403
                    lastError = error
                } catch (error: Exception) {
                    lastError = error
                }
            }
            if (authenticationRequired) {
                throw IllegalStateException("该站接口需要密钥或登录，且未读到公开模型列表。请确认模型广场是否对外开放；本功能不发送访问密钥。")
            }
            throw lastError ?: IllegalStateException("无法读取模型列表")
        }

    private fun execute(url: String): List<RelayRemoteModel> {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AI-Rankboard/1.0")

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw RelayHttpException(response.code)
            }
            val body = response.body?.string().orEmpty()
            return parseModels(body).ifEmpty {
                throw IllegalStateException("响应不是可识别的模型列表")
            }
        }
    }

    internal fun endpointCandidates(rawUrl: String): List<String> {
        val normalized = rawUrl.trim()
        val withScheme = if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            normalized
        } else {
            "https://$normalized"
        }
        val parsed = withScheme.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("请输入有效的中转站地址")
        if (parsed.scheme != "https") {
            throw IllegalArgumentException("中转站地址必须使用 HTTPS")
        }
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw IllegalArgumentException("地址不能包含用户名或密码")
        }
        val candidates = LinkedHashSet<String>()
        candidates += parsed.toString()
        val path = parsed.encodedPath.trimEnd('/')
        val origin = parsed.newBuilder().encodedPath("/").query(null).fragment(null).build()
        fun endpoint(path: String) = origin.newBuilder().encodedPath(path).build().toString()
        val plaza = endpoint("/api/v1/model-plaza")
        val openAiModels = endpoint("/v1/models")
        when {
            path.isBlank() || path.endsWith("/model-plaza") -> {
                candidates += plaza
            }
            path.endsWith("/v1") -> {
                candidates += parsed.newBuilder().encodedPath("$path/models")
                    .query(null).fragment(null).build().toString()
            }
        }
        // New API sites expose a public catalogue even when /v1/models needs a token.
        candidates += endpoint("/api/pricing")
        candidates += plaza
        candidates += openAiModels
        return candidates.toList()
    }

    private class RelayHttpException(val status: Int) :
        IllegalStateException("HTTP $status，未能读取公开模型列表")

    companion object {
        fun parseModels(text: String): List<RelayRemoteModel> = runCatching {
            val root = JsonParser.parseString(text)
            if (root.isJsonObject) {
                val obj = root.asJsonObject
                if (obj.has("error") || obj.get("success")?.let { it.isJsonPrimitive && it.asString == "false" } == true) {
                    return emptyList()
                }
                parsePlaza(root.asJsonObject).takeIf { it.isNotEmpty() }?.let { return it }
            }

            val array = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject -> modelArray(root.asJsonObject)
                else -> null
            } ?: return emptyList()

            array.mapNotNull { item ->
                when {
                    item.isJsonPrimitive && item.asJsonPrimitive.isString -> item.asString
                    item.isJsonObject -> item.asJsonObject.get(
                        listOf("id", "model", "model_id", "model_name", "name"),
                    )?.takeIf { it.isJsonPrimitive }?.asString
                    else -> null
                }?.takeIf { it.isNotBlank() }?.let { id ->
                    val owner = if (item.isJsonObject) {
                        item.asJsonObject.get(listOf("owned_by", "owner_by"))
                            ?.takeIf { it.isJsonPrimitive }?.asString
                    } else {
                        null
                    }
                    RelayRemoteModel(id.trim(), owner?.trim()?.takeIf { it.isNotBlank() })
                }
            }.distinctBy { it.id.lowercase() }
        }.getOrDefault(emptyList())

        private fun parsePlaza(root: com.google.gson.JsonObject): List<RelayRemoteModel> {
            val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: root
            val currency = data.get("currency")
                ?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.uppercase()
            val groups = data.get("groups")
                ?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return emptyList()

            return groups.mapNotNull { group ->
                group.takeIf { it.isJsonObject }?.asJsonObject
            }.flatMap { group ->
                val groupName = group.get("name")
                    ?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() }
                val rate = group.get("rate_multiplier")
                    ?.takeIf { it.isJsonPrimitive }?.asDouble
                val models = group.get("models")
                    ?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: return@flatMap emptyList()

                models.mapNotNull { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject
                }.mapNotNull { item ->
                    val id = item.get(
                        listOf("name", "id", "model", "model_id", "model_name"),
                    )?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                    if (id.isBlank()) return@mapNotNull null

                    RelayRemoteModel(
                        id = id,
                        ownedBy = item.get("platform")
                            ?.takeIf { it.isJsonPrimitive }?.asString?.trim(),
                        groupName = groupName,
                        rateMultiplier = rate,
                        actualPrice = parsePrice(item.get("pricing"), currency),
                        officialPrice = parsePrice(item.get("official_pricing"), "USD"),
                    )
                }
            }
        }

        private fun parsePrice(
            element: com.google.gson.JsonElement?,
            currency: String?,
        ): RelayPrice? {
            val obj = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            fun number(keys: List<String>) = obj.get(keys)
                ?.takeIf { it.isJsonPrimitive }
                ?.takeIf { !it.asString.isNullOrBlank() }
                ?.asDouble
                ?.takeIf { !it.isNaN() && !it.isInfinite() }

            return RelayPrice(
                input = number(listOf("input_price", "input")),
                output = number(listOf("output_price", "output")),
                cacheWrite = number(listOf("cache_write_price", "cache_write")),
                cacheRead = number(listOf("cache_read_price", "cache_read")),
                currency = currency?.takeIf { it.isNotBlank() },
            )
        }

        private fun modelArray(obj: com.google.gson.JsonObject): com.google.gson.JsonArray? {
            val plazaGroups = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("groups")?.takeIf { it.isJsonArray }
                ?: obj.get("groups")?.takeIf { it.isJsonArray }
            plazaGroups?.asJsonArray?.let { groups ->
                val models = com.google.gson.JsonArray()
                groups.forEach { group ->
                    group.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("models")?.takeIf { it.isJsonArray }
                        ?.asJsonArray?.forEach(models::add)
                }
                if (models.size() > 0) return models
            }

            val direct = obj.get("data") ?: obj.get("models") ?: obj.get("model_list")
                ?: obj.get("result") ?: obj.get("items")
            direct?.takeIf { it.isJsonArray }?.asJsonArray?.let { return it }
            direct?.takeIf { it.isJsonObject }?.asJsonObject?.let { nested ->
                return modelArray(nested)
            }
            return null
        }

        private fun com.google.gson.JsonObject.get(keys: List<String>): com.google.gson.JsonElement? {
            return keys.firstNotNullOfOrNull { key -> get(key)?.takeIf { !it.isJsonNull } }
        }
    }
}

object RelayModelRanking {
    fun rank(models: List<RelayRemoteModel>, snapshot: Snapshot): RelayRanking {
        val baseEntries = snapshot.entriesByBoard["overall"].orEmpty()
        val ranked = bestOffers(models)
            .map { model -> match(model, baseEntries) }
            .sortedWith(
                compareByDescending<RelayModelRank> { it.matchQuality != RelayMatchQuality.NONE }
                    .thenByDescending { it.score ?: Double.NEGATIVE_INFINITY }
                    .thenBy { it.globalRank ?: Int.MAX_VALUE }
                    .thenBy { it.remoteId.lowercase() },
            )
            .mapIndexed { index, item -> item.copy(relayRank = index + 1) }

        return RelayRanking(
            url = "",
            snapshotGeneratedAt = snapshot.generatedAt,
            models = ranked,
            total = ranked.size,
            matched = ranked.count { it.matchQuality != RelayMatchQuality.NONE },
        )
    }

    private fun bestOffers(models: List<RelayRemoteModel>): List<RelayRemoteModel> =
        models
            .groupBy { it.id.trim().lowercase() }
            .map { (_, offers) ->
                offers.minByOrNull { offer ->
                    listOfNotNull(
                        offer.rateMultiplier,
                        offer.priceMultiplier(),
                    ).minOrNull() ?: Double.MAX_VALUE
                } ?: offers.first()
            }

    private fun match(model: RelayRemoteModel, entries: List<EntryDto>): RelayModelRank {
        val normalizedRemote = normalize(model.id)
        val normalizedRemoteShort = stripVendorPrefix(normalizedRemote)
        val remoteTokens = tokens(normalizedRemoteShort)
        val candidates = entries.mapNotNull { entry ->
            val slugFull = normalize(entry.slug)
            val slugShort = stripVendorPrefix(slugFull)
            val displayBase = normalize(entry.displayName.substringBefore('('))
            var score = when (normalizedRemote) {
                slugFull -> 1000
                displayBase -> 940
                else -> 0
            }
            if (score == 0 && normalizedRemoteShort == slugShort) score = 1000
            if (score == 0 && normalizedRemoteShort == displayBase) score = 940
            if (score == 0) score = tokenScore(remoteTokens, tokens(slugShort))
            if (score == 0) score = tokenScore(remoteTokens, tokens(displayBase))
            score += strengthBonus(model.id, entry)
            score.takeIf { it >= 700 }?.let { score to entry }
        }
        var best: Pair<Int, EntryDto>? = null
        candidates.forEach { candidate ->
            val current = best
            if (current == null || candidateWins(candidate, current)) best = candidate
        }

        val bestCandidate = best
        val entry = bestCandidate?.second
        val quality = when {
            bestCandidate == null -> RelayMatchQuality.NONE
            bestCandidate.first >= 900 -> RelayMatchQuality.EXACT
            else -> RelayMatchQuality.INFERRED
        }
        return RelayModelRank(
            remoteId = model.id.trim(),
            displayName = entry?.displayName ?: model.id.trim(),
            vendor = entry?.vendor ?: guessVendor(model),
            relayRank = 0,
            globalRank = entry?.rank?.takeIf { it > 0 },
            score = entry?.score,
            matchQuality = quality,
            matchedSlug = entry?.slug,
            groupName = model.groupName,
            rateMultiplier = model.rateMultiplier,
            actualPrice = model.actualPrice,
            officialPrice = model.officialPrice,
            priceMultiplier = model.priceMultiplier(),
        )
    }

    private fun RelayRemoteModel.priceMultiplier(): Double? {
        val actual = actualPrice ?: return null
        val official = officialPrice ?: return null
        if (actual.currency != official.currency) return null

        val ratios = buildList {
            actual.input?.let { input -> official.input?.takeIf { it > 0.0 }?.let { official -> add(input / official) } }
            actual.output?.let { output -> official.output?.takeIf { it > 0.0 }?.let { official -> add(output / official) } }
        }.filter { it.isFinite() }
        return ratios.takeIf { it.isNotEmpty() }?.average()
    }

    private fun candidateWins(
        candidate: Pair<Int, EntryDto>,
        current: Pair<Int, EntryDto>,
    ): Boolean {
        if (candidate.first != current.first) {
            return candidate.first > current.first
        }
        val candidateScore = candidate.second.score ?: Double.NEGATIVE_INFINITY
        val currentScore = current.second.score ?: Double.NEGATIVE_INFINITY
        if (candidateScore != currentScore) return candidateScore > currentScore
        return candidate.second.rank < current.second.rank
    }

    private fun tokenScore(remote: List<String>, candidate: List<String>): Int {
        if (remote.size < 2 || candidate.size < 2) return 0
        val remoteSet = remote.toSet()
        val candidateSet = candidate.toSet()
        val common = remoteSet.intersect(candidateSet)
        val minSize = minOf(remoteSet.size, candidateSet.size)
        val maxSize = maxOf(remoteSet.size, candidateSet.size)
        val ratio = common.size.toDouble() / minSize
        if (common.size < 2 || ratio < 0.7) return 0
        val extra = maxSize - common.size
        if (extra > maxOf(2, minSize / 2)) return 0
        return (720 + common.size * 12 + (ratio * 30).toInt() - extra * 5)
            .coerceIn(700, 880)
    }

    private fun strengthBonus(remoteId: String, entry: EntryDto): Int {
        val remote = remoteId.lowercase()
        val display = entry.displayName.lowercase()
        val slug = entry.slug.lowercase()
        var bonus = 0
        listOf(
            "xhigh", "high", "medium", "low", "max", "non-reasoning", "thinking",
        ).forEach { strength ->
            val remoteHas = remote.contains("-$strength") || remote.endsWith(strength)
            if (remoteHas && (display.contains("($strength") || slug.contains("-$strength"))) {
                bonus += 35
            }
        }
        if (remote.contains("fallback") && display.contains("with fallback")) bonus += 10
        return bonus
    }

private fun normalize(value: String): String = value.trim().lowercase()
    .replace(Regex("(\\d)\\.(\\d)"), "$1$2")
    .replace(Regex("-?\\d{4}-\\d{2}-\\d{2}$"), "")
        .replace(Regex("-?\\d{8}$"), "")
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')

    private fun tokens(value: String): List<String> =
        value.split('-').filter { it.isNotBlank() && it !in GENERIC_TOKENS }

    private fun stripVendorPrefix(value: String): String {
        val prefix = VENDOR_PREFIXES
            .filter { value == it || value.startsWith("$it-") }
            .maxByOrNull { it.length }
        return prefix?.let { value.removePrefix(it).trim('-') } ?: value
    }

    private fun guessVendor(model: RelayRemoteModel): String? {
        val text = listOf(model.id, model.ownedBy.orEmpty()).joinToString(" ").lowercase()
        return when {
            text.contains("anthropic") -> "Anthropic"
            text.contains("openai") -> "OpenAI"
            text.contains("google") -> "Google"
            text.contains("xai") -> "xAI"
            text.contains("deepseek") -> "DeepSeek"
            text.contains("qwen") || text.contains("alibaba") -> "阿里百炼"
            text.contains("zhipu") || text.contains("glm") -> "智谱AI"
            text.contains("kimi") || text.contains("moonshot") -> "Moonshot/Kimi"
            text.contains("minimax") -> "MiniMax"
            text.contains("mistral") -> "Mistral AI"
            text.contains("meta") -> "Meta"
            text.contains("xiaomi") -> "小米"
            text.contains("doubao") || text.contains("bytedance") -> "火山引擎/豆包"
            else -> null
        }
    }

    private val GENERIC_TOKENS = setOf("api", "latest", "model", "models")
    private val VENDOR_PREFIXES = setOf(
        "openai", "anthropic", "google", "xai", "deepseek", "alibaba", "qwen",
        "deepseek-ai", "zhipu", "moonshot", "meta", "mistral", "minimax", "xiaomi", "bytedance",
        "tencent", "baidu", "microsoft", "amazon", "nvidia", "stepfun", "cohere",
    )
}
