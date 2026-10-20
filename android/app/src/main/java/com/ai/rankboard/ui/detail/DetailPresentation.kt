package com.ai.rankboard.ui.detail

import com.ai.rankboard.data.ModelDetailDto
import com.ai.rankboard.data.ScoreDto
import java.util.Locale

internal fun detailRankLabel(rank: Int): String = if (rank > 0) "#$rank（仅本榜）" else "名次未获取"

internal fun representativePricedScore(scores: List<ScoreDto>): ScoreDto? = scores
    .filter { it.priceIn != null || it.priceOut != null }
    .sortedWith(compareBy<ScoreDto> { it.boardSlug != "overall" }.thenBy { it.boardSlug }).firstOrNull()

internal fun currencyLabel(currency: String?): String = when (currency?.trim()?.uppercase(Locale.ROOT)) {
    "USD" -> "USD"
    "CNY", "RMB" -> "CNY"
    else -> currency?.trim()?.takeIf { it.isNotBlank() }?.let { "$it（未换汇）" } ?: "币种未提供"
}

internal fun formatModelPrice(score: ScoreDto): String {
    fun price(value: Double?): String = value?.takeIf { it.isFinite() && it >= 0 }
        ?.let { String.format(Locale.US, "%.4f", it) } ?: "未获取"
    return "输入 ${price(score.priceIn)} / 输出 ${price(score.priceOut)} ${currencyLabel(score.currency)} /百万tokens"
}

internal fun estimatePrice(score: ScoreDto, inputText: String, outputText: String): String {
    val input = inputText.trim().toDoubleOrNull()
    val output = outputText.trim().toDoubleOrNull()
    if (input == null || output == null || !input.isFinite() || !output.isFinite() || input < 0 || output < 0) {
        return "请输入非负有限数字，单位为百万 tokens"
    }
    fun valid(price: Double?) = price != null && price.isFinite() && price >= 0
    if (!valid(score.priceIn) || !valid(score.priceOut)) return "价格未获取完整，无法估算总价（缺失不按0计算）"
    if (score.currency.isNullOrBlank()) return "币种未提供，无法估算总价"
    val cost = input * score.priceIn!! + output * score.priceOut!!
    if (!cost.isFinite()) return "计算结果超出范围，请减小用量"
    return "约 ${String.format(Locale.US, "%.4f", cost)} ${currencyLabel(score.currency)}（仅按此来源单价估算）"
}

/** Stable reference, not the numerically largest result across unrelated benchmarks. */
internal fun primaryDetailScore(scores: List<ScoreDto>): ScoreDto? =
    scores.firstOrNull { it.boardSlug == "overall" && it.score?.isFinite() == true }
        ?: scores.firstOrNull { it.score?.isFinite() == true }

internal fun detailSpecifications(model: ModelDetailDto): List<Pair<String, String?>> = listOf(
    "上下文" to model.contextWindow?.takeIf { it.isNotBlank() },
    "参数" to model.paramsB?.takeIf { it.isFinite() && it > 0 }?.let { "$it B" },
    "许可" to model.license?.takeIf { it.isNotBlank() },
    "发布日期" to model.releaseDate?.takeIf { it.isNotBlank() },
)
