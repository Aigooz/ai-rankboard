import SwiftUI

func scoreColor(_ score: Double?, minScore: Double?, maxScore: Double?) -> Color {
    guard let score, !score.isNaN, let minScore, let maxScore, maxScore > minScore else {
        return .secondary
    }
    let ratio = ((score - minScore) / (maxScore - minScore)).clamped01()
    if ratio < 0.5 {
        return Color(red: 0.94, green: 0.27, blue: 0.27)
            .interpolate(to: Color(red: 0.96, green: 0.62, blue: 0.04), amount: ratio * 2)
    }
    return Color(red: 0.96, green: 0.62, blue: 0.04)
        .interpolate(to: Color(red: 0.13, green: 0.77, blue: 0.37), amount: (ratio - 0.5) * 2)
}

func scoreRatio(_ score: Double?, minScore: Double?, maxScore: Double?) -> CGFloat {
    guard let score, let minScore, let maxScore, maxScore > minScore else { return 0 }
    return CGFloat(((score - minScore) / (maxScore - minScore)).clamped01())
}

extension Double {
    func clamped01() -> Double {
        min(max(self, 0), 1)
    }
}

extension Color {
    func interpolate(to other: Color, amount: Double) -> Color {
        let from = UIColor(self)
        let destination = UIColor(other)
        var red1: CGFloat = 0
        var green1: CGFloat = 0
        var blue1: CGFloat = 0
        var alpha1: CGFloat = 0
        var red2: CGFloat = 0
        var green2: CGFloat = 0
        var blue2: CGFloat = 0
        var alpha2: CGFloat = 0
        from.getRed(&red1, green: &green1, blue: &blue1, alpha: &alpha1)
        destination.getRed(&red2, green: &green2, blue: &blue2, alpha: &alpha2)
        let clampedAmount = CGFloat(min(max(amount, 0), 1))
        return Color(
            red: Double(red1 + (red2 - red1) * clampedAmount),
            green: Double(green1 + (green2 - green1) * clampedAmount),
            blue: Double(blue1 + (blue2 - blue1) * clampedAmount),
            opacity: Double(alpha1 + (alpha2 - alpha1) * clampedAmount)
        )
    }
}

struct VendorIcon: View {
    let vendor: String?
    var size: CGFloat = 32

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.25, style: .continuous)
            .fill(spec.color)
            .frame(width: size, height: size)
            .overlay {
                Text(spec.label)
                    .font(.system(size: size * 0.34, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.4)
                    .padding(size * 0.12)
            }
    }

    private var spec: (color: Color, label: String) {
        let raw = vendor?.trimmingCharacters(in: .whitespaces) ?? ""
        let key = raw.lowercased()
        switch true {
        case key.isEmpty:
            return (Color(hex: 0x475569), "AI")
        case key.hasPrefix("openai"):
            return (Color(hex: 0x101010), "AI")
        case key.hasPrefix("anthropic"):
            return (Color(hex: 0xcc785c), "AI")
        case key.hasPrefix("google"):
            return (Color(hex: 0x4285f4), "G")
        case key.contains("qwen") || key.contains("alibaba") || key.contains("阿里"):
            return (Color(hex: 0xff6a00), "Q")
        case key.hasPrefix("deepseek"):
            return (Color(hex: 0x4d6bfe), "DS")
        case key == "xai" || key.contains("grok"):
            return (Color(hex: 0x171717), "X")
        case key.hasPrefix("meta"):
            return (Color(hex: 0x0866ff), "M")
        case key.contains("moonshot") || key.contains("kimi"):
            return (Color(hex: 0x1b1b1f), "K")
        case key.contains("zhipu") || key.contains("z.ai"):
            return (Color(hex: 0x2a6af5), "Z")
        case key.hasPrefix("mistral"):
            return (Color(hex: 0xff7000), "M")
        case key.hasPrefix("minimax"):
            return (Color(hex: 0xf23f5d), "MM")
        case key.hasPrefix("xiaomi"):
            return (Color(hex: 0xff6900), "MI")
        case key.contains("tencent") || key.contains("腾讯"):
            return (Color(hex: 0x0052d9), "T")
        case key.hasPrefix("bytedance") || key.contains("豆包"):
            return (Color(hex: 0x325ab4), "B")
        case key.hasPrefix("nvidia"):
            return (Color(hex: 0x76b900), "N")
        case key.hasPrefix("amazon"):
            return (Color(hex: 0xff9900), "A")
        case key.hasPrefix("microsoft"):
            return (Color(hex: 0x0078d4), "MS")
        case key.hasPrefix("baidu"):
            return (Color(hex: 0x2932e1), "B")
        case key.hasPrefix("perplexity"):
            return (Color(hex: 0x20b8cd), "P")
        case key.hasPrefix("cohere"):
            return (Color(hex: 0x39594d), "C")
        default:
            return (Color(hex: 0x5b6472), monogram(raw))
        }
    }

    private func monogram(_ value: String) -> String {
        let asciiLetters = value.prefix { $0.isASCII && $0.isLetter }
        let words = asciiLetters.split(separator: " ")
        if words.count >= 2 {
            return words.prefix(2).map { $0.prefix(1).uppercased() }.joined()
        }
        if let first = value.first {
            return first.isASCII ? String(first).uppercased() : String(first)
        }
        return "AI"
    }
}

extension Color {
    init(hex: UInt64, opacity: Double = 1) {
        self.init(
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: opacity
        )
    }
}

struct ScoreBar: View {
    let score: Double?
    let minScore: Double?
    let maxScore: Double?
    var height: CGFloat = 5

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.secondary.opacity(0.18))
                Capsule()
                    .fill(scoreColor(score, minScore: minScore, maxScore: maxScore))
                    .frame(width: proxy.size.width * scoreRatio(score, minScore: minScore, maxScore: maxScore))
            }
        }
        .frame(height: height)
    }
}
