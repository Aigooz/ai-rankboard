package com.ai.rankboard.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.BenchmarkReferenceDto
import com.ai.rankboard.data.BoardDto
import com.ai.rankboard.data.sourceHttpUrl

fun isHleBoard(board: BoardDto?): Boolean = board?.sourceId == "hle" || board?.scoreType in setOf("hle", "hle_diamond")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HleReferenceEntry(board: BoardDto?, reference: BenchmarkReferenceDto?) {
    if (!isHleBoard(board) || reference == null) return
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    TextButton(onClick = { open = true }) { Text("测评说明与公开示例 ⓘ", style = MaterialTheme.typography.labelSmall) }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SelectionContainer {
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text(reference.name ?: "HLE参考说明", style = MaterialTheme.typography.titleLarge) }
                item { Text(board?.name.orEmpty(), style = MaterialTheme.typography.titleSmall) }
                item { Text("题集版本：${board?.evaluation?.version ?: board?.release ?: "未提供"}。这是评测题集版本，不是模型发布日期；官网历史公开结果不代表实时榜单。", style = MaterialTheme.typography.bodyMedium) }
                reference.description?.let { text -> item { Text(text) } }
                item { Text("原版HLE与HLE-Diamond题集不同，分数不能直接混比；本页不提供完整题库或答案。") }
                reference.publisher?.let { text -> item { Text("发布方：$text") } }
                reference.licenseNotice?.let { text -> item { Text(text) } }
                reference.trainingWarning?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error) } }
                reference.canary?.let { text -> item { Text("Canary：$text", style = MaterialTheme.typography.bodySmall) } }
                listOf("官网出处" to reference.sourceUrl, "数据集访问与条款" to reference.datasetUrl, "论文" to reference.paperUrl).forEach { (label, raw) ->
                    sourceHttpUrl(raw)?.let { url -> item { TextButton(onClick = { openUrl(context, url) }) { Text("$label ↗") } } }
                }
                // Deliberately expose at most the sole public text example, never answers or a question bank.
                reference.examples.orEmpty().filter { it.modality == "text" && !it.question.isNullOrBlank() }.take(1).forEach { example ->
                    item { Text("官网公开示例 · ${example.category.orEmpty()}（无答案）", style = MaterialTheme.typography.titleMedium) }
                    item { Text(example.question.orEmpty(), style = MaterialTheme.typography.bodyLarge) }
                    example.author?.let { author -> item { Text("作者：$author", style = MaterialTheme.typography.bodySmall) } }
                    example.note?.let { note -> item { Text(note, style = MaterialTheme.typography.bodySmall) } }
                    sourceHttpUrl(example.sourceUrl)?.let { url -> item { TextButton(onClick = { openUrl(context, url) }) { Text("示例出处 ↗") } } }
                }
                item { TextButton(onClick = { open = false }) { Text("关闭") } }
            }
        }
    }
}
