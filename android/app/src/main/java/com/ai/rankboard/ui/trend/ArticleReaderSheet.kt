package com.ai.rankboard.ui.trend

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.NewsArticleDto
import com.ai.rankboard.ui.common.openUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Native text from the verified snapshot; never embeds a browser or interprets HTML. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderSheet(article: NewsArticleDto, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val url = remember(article.url) { articleReaderUrl(article.url) }
    val paragraphs = remember(article.content) { articleParagraphs(article.content) }
    val scope = rememberCoroutineScope()
    var translations by remember(article.content) { mutableStateOf<List<String>>(emptyList()) }
    var translating by remember(article.content) { mutableStateOf(false) }
    var completed by remember(article.content) { mutableStateOf(0) }
    var translationError by remember(article.content) { mutableStateOf<String?>(null) }
    var showTranslation by remember(article.content) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("资讯阅读", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            if (paragraphs.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !translating, onClick = {
                            if (translations.size == paragraphs.size) showTranslation = !showTranslation
                            else scope.launch {
                                translating = true; translationError = null; completed = 0
                                try {
                                    val result = mutableListOf<String>()
                                    for (paragraph in paragraphs) {
                                        result += ArticleTranslation.translate(paragraph)
                                        completed = result.size
                                    }
                                    translations = result; showTranslation = true
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) { translationError = error.message ?: "翻译失败，请重试" }
                                finally { translating = false }
                            }
                        }) { Text(when {
                            translating -> "翻译中 $completed/${paragraphs.size}"
                            translations.size == paragraphs.size -> if (showTranslation) "查看原文" else "查看中文译文"
                            translationError != null -> "重试翻译"
                            else -> "翻译为中文"
                        }) }
                    }
                    Text("MyMemory 机器翻译，仅供参考；点击后将正文发送至第三方服务。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    translationError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (translating) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    item { Text(article.title, style = MaterialTheme.typography.headlineSmall) }
                    item {
                        Text("Artificial Analysis" + (article.publishedAt?.takeIf { it.isNotBlank() }?.let { " · ${it.take(10)}" } ?: " · 日期未提供"),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (paragraphs.isEmpty()) {
                        item { Text("正文未获取，打开原文可查看完整内容。", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(if (showTranslation && translations.size == paragraphs.size) translations else paragraphs) { paragraph ->
                            Text(paragraph, style = MaterialTheme.typography.bodyLarge)
                        }
                        item { Text("快照收录的纯文本正文，图表、链接及排版请参阅原文；正文可能有截断。",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    item {
                        if (url != null) TextButton(onClick = { openUrl(context, url) }) { Text("打开原文 ↗") }
                        else Text("原文链接不可用", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

fun articleParagraphs(content: String?): List<String> = content.orEmpty().trim()
    .split(Regex("\\r?\\n[\\t ]*\\r?\\n(?:[\\t ]*\\r?\\n)*"))
    .map { it.trim() }.filter { it.isNotBlank() }
