package com.ai.rankboard.data

/**
 * 价格分档与"同档最便宜"的统一口径，参照 ModelSage 的比价方式：
 * 混合价 = (3×输入价 + 输出价) / 4，美元按近似汇率折算为人民币。
 */
const val USD_TO_CNY_RATE = 7.2

fun EntryDto.blendedPriceCny(): Double? {
    val rate = if (currency.equals("USD", ignoreCase = true)) USD_TO_CNY_RATE else 1.0
    val input = priceIn?.times(rate)
    val output = priceOut?.times(rate)
    return when {
        input != null && output != null -> (input * 3.0 + output) / 4.0
        input != null -> input
        output != null -> output
        else -> null
    }
}

const val PRICE_FREE = "free"
const val PRICE_LT1 = "lt1"
const val PRICE_1TO5 = "1to5"
const val PRICE_5TO10 = "5to10"
const val PRICE_10PLUS = "10plus"

fun priceTierLabel(tier: String): String = when (tier) {
    PRICE_FREE -> "免费"
    PRICE_LT1 -> "<¥1"
    PRICE_1TO5 -> "¥1–5"
    PRICE_5TO10 -> "¥5–10"
    PRICE_10PLUS -> "≥¥10"
    else -> tier
}

fun priceTierOf(price: Double): String = when {
    price <= 0.0 -> PRICE_FREE
    price < 1.0 -> PRICE_LT1
    price < 5.0 -> PRICE_1TO5
    price < 10.0 -> PRICE_5TO10
    else -> PRICE_10PLUS
}

data class PriceTierPicks(
    val label: String,
    val range: String,
    val models: List<EntryDto>,
    val modelCount: Int = models.size,
    val cheapestPrice: Double? = models.firstNotNullOfOrNull { it.blendedPriceCny() },
    val highestPrice: Double? = models.mapNotNull { it.blendedPriceCny() }.maxOrNull(),
)

private data class ScoreTier(val label: String, val min: Double?, val max: Double?)

private val SCORE_TIERS = listOf(
    ScoreTier("旗舰", 85.0, null),
    ScoreTier("主力", 70.0, 85.0),
    ScoreTier("入门", 50.0, 70.0),
    ScoreTier("轻量", null, 50.0),
)

/** 按能力分档，各档给出混合价最低的几个模型；没有价格或分数的模型不参与。 */
fun cheapestByTier(entries: List<EntryDto>, perTier: Int = 3): List<PriceTierPicks> =
    SCORE_TIERS.mapNotNull { tier ->
        val inTier = entries.filter { entry ->
            val score = entry.score ?: return@filter false
            entry.blendedPriceCny() != null &&
                (tier.min == null || score >= tier.min) &&
                (tier.max == null || score < tier.max)
        }
        if (inTier.isEmpty()) {
            null
        } else {
            val tierPrices = inTier.mapNotNull { it.blendedPriceCny() }
            PriceTierPicks(
                label = tier.label,
                range = when {
                    tier.min != null && tier.max != null ->
                        "${tier.min.toInt()}–${tier.max.toInt()} 分"
                    tier.min != null -> "≥${tier.min.toInt()} 分"
                    else -> "<${tier.max!!.toInt()} 分"
                },
                models = inTier
                    .sortedBy { it.blendedPriceCny() ?: Double.MAX_VALUE }
                    .take(perTier),
                modelCount = inTier.size,
                cheapestPrice = tierPrices.minOrNull(),
                highestPrice = tierPrices.maxOrNull(),
            )
        }
    }

/** 官方 value 榜可直接使用；其他榜单用“能力 / 混合价”作为兜底性价比。 */
data class ValuePicksResult(
    val isSourceBoard: Boolean,
    val models: List<EntryDto>,
)

fun bestValuePicks(
    entries: List<EntryDto>,
    isSourceBoard: Boolean,
    perPage: Int = 6,
): ValuePicksResult {
    val priced = entries.filter { it.blendedPriceCny() != null }
    if (priced.isEmpty()) return ValuePicksResult(isSourceBoard, emptyList())

    val sorted = if (isSourceBoard) {
        priced.sortedWith(
            compareByDescending<EntryDto> { it.score ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.blendedPriceCny() ?: Double.MAX_VALUE }
                .thenBy { it.displayName },
        )
    } else {
        priced.sortedWith(
            compareByDescending<EntryDto> { valueIndexOf(it) }
                .thenByDescending { it.score ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.blendedPriceCny() ?: Double.MAX_VALUE },
        )
    }
    return ValuePicksResult(isSourceBoard, sorted.take(perPage))
}

private fun valueIndexOf(entry: EntryDto): Double {
    val score = entry.score ?: return Double.NEGATIVE_INFINITY
    val price = entry.blendedPriceCny() ?: return Double.NEGATIVE_INFINITY
    return score / maxOf(price, 0.5)
}

data class ScenarioRecommendation(
    val slug: String,
    val total: Int,
    val strengthPick: EntryDto,
    val valuePicks: List<EntryDto>,
) {
    val savingRatio: Double?
        get() {
            val topPrice = strengthPick.blendedPriceCny() ?: return null
            val alternativePrice = valuePicks.firstOrNull()
                ?.blendedPriceCny()
                ?: return null
            return if (alternativePrice > 0.0) topPrice / alternativePrice else null
        }
}

/** 场景榜中选最高能力模型，并从低成本、能力仍接近的模型中给出替代选项。 */
fun scenarioRecommendation(entries: List<EntryDto>, slug: String, perPage: Int = 3): ScenarioRecommendation? {
    val scored = entries.filter { it.score != null }
    val strengthPick = scored.maxByOrNull { it.score ?: Double.NEGATIVE_INFINITY } ?: return null
    val prices = scored.mapNotNull { it.blendedPriceCny() }
    if (prices.isEmpty()) {
        return ScenarioRecommendation(slug, scored.size, strengthPick, emptyList())
    }

    val scoreMin = scored.minOf { it.score ?: Double.NEGATIVE_INFINITY }
    val scoreMax = maxOf(scoreMin + 0.01, scored.maxOf { it.score ?: Double.NEGATIVE_INFINITY })
    // 保留 70% 相对能力，避免为了省钱推荐完全不适用的模型。
    val qualityFloor = scoreMin + (scoreMax - scoreMin) * 0.70
    val topPrice = strengthPick.blendedPriceCny() ?: Double.MAX_VALUE
    val alternatives = scored.asSequence()
        .filter { it.slug != strengthPick.slug }
        .filter { (it.score ?: Double.NEGATIVE_INFINITY) >= qualityFloor }
        .filter { (it.blendedPriceCny() ?: Double.MAX_VALUE) < topPrice }
        .sortedWith(
            compareBy<EntryDto> { it.blendedPriceCny() ?: Double.MAX_VALUE }
                .thenByDescending { valueIndexOf(it) },
        )
        .toList()

    val valuePicks = alternatives.ifEmpty {
        scored.asSequence()
            .filter { it.slug != strengthPick.slug && it.blendedPriceCny() != null }
            .sortedWith(
                compareByDescending<EntryDto> { valueIndexOf(it) }
                    .thenBy { it.blendedPriceCny() ?: Double.MAX_VALUE },
            )
            .toList()
    }
    return ScenarioRecommendation(slug, scored.size, strengthPick, valuePicks.take(perPage))
}

private val OPEN_SOURCE_MARKERS = listOf(
    "open", "apache", "mit", "bsd", "gpl", "lgpl", "agpl", "mpl", "epl",
    "cc-by", "llama", "qwen", "gemma", "falcon", "community", "research",
)

/** 按许可文本粗判是否开源，榜单存储与 UI 筛选共用同一口径。 */
fun isLikelyOpenSourceLicense(license: String?): Boolean {
    val text = license?.lowercase() ?: return false
    return OPEN_SOURCE_MARKERS.any { text.contains(it) }
}

data class PriceDistributionRow(val label: String, val count: Int)

/** 按混合价把模型分桶计数，用于趋势页的价格分布；无价格数据单独一档。 */
fun priceDistribution(entries: List<EntryDto>): List<PriceDistributionRow> {
    val buckets = linkedMapOf(
        PRICE_FREE to 0,
        PRICE_LT1 to 0,
        PRICE_1TO5 to 0,
        PRICE_5TO10 to 0,
        PRICE_10PLUS to 0,
    )
    var unpriced = 0
    entries.forEach { entry ->
        val price = entry.blendedPriceCny()
        if (price == null) {
            unpriced += 1
        } else {
            val tier = priceTierOf(price)
            buckets[tier] = (buckets[tier] ?: 0) + 1
        }
    }
    val rows = buckets.map { (tier, count) -> PriceDistributionRow(priceTierLabel(tier), count) }
        .filter { it.count > 0 }
    return if (unpriced > 0) rows + PriceDistributionRow("未公开价格", unpriced) else rows
}
