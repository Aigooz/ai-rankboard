package com.ai.rankboard.data

import java.util.Locale

enum class ModelRegion(val label: String) {
    ALL("全部"),
    DOMESTIC("国内"),
    INTERNATIONAL("国外");

    fun matches(vendor: String?): Boolean = this == ALL || this == modelRegion(vendor)
}

// Classify the developer, not the API host or model language. Unknown vendors stay unclassified.
fun modelRegion(vendor: String?): ModelRegion? {
    val key = vendor?.trim()?.lowercase(Locale.ROOT)?.replace(Regex("[\\s._/\\-]+"), "")
        ?: return null
    return when (key) {
        "alibaba", "alibabaqwen", "alibabaath", "qwen", "阿里", "阿里巴巴", "阿里百炼", "通义千问",
        "deepseek", "深度求索",
        "zai", "zhipu", "zhipuai", "智谱", "智谱ai", "智谱清言",
        "bytedance", "字节跳动", "火山引擎", "豆包", "火山引擎豆包",
        "tencent", "腾讯", "腾讯混元",
        "minimax", "稀宇科技",
        "moonshot", "moonshotai", "moonshotkimi", "kimi", "月之暗面",
        "stepfun", "阶跃星辰",
        "xiaomi", "小米",
        "baidu", "百度", "百度千帆",
        "klingai", "kling", "可灵", "快手", "kuaishou",
        "meituan", "美团", "antgroup", "蚂蚁集团", "蚂蚁", "hidream", "智象未来",
        "01ai", "零一万物", "sensenova", "sensetime", "商汤", "modelbest", "面壁智能",
        "internlm", "internvl", "上海人工智能实验室", "shanghaiailaboratory",
        "huawei", "华为", "iflytek", "科大讯飞" -> ModelRegion.DOMESTIC

        "openai", "anthropic", "google", "googledeepmind", "deepmind",
        "xai", "spacexai", "mistral", "mistralai", "meta", "metaai", "nvidia",
        "microsoft", "microsoftai", "amazon", "amazonwebservices", "aws",
        "ai2", "allenai", "ibm", "cohere", "blackforestlabs",
        "perplexity", "perplexityai", "upstage", "recraft", "luma", "lumaai",
        "krea", "ideogram", "reve", "runway", "poolside", "inception", "inceptionai",
        "kandinsky", "pika", "genmo", "genmoai", "diffbot", "leonardoai",
        "stabilityai", "huggingface", "databricks", "reka", "rekaai" -> ModelRegion.INTERNATIONAL

        else -> null
    }
}
