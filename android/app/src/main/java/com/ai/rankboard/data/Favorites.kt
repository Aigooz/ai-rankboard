package com.ai.rankboard.data

/**
 * 把 Room 中已持久化的收藏与"尚未写回数据库"的本地切换合并成 UI 应展示的列表，
 * 避免连续点击收藏按钮时读到滞后数据导致同方向执行两次。
 */
fun mergeFavoriteSlugs(
    base: Collection<String>,
    pending: Map<String, Boolean>,
): List<String> {
    val removed = pending.filterValues { !it }.keys
    val added = pending.filterValues { it }.keys
    val kept = base.toSet() - removed
    return (kept + added).toList()
}
