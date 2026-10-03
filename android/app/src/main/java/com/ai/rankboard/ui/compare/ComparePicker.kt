package com.ai.rankboard.ui.compare

import com.ai.rankboard.data.ModelDetailDto
import java.text.Collator
import java.util.Locale

enum class PickerTab(val label: String) {
    Favorites("收藏"),
    Vendors("厂商"),
}

data class VendorGroup(
    val vendor: String,
    val models: List<ModelDetailDto>,
)

object ModelPickerGroups {
    private val knownVendors = listOf(
        "OpenAI",
        "Anthropic",
        "Google",
        "xAI",
        "DeepSeek",
        "阿里百炼",
        "智谱AI",
        "Moonshot/Kimi",
        "Meta",
        "Microsoft",
        "Mistral AI",
        "MiniMax",
        "小米",
        "火山引擎/豆包",
        "百度千帆",
    )
    private val collator = Collator.getInstance(Locale.CHINA)

    fun group(models: List<ModelDetailDto>): List<VendorGroup> {
        return models
            .groupBy { groupVendor(it.vendor) }
            .map { (vendor, groupModels) ->
                VendorGroup(
                    vendor = vendor,
                    models = groupModels.sortedWith(
                        compareBy({ it.displayName.length }, { collator.getCollationKey(it.displayName) }),
                    ),
                )
            }
            .sortedWith(
                compareBy<VendorGroup> { knownVendorRank(it.vendor) }
                    .thenBy { it.vendor == "其他" }
                    .thenBy { collator.getCollationKey(it.vendor) },
            )
    }

    private fun knownVendorRank(vendor: String): Int {
        val index = knownVendors.indexOfFirst { known -> sameVendor(vendor, known) }
        return if (index >= 0) index else knownVendors.size
    }

    fun matches(model: ModelDetailDto, query: String): Boolean {
        val keyword = query.trim()
        if (keyword.isBlank()) return true
        return model.displayName.contains(keyword, ignoreCase = true) ||
            model.vendor?.contains(keyword, ignoreCase = true) == true ||
            model.slug.contains(keyword, ignoreCase = true)
    }

    private fun groupVendor(rawVendor: String?): String {
        val vendor = rawVendor?.trim().orEmpty()
        return if (vendor.isBlank() || vendor == "-") "其他" else vendor
    }

    private fun sameVendor(left: String, right: String): Boolean =
        left.equals(right, ignoreCase = true) || left.contains(right, ignoreCase = true)
}
