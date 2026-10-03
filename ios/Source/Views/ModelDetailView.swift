import SwiftUI

struct ModelDetailView: View {
    let entry: Entry
    let snapshot: Snapshot
    @ObservedObject var favorites: FavoritesStore

    private var detail: ModelDetail? {
        snapshot.models[entry.slug]
    }

    private var scores: [(board: Board, entry: Entry)] {
        snapshot.boards.compactMap { board in
            let matching = snapshot.entries(board: board.slug).first { $0.slug == entry.slug }
            return matching.map { (board, $0) }
        }
        .sorted { $0.entry.rank < $1.entry.rank }
    }

    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    VendorIcon(vendor: entry.vendor, size: 52)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(formatName(entry.displayName).0)
                            .font(.title3.weight(.bold))
                        Text(entry.vendor ?? "未知厂商")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        favorites.toggle(entry.slug)
                    } label: {
                        Image(systemName: favorites.contains(entry.slug) ? "star.fill" : "star")
                            .foregroundStyle(.yellow)
                    }
                }
                .padding(.vertical, 4)
            }

            Section("模型信息") {
                infoRow("参数规模", entry.paramsB.map { "\(formatNumber($0))B" } ?? detail?.paramsB.map { "\(formatNumber($0))B" } ?? "未知")
                infoRow("上下文长度", entry.contextWindow ?? detail?.contextWindow ?? "未知")
                infoRow("许可证", entry.license ?? detail?.license ?? "未知")
                infoRow("发布时间", entry.releaseDate ?? detail?.releaseDate ?? "未知")
                infoRow("数据时间", entry.fetchedAt.isEmpty ? snapshot.generatedAt : entry.fetchedAt)
                if let sourceURL = entry.sourceUrl ?? detail?.sourceUrl, let url = URL(string: sourceURL) {
                    Link(destination: url) {
                        HStack {
                            Text("榜单来源")
                                .foregroundStyle(.primary)
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Section("价格") {
                HStack {
                    Text("输入价格")
                    Spacer()
                    Text(entry.priceIn.map { formatPrice($0, currency: entry.currency) } ?? "未知")
                        .foregroundStyle(.secondary)
                }
                HStack {
                    Text("输出价格")
                    Spacer()
                    Text(entry.priceOut.map { formatPrice($0, currency: entry.currency) } ?? "未知")
                        .foregroundStyle(.secondary)
                }
            }

            if !scores.isEmpty {
                Section("各榜单得分") {
                    ForEach(scores, id: \.board.slug) { item in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(item.board.name)
                                    .font(.subheadline)
                                    .lineLimit(1)
                                Spacer()
                                Text("排名 #\(item.entry.rank)")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.secondary)
                                Text(item.entry.score.map { String(format: "%.1f", $0) } ?? "-")
                                    .font(.subheadline.weight(.bold).monospacedDigit())
                            }
                            ScoreBar(
                                score: item.entry.score,
                                minScore: 0,
                                maxScore: max(item.entry.score ?? 0, 100)
                            )
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("模型详情")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func infoRow(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
        }
    }

    private func formatPrice(_ value: Double, currency: String) -> String {
        let symbol = currency.uppercased() == "USD" ? "$" : "¥"
        return "\(symbol)\(String(format: "%.2f", value)) / 1M"
    }
}
