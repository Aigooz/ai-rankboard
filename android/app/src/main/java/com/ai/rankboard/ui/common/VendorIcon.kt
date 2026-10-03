package com.ai.rankboard.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.rankboard.R
import java.util.Locale

private data class VendorSpec(
    val iconRes: Int? = null,
    val background: Color,
    val tint: Color = Color.White,
    val fallback: String = "",
)

private fun vendorSpec(rawVendor: String?): VendorSpec {
    val vendor = rawVendor?.trim().orEmpty()
    val key = vendor.lowercase(Locale.ROOT)
    return when {
        key.startsWith("openai") -> VendorSpec(R.drawable.vendor_openai, Color(0xFF101010))
        key.startsWith("anthropic") -> VendorSpec(R.drawable.vendor_anthropic, Color(0xFFCC785C))
        key.startsWith("google") -> VendorSpec(R.drawable.vendor_google, Color(0xFF4285F4))
        key.contains("阿里百炼") || key.startsWith("alibaba") || key.contains("qwen") ->
            VendorSpec(R.drawable.vendor_alibaba, Color(0xFFFF6A00))
        key.startsWith("deepseek") -> VendorSpec(R.drawable.vendor_deepseek, Color(0xFF4D6BFE))
        key == "xai" || key == "spacexai" -> VendorSpec(R.drawable.vendor_xai, Color(0xFF171717))
        key.startsWith("mistral") -> VendorSpec(R.drawable.vendor_mistral, Color(0xFFFF7000))
        key.contains("moonshot") || key.contains("kimi") ->
            VendorSpec(R.drawable.vendor_moonshot, Color(0xFF1B1B1F))
        key.contains("zhipu") || key.contains("z.ai") ->
            VendorSpec(background = Color(0xFF2A6AF5), fallback = "Z")
        key == "meta" || key.startsWith("meta ") ->
            VendorSpec(R.drawable.vendor_meta, Color(0xFF0866FF))
        key.startsWith("minimax") -> VendorSpec(R.drawable.vendor_minimax, Color(0xFFF23F5D))
        key.startsWith("xiaomi") || key.contains("小米") ->
            VendorSpec(R.drawable.vendor_xiaomi, Color(0xFFFF6900))
        key.contains("tencent") || key.contains("腾讯") ->
            VendorSpec(R.drawable.vendor_tencent, Color(0xFF0052D9))
        key.startsWith("bytedance") || key.contains("火山引擎") || key.contains("豆包") ->
            VendorSpec(R.drawable.vendor_bytedance, Color(0xFF325AB4))
        key.startsWith("nvidia") -> VendorSpec(R.drawable.vendor_nvidia, Color(0xFF76B900))
        key.startsWith("amazon") -> VendorSpec(R.drawable.vendor_amazon, Color(0xFFFF9900))
        key.startsWith("microsoft") -> VendorSpec(R.drawable.vendor_microsoft, Color(0xFF0078D4))
        key.startsWith("baidu") || key.contains("百度千帆") ->
            VendorSpec(R.drawable.vendor_baidu, Color(0xFF2932E1))
        key.startsWith("perplexity") -> VendorSpec(R.drawable.vendor_perplexity, Color(0xFF20B8CD))
        key.startsWith("stepfun") -> VendorSpec(background = Color(0xFF0057FF), fallback = "S")
        key.startsWith("cohere") -> VendorSpec(background = Color(0xFF39594D), fallback = "C")
        key.startsWith("ibm") -> VendorSpec(background = Color(0xFF052FAD), fallback = "IBM")
        key.startsWith("black forest") -> VendorSpec(background = Color(0xFF0F172A), fallback = "B")
        key.startsWith("ai2") -> VendorSpec(background = Color(0xFFF6653C), fallback = "AI2")
        key.startsWith("klingai") || key.contains("可灵") ->
            VendorSpec(background = Color(0xFF121212), fallback = "K")
        key.startsWith("meituan") -> VendorSpec(background = Color(0xFFFFC300), fallback = "M")
        key.startsWith("upstage") -> VendorSpec(background = Color(0xFF6D5DFC), fallback = "U")
        key.startsWith("ideogram") -> VendorSpec(background = Color(0xFF1F62FF), fallback = "I")
        key.startsWith("runway") -> VendorSpec(background = Color(0xFF110F17), fallback = "R")
        key.startsWith("luma") -> VendorSpec(background = Color(0xFF141414), fallback = "L")
        key.startsWith("recraft") -> VendorSpec(background = Color(0xFF4A4AF4), fallback = "R")
        key.startsWith("ant group") -> VendorSpec(background = Color(0xFF1677FF), fallback = "A")
        vendor.isBlank() || vendor == "-" ->
            VendorSpec(background = Color(0xFF475569), fallback = "AI")
        else -> VendorSpec(background = Color(0xFF5B6472), fallback = fallbackText(vendor))
    }
}

private fun fallbackText(vendor: String): String {
    val letters = vendor.takeWhile { it.isLetter() && it.code < 128 }
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase(Locale.ROOT) }
    if (letters.isNotBlank()) return letters
    val first = vendor.firstOrNull() ?: return "AI"
    return if (first.code < 128) first.uppercase(Locale.ROOT) else first.toString()
}

@Composable
fun VendorIcon(
    vendor: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val spec = remember(vendor) { vendorSpec(vendor) }
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = spec.background,
        shadowElevation = 0.dp,
        modifier = modifier.size(size),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (spec.iconRes != null) 6.dp else 2.dp),
        ) {
            val resId = spec.iconRes
            if (resId != null) {
                Icon(
                    painter = painterResource(resId),
                    contentDescription = null,
                    tint = spec.tint,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = spec.fallback,
                    color = spec.tint,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
