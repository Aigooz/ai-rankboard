package com.ai.rankboard.data

import java.util.Locale

/** 一个可用的快照下载入口；镜像只影响速度，内容仍由 SHA-256 校验。 */
data class SnapshotSource(
    val url: String,
    val label: String,
    val host: String = hostOf(url),
)

internal fun hostOf(url: String): String = runCatching {
    java.net.URI(url).host.orEmpty().lowercase(Locale.ROOT)
}.getOrDefault("")

/**
 * 快照的校验文件地址：把文件名后缀替换为 `.sha256`，保留查询参数与锚点。
 * 例：`.../leaderboards.json?ref=main` -> `.../leaderboards.json.sha256?ref=main`
 */
internal fun snapshotHashUrl(url: String): String {
    val marker = url.indexOf('#')
    val fragment = if (marker >= 0) url.substring(marker) else ""
    val withoutFragment = if (marker >= 0) url.substring(0, marker) else url
    val queryMarker = withoutFragment.indexOf('?')
    val query = if (queryMarker >= 0) withoutFragment.substring(queryMarker) else ""
    val core = if (queryMarker >= 0) withoutFragment.substring(0, queryMarker) else withoutFragment
    return "$core.sha256$query$fragment"
}

private const val RAW_PREFIX = "https://raw.githubusercontent.com/"
private const val API_PREFIX = "https://api.github.com/repos/"

/**
 * 把配置的地址展开成按优先级排列的下载入口。
 *
 * 官方地址在国内网络下经常超时，因此额外给出同内容的镜像；所有入口最终都要通过
 * SHA-256 校验，镜像只用于提速和容错。自定义地址不扩展镜像，避免把数据发到非预期主机。
 */
internal fun snapshotSourceCandidates(configuredUrl: String, preferredHost: String? = null): List<SnapshotSource> {
    val url = configuredUrl.trim()
    if (url.isBlank()) return emptyList()
    val canonical = canonicalRawUrl(url)
    val sources = if (canonical == null) {
        listOf(SnapshotSource(url, "自定义数据源"))
    } else {
        buildList {
            add(SnapshotSource(canonical, "GitHub 原始地址"))
            add(SnapshotSource("https://gh-proxy.com/$canonical", "镜像 gh-proxy"))
            add(SnapshotSource("https://ghfast.top/$canonical", "镜像 ghfast"))
            jsDelivrUrl(canonical)?.let { add(SnapshotSource(it, "镜像 jsDelivr")) }
            // API 有每小时 60 次的匿名限流，只作为最后兜底。
            if (url.startsWith(API_PREFIX, ignoreCase = true)) add(SnapshotSource(url, "GitHub API"))
        }
    }
    val preferred = preferredHost?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (preferred.isBlank()) return sources
    val hit = sources.filter { it.host == preferred }
    return hit + sources.filter { it.host != preferred }
}

/** GitHub 的 contents API 与 raw 地址都归一到 raw；其他主机返回 null。 */
internal fun canonicalRawUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.startsWith(RAW_PREFIX, ignoreCase = true)) return trimmed
    if (!trimmed.startsWith(API_PREFIX, ignoreCase = true)) return null
    val rest = trimmed.substring(API_PREFIX.length)
    val query = rest.substringAfter('?', "")
    val path = rest.substringBefore('?').removeSuffix("/")
    val parts = path.split('/')
    if (parts.size < 4) return null
    val owner = parts[0]
    val repo = parts[1]
    if (parts[2] != "contents") return null
    val file = parts.drop(3).joinToString("/")
    val ref = query.split('&')
        .firstOrNull { it.startsWith("ref=") }
        ?.removePrefix("ref=")
        ?.takeIf { it.isNotBlank() }
        ?: "main"
    return "$RAW_PREFIX$owner/$repo/$ref/$file"
}

private fun jsDelivrUrl(rawUrl: String): String? {
    val rest = rawUrl.removePrefix(RAW_PREFIX)
    val parts = rest.split('/')
    if (parts.size < 4) return null
    val owner = parts[0]
    val repo = parts[1]
    val ref = parts[2]
    val file = parts.drop(3).joinToString("/")
    return "https://cdn.jsdelivr.net/gh/$owner/$repo@$ref/$file"
}

/** 远端校验值与本地一致时无需下载整份快照。 */
internal fun shouldSkipSnapshotDownload(remoteHash: String?, currentHash: String?): Boolean {
    val remote = remoteHash?.trim().orEmpty()
    val current = currentHash?.trim().orEmpty()
    if (!isSha256(remote) || !isSha256(current)) return false
    return remote.equals(current, ignoreCase = true)
}

internal fun isSha256(value: String): Boolean = value.matches(Regex("[0-9a-fA-F]{64}"))

/** Add a changing query parameter so a CDN cannot reuse an old checksum or snapshot response. */
internal fun withCacheBust(url: String, token: Long): String {
    val fragmentIndex = url.indexOf('#')
    val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""
    val withoutFragment = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
    val separator = if (withoutFragment.contains('?')) '&' else '?'
    return "$withoutFragment${separator}sync=$token$fragment"
}

enum class SnapshotSyncPhase { CONNECTING, DOWNLOADING, PARSING }

data class SnapshotSyncProgress(
    val phase: SnapshotSyncPhase,
    val sourceLabel: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
)
