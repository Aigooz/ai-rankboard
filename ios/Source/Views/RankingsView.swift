import SwiftUI

struct RankingsView: View {
    @ObservedObject var store: SnapshotStore
    @ObservedObject var favorites: FavoritesStore
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
            .filter { $0.dimension == dimension }
            .sorted {
                if $0.sourceId == "modelsage" && $1.sourceId != "modelsage" { return true }
                if $0.sourceId != "modelsage" && $1.sourceId == "modelsage" { return false }
                return $0.name < $1.name
            }
    }

    private var snapshot: Snapshot { store.snapshot }

    private var activeBoard: Board? {
        boardsForDimension.first { $0.slug == boardSlug } ?? boardsForDimension.first
    }

    private var entries: [Entry] {
        let base = activeBoard.map { snapshot.entries(board: $0.slug) } ?? []
        let normalizedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !normalizedQuery.isEmpty else { return base }
        return base.filter { $0.displayName.lowercased().contains(normalizedQuery) }
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
                        overview
                        ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                            row(entry)
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
            .navigationTitle("AI 排行榜")
            .toolbarTitleDisplayMode(.inline)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "搜索模型名称")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    filterMenu
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
                boardSlug = snapshot.boards.first { $0.dimension == newValue }?.slug ?? ""
            }
            .onChange(of: snapshot.boards) { _, _ in
                if boardsForDimension.first(where: { $0.slug == boardSlug }) == nil {
                    boardSlug = boardsForDimension.first?.slug ?? ""
                }
            }
        }
    }

    private var overview: some View {
        Section {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(activeBoard?.name ?? "选择榜单")
                        .font(.headline)
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
