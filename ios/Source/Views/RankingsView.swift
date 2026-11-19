import SwiftUI

struct RankingsView: View {
    @ObservedObject var store: SnapshotStore
    @ObservedObject var favorites: FavoritesStore
    var usageOnly = false
    @State private var query = ""
    @State private var dimension = "overall"
    @State private var boardSlug = ""

    private let dimensions = [
        "overall": "综合榜",
        "coding": "代码榜",
        "writing": "写作榜",
        "multimodal": "多模态榜",
        "agent": "智能体榜",
        "search": "搜索榜",
        "speed": "速度榜",
        "value": "性价比榜",
        "math": "数学榜",
        "analysis": "数据分析榜",
    ]

    private var boardsForDimension: [Board] {
        snapshot.boards
            .filter { $0.dimension == (usageOnly ? "usage" : dimension) }
            .sorted {
                if $0.historical != $1.historical { return !$0.historical }
                let nameOrder = $0.name.localizedCaseInsensitiveCompare($1.name)
                return nameOrder == .orderedSame ? $0.slug < $1.slug : nameOrder == .orderedAscending
            }
    }

    private var snapshot: Snapshot { store.snapshot }

    private var activeBoard: Board? {
        boardsForDimension.first { $0.slug == boardSlug } ?? boardsForDimension.first
    }

    private var isUsageBoard: Bool {
        activeBoard?.dimension == "usage"
    }

    private var entries: [Entry] {
        let base = activeBoard.map { snapshot.entries(board: $0.slug) } ?? []
        return base.filter { matchesModelQuery(query, fields: $0.displayName, $0.vendor, $0.slug) }
    }

    private var scoreRange: (min: Double, max: Double)? {
        let scores = entries.compactMap(\.score)
        guard let minScore = scores.min(), let maxScore = scores.max(), maxScore > minScore else { return nil }
        return (minScore, maxScore)
    }

    var body: some View {
        NavigationStack {
            Group {
                if snapshot.boards.isEmpty {
                    ContentUnavailableView(
                        "暂无榜单数据",
                        systemImage: "tray",
                        description: Text(store.message)
                    )
                } else {
                    List {
                        if isUsageBoard {
                            usageProvenance
                            usageHeader
                            ForEach(entries) { entry in
                                usageRow(entry)
                            }
                        } else {
                            overview
                            ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                                row(entry)
                            }
                        }
                        if entries.isEmpty {
                            Text("没有匹配的模型")
                                .foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 28)
                        }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                    .background(Color(.systemGroupedBackground))
                }
            }
            .navigationTitle(usageOnly ? "使用趋势" : "AI 排行榜")
            .toolbarTitleDisplayMode(.inline)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "搜索模型名称")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    if !usageOnly { filterMenu }
                }
            }
            .refreshable {
                await store.refresh()
            }
            .onAppear {
                if boardSlug.isEmpty {
                    boardSlug = boardsForDimension.first?.slug ?? ""
                }
            }
            .onChange(of: dimension) { _, newValue in
                boardSlug = boardsForDimension.first?.slug ?? ""
            }
            .onChange(of: snapshot.boards) { _, _ in
                if boardsForDimension.first(where: { $0.slug == boardSlug }) == nil {
                    boardSlug = boardsForDimension.first?.slug ?? ""
                }
            }
        }
    }

    private var usageProvenance: some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label("OpenRouter 官方周 token 使用趋势", systemImage: "chart.line.uptrend.xyaxis")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    if let rawURL = activeBoard?.url, let url = URL(string: rawURL) {
                        Link(destination: url) {
                            Label("原始榜", systemImage: "arrow.up.right")
                                .font(.caption)
                        }
                    }
                }
                Text("上游更新时间：\(upstreamDateLabel(activeBoard?.evaluation?.upstreamUpdatedAt))。抓取时间不替代上游更新时间。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if let board = activeBoard {
                    cacheInfo(board)
                }
                Text(activeBoard?.metric?.description ?? "仅统计 OpenRouter 聚合网关，不代表模型能力、质量、市场份额或全球调用量。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var usageHeader: some View {
        Section {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("近期使用榜")
                        .font(.headline)
                    Text(activeBoard?.metric?.name ?? "OpenRouter 周 token 使用量")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text("\(entries.count) 条")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func usageRow(_ entry: Entry) -> some View {
        let maxTokens = entries.compactMap(\.score).max() ?? 1
        let ratio = max(0, min((entry.score ?? 0) / max(maxTokens, 0.000_001), 1))
        return NavigationLink {
            ModelDetailView(entry: entry, snapshot: snapshot, favorites: favorites)
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Text("\(entry.rank)")
                        .font(.caption.weight(.bold).monospacedDigit())
                        .foregroundStyle(.tint)
                        .frame(width: 24, alignment: .leading)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(formatName(entry.displayName).0)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(2)
                        Text(entry.vendor?.isEmpty == false ? entry.vendor! : "作者未提供")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 6)
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(entry.score.map { String(format: "%.2f", $0) } ?? "-")
                            .font(.subheadline.weight(.bold).monospacedDigit())
                        Text("万亿 tokens/周")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color.secondary.opacity(0.16))
                        Capsule()
                            .fill(Color.accentColor)
                            .frame(width: proxy.size.width * ratio)
                    }
                }
                .frame(height: 6)

                HStack {
                    Spacer()
                    Text(weeklyChangeLabel(entry.weeklyChange))
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(weeklyChangeColor(entry.weeklyChange))
                }
            }
            .padding(.vertical, 3)
        }
    }

    private var overview: some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(activeBoard?.name ?? "选择榜单")
                        .font(.headline)
                    Text(
                        snapshot.sources.first(where: { $0.id == activeBoard?.sourceId })?.name
                            ?? activeBoard?.sourceId
                            ?? ""
                    )
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Spacer()
                    if !store.message.isEmpty {
                        Text(store.message)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

                HStack(spacing: 10) {
                    topCard(entry: entries.first, rankColor: .yellow)
                    topCard(entry: entries.dropFirst().first, rankColor: Color(hex: 0xc0c0c0))
                    topCard(entry: entries.dropFirst(2).first, rankColor: Color(hex: 0xcd7f32))
                }

                HStack(spacing: 16) {
                    metric("模型", "\(entries.count)")
                    metric("榜单", "\(snapshot.boards.count)")
                    metric("数据源", "\(max(snapshot.sources.count, 1))")
                    metric("更新", String(snapshot.generatedAt.prefix(10)))
                }
                if let board = activeBoard {
                    cacheInfo(board)
                    Text("上游更新：\(board.evaluation?.upstreamUpdatedAt ?? "未提供") · 评测版本：\(board.evaluation?.version ?? "未提供")")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

    private func topCard(entry: Entry?, rankColor: Color) -> some View {
        NavigationLink {
            if let entry {
                ModelDetailView(entry: entry, snapshot: snapshot, favorites: favorites)
            } else {
                EmptyView()
            }
        } label: {
            VStack(spacing: 6) {
                Text(entry.map { "#\($0.rank)" } ?? "-")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(rankColor)
                VendorIcon(vendor: entry?.vendor, size: 26)
                Text(entry.map { formatName($0.displayName).0 } ?? "暂无")
                    .font(.caption2.weight(.semibold))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .frame(minHeight: 28)
                Text(entry?.score.map { String(format: "%.1f", $0) } ?? "-")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(scoreColor(entry?.score, minScore: scoreRange?.min, maxScore: scoreRange?.max))
            }
            .frame(maxWidth: .infinity)
            .padding(8)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(entry == nil)
    }

    private func row(_ entry: Entry) -> some View {
        NavigationLink {
            ModelDetailView(entry: entry, snapshot: snapshot, favorites: favorites)
        } label: {
            HStack(spacing: 10) {
                Text("\(entry.rank)")
                    .font(.caption.weight(.bold).monospacedDigit())
                    .foregroundStyle(.tint)
                    .frame(width: 26, alignment: .leading)

                VendorIcon(vendor: entry.vendor, size: 28)

                VStack(alignment: .leading, spacing: 3) {
                    Text(formatName(entry.displayName).0)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text(subtitle(entry))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 6)

                VStack(alignment: .trailing, spacing: 4) {
                    Text(entry.score.map { String(format: "%.1f", $0) } ?? "-")
                        .font(.subheadline.weight(.bold).monospacedDigit())
                        .foregroundStyle(scoreColor(entry.score, minScore: scoreRange?.min, maxScore: scoreRange?.max))
                    ScoreBar(
                        score: entry.score,
                        minScore: scoreRange?.min,
                        maxScore: scoreRange?.max
                    )
                    .frame(width: 46)
                }
            }
            .padding(.vertical, 3)
        }
    }

    private var filterMenu: some View {
        Menu {
            Picker("维度", selection: $dimension) {
                ForEach(dimensions.sorted { $0.value < $1.value }, id: \.key) { key, label in
                    Text(label).tag(key)
                }
            }

            Divider()

            Picker("榜单", selection: $boardSlug) {
                ForEach(boardsForDimension) { board in
                    Text(board.name).tag(board.slug)
                }
            }
        } label: {
            Label("筛选", systemImage: "line.3.horizontal.decrease.circle")
        }
    }

    private func metric(_ label: String, _ value: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }

    private func cacheInfo(_ board: Board) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("成功抓取：\(board.health?.lastSuccessAt ?? board.lastSuccessAt ?? "未提供")")
                .foregroundStyle(.secondary)
            if let status = boardCacheStatus(board) {
                Text(status).foregroundStyle(.red)
            }
        }
        .font(.caption2)
    }

    private func subtitle(_ entry: Entry) -> String {
        var parts = [entry.vendor].compactMap { $0 }
        if let params = entry.paramsB {
            parts.append("\(formatNumber(params))B")
        }
        if let date = entry.releaseDate {
            parts.append(String(date.prefix(10)))
        }
        return parts.joined(separator: " · ")
    }

    private func upstreamDateLabel(_ value: String?) -> String {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return "未提供"
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: String(value.prefix(10))) else {
            return "\(value)（日期格式无法解析）"
        }
        let days = Calendar(identifier: .gregorian).dateComponents(
            [.day],
            from: Calendar(identifier: .gregorian).startOfDay(for: date),
            to: Calendar(identifier: .gregorian).startOfDay(for: Date())
        ).day ?? 0
        let day = String(value.prefix(10))
        if days < 0 { return "\(day)（日期晚于设备时间）" }
        if days == 0 { return "\(day)（今天）" }
        return "\(day)（\(days) 天前）"
    }

    private func weeklyChangeLabel(_ value: String?) -> String {
        guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "环比未提供"
        }
        return value.caseInsensitiveCompare("new") == .orderedSame ? "本周新进" : "周环比 \(value)"
    }

    private func weeklyChangeColor(_ value: String?) -> Color {
        guard let value, !value.isEmpty else { return .secondary }
        if value.caseInsensitiveCompare("new") == .orderedSame { return .accentColor }
        if value.hasPrefix("+") { return Color(red: 0.08, green: 0.50, blue: 0.24) }
        if value.hasPrefix("-") { return Color(red: 0.78, green: 0.07, blue: 0.24) }
        return .secondary
    }
}

func formatName(_ name: String) -> (name: String, strength: String?) {
    guard let range = name.range(of: #" \((xhigh|high|medium|low|max|non-reasoning|reasoning)( with fallback)?\)$"#, options: .regularExpression) else {
        return (name, nil)
    }
    let strength = name[range].replacingOccurrences(of: "(", with: "").replacingOccurrences(of: ")", with: "")
    return (String(name[..<range.lowerBound]), strength)
}

func formatNumber(_ value: Double) -> String {
    value == value.rounded() ? String(Int(value)) : String(format: "%.1f", value)
}
