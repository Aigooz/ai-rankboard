package com.ai.rankboard.ui.select

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.rankboard.data.isLikelyOpenSourceLicense
import com.ai.rankboard.ui.common.AppCard
import com.ai.rankboard.ui.common.VendorIcon
import com.ai.rankboard.ui.common.scoreColor
import com.ai.rankboard.ui.compare.parseModelStrength
import java.util.Locale

internal enum class ModelTableSort(val label: String) {
    INTELLIGENCE("综合智能"),
    SPEED("响应速度"),
    PRICE("价格最低"),
    RELEASE("最新发布"),
}

/** 借鉴 Artificial Analysis 模型页的全模型总表：可按能力/速度/价格/时间排序，可筛开源闭源。 */
internal fun filterAndSortModels(
    models: List<ScatterModel>,
    sort: ModelTableSort,
    openness: Int,
): List<ScatterModel> = models
    .filter { model ->
        when (openness) {
            1 -> isLikelyOpenSourceLicense(model.license)
            2 -> !isLikelyOpenSourceLicense(model.license)
            else -> true
        }
    }
    .sortedWith(
        when (sort) {
            ModelTableSort.INTELLIGENCE ->
                compareByDescending<ScatterModel> { it.scores["overall"] }
                    .thenByDescending { it.scores["coding"] }
            ModelTableSort.SPEED ->
                compareByDescending<ScatterModel> { it.scores["speed"] }
            ModelTableSort.PRICE ->
                compareBy<ScatterModel> { it.priceCny ?: Double.MAX_VALUE }
            ModelTableSort.RELEASE ->
                compareByDescending<ScatterModel> { it.releaseDate.orEmpty() }
        }
            .thenByDescending { it.scores["overall"] },
    )

@Composable
internal fun ModelTableControls(
    sort: ModelTableSort,
    openness: Int,
    count: Int,
    onSort: (ModelTableSort) -> Unit,
    onOpenness: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ModelTableSort.entries, key = { it.name }) { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { onSort(option) },
                    label = { Text(option.label, maxLines = 1) },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(0 to "全部", 1 to "开源", 2 to "闭源").forEach { (value, label) ->
                FilterChip(
                    selected = openness == value,
                    onClick = { onOpenness(value) },
                    label = { Text(label, maxLines = 1) },
                )
            }
            Text(
                text = "$count 个模型",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
internal fun ModelTableRow(
    model: ScatterModel,
    onOpenModel: (String) -> Unit,
) {
    val strength = parseModelStrength(model.displayName)
    val overall = model.scores["overall"]
    val speed = model.scores["speed"]
    val openSource = isLikelyOpenSourceLicense(model.license)
    AppCard(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenModel(model.slug) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VendorIcon(vendor = model.vendor, size = 22.dp)
                Text(
                    text = strength.first,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                strength.second?.let { tier ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = tier,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = overall?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = scoreColor(overall, 0.0, 100.0),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = listOfNotNull(
                        model.vendor,
                        model.releaseDate?.take(10),
                        model.contextWindow?.let { ctx -> "上下文 $ctx" },
                        if (openSource) "开源" else "闭源",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTablePrice(model),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = speed?.let { String.format(Locale.US, "%.0f", it) } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(34.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private fun formatTablePrice(model: ScatterModel): String {
    val price = model.priceCny ?: return "—"
    return when {
        price <= 0.0 -> "免费"
        price < 1.0 -> "¥%.2f/M".format(Locale.US, price)
        price < 100.0 -> "¥%.1f/M".format(Locale.US, price)
        else -> "¥%.0f/M".format(Locale.US, price)
    }
}
