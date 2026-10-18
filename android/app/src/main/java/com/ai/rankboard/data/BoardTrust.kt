package com.ai.rankboard.data

import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

const val CROSS_BOARD_WARNING = "不同榜单的指标、尺度、评测版本与样本不同，原始分数不能跨榜直接比较；色条/雷达仅表示各自榜内相对位置，不是统一能力分。"

fun sourceHttpUrl(value: String?): String? = value?.trim()?.takeIf {
    runCatching {
        val uri = URI(it)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

internal fun trustInstant(value: String?): Instant? = value?.takeIf { it.isNotBlank() }?.let {
    runCatching { Instant.parse(it) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
}

/** Never substitute snapshot generation or refresh time for collection time. */
fun collectionAgeMessage(value: String?, now: Instant = Instant.now()): String {
    val fetched = trustInstant(value) ?: return "抓取时间缺失或无效，无法判断数据新鲜度"
    val age = Duration.between(fetched, now)
    return when {
        age.isNegative -> "抓取时间晚于当前设备时间，请核对时钟"
        age > Duration.ofHours(48) -> "抓取已超过 48 小时（本地提示阈值），数据可能过期"
        else -> "48 小时内曾抓取成功；不代表上游评测刚刚更新"
    }
}

fun metricExplanation(scoreType: String?): String = when (scoreType?.lowercase()) {
    "elo", "arena_elo" -> "Elo 为对战相对评分，受参赛模型、投票与统计区间影响，不是正确率。"
    "livebench" -> "LiveBench 为指定评测版本的任务成绩；不同版本的题目与评测条件可能不同，不直接跨版本比较。"
    "swe_bench", "swebench" -> "SWE-bench 为软件工程任务解决率；数据子集、代理框架与运行条件影响结果，不等同于模型通用能力。"
    "aa_index" -> "能力指数采用来源方的综合计算口径，不是百分制正确率；具体组成请查看原始来源。"
    else -> "指标类型：${scoreType?.takeIf { it.isNotBlank() } ?: "未提供"}。定义与计算口径请以原始来源说明为准。"
}

fun boardHealthLines(board: BoardDto, now: Instant = Instant.now()): List<String> = buildList {
    val success = board.health?.lastSuccessAt?.takeIf { it.isNotBlank() } ?: board.lastSuccessAt
    add("榜单最近成功抓取：${success?.takeIf { it.isNotBlank() } ?: "未提供"}")
    add(collectionAgeMessage(success, now))
    val health = board.health
    if (health?.stale == true) add("来源快照标记抓取缓存过期（阈值 ${health.staleAfterHours ?: 48} 小时）；非上游发布日期判断")
    add(when (health?.latestAttemptStatus) {
        "ok" -> "最近抓取尝试成功（不等同于上游发布新成绩）"
        "error" -> "最近抓取失败；如有历史数据则保留展示；${health.error?.takeIf { it.isNotBlank() } ?: "未提供失败详情"}"
        else -> "最近抓取结果：未提供（旧快照不据此推断成功）"
    })
    health?.latestAttemptAt?.takeIf { it.isNotBlank() }?.let { add("最近尝试：$it") }
    health?.anomalies.orEmpty().mapNotNull { it.message?.takeIf(String::isNotBlank) }.forEach { add("数据提示：$it") }
}

/** Only compare common models from two real collections of the same known evaluation version. */
fun historyChangeLines(history: List<BoardHistoryDto>): List<String> {
    val ordered = history.mapNotNull { point -> trustInstant(point.fetchedAt)?.let { it to point } }
        .sortedBy { it.first }.distinctBy { it.first }
    if (ordered.size < 2) return emptyList()
    val previous = ordered[ordered.lastIndex - 1].second
    val latest = ordered.last().second
    if (latest.evaluationVersion.isNullOrBlank() || latest.evaluationVersion != previous.evaluationVersion) {
        return listOf("已有多次抓取记录，但评测版本未知或已切换，不计算分数/名次变化。")
    }
    val old = previous.entries.orEmpty().associateBy { it.slug }
    if (latest.entries.orEmpty().none { old.containsKey(it.slug) }) {
        return listOf("两次历史记录没有共同模型样本，不计算变化。")
    }
    var comparableCount = 0
    val changes = latest.entries.orEmpty().mapNotNull { entry ->
        val before = old[entry.slug] ?: return@mapNotNull null
        val rank = if ((entry.rank ?: 0) > 0 && (before.rank ?: 0) > 0) before.rank!! - entry.rank!! else null
        val delta = entry.score?.let { score -> before.score?.let { score - it } }?.takeIf { it.isFinite() }
        if (rank != null || delta != null) comparableCount++
        if ((rank == null || rank == 0) && (delta == null || delta == 0.0)) return@mapNotNull null
        val pieces = buildList {
            rank?.takeIf { it != 0 }?.let { add("名次${if (it > 0) "上升" else "下降"} ${kotlin.math.abs(it)}") }
            delta?.takeIf { it != 0.0 }?.let { add("分数变化 ${String.format(java.util.Locale.US, "%+.2f", it)}") }
        }
        "${entry.displayName ?: entry.slug}：${pieces.joinToString("；")}"
    }.take(5)
    if (comparableCount == 0) return listOf("共同样本缺少可比较的有效分数/名次，不计算变化。")
    if (changes.isEmpty()) return listOf("最近两次有记录抓取中，共同样本的名次/分数未观察到变化。")
    return listOf("观测区间：${previous.fetchedAt} → ${latest.fetchedAt}") + changes +
        "仅对两次记录的共同样本作比较，可能为截断样本；不是上游发布日期，也不能据此判断异常原因。"
}
