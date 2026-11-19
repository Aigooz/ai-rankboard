import SwiftUI

struct CompareView: View {
    @ObservedObject var store: SnapshotStore
    @State private var boardSlug = ""
    @State private var firstSlug = ""
    @State private var secondSlug = ""

    private var boards: [Board] {
        store.snapshot.boards.sorted {
            if $0.sourceId != $1.sourceId {
                return $0.sourceId < $1.sourceId
            }
            return $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
    }

    private var activeBoard: Board? {
        boards.first { $0.slug == boardSlug } ?? boards.first
    }

    private var models: [Entry] {
        guard let activeBoard else { return [] }
        return store.snapshot.entries(board: activeBoard.slug)
            .sorted {
                if $0.rank != $1.rank {
                    return $0.rank < $1.rank
                }
                return $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending
            }
    }

    private var firstEntry: Entry? { models.first { $0.slug == firstSlug } }
    private var secondEntry: Entry? { models.first { $0.slug == secondSlug } }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("榜单", selection: $boardSlug) {
                        ForEach(boards) { board in
                            Text(board.name).tag(board.slug)
                        }
                    }
                    if let activeBoard {
                        LabeledContent("数据来源", value: sourceName(activeBoard.sourceId))
                        LabeledContent("榜单模型", value: "\(models.count)")
                        LabeledContent(
                            "数据时间",
                            value: (activeBoard.lastSuccessAt ?? activeBoard.sourceId).prefix(10).description
                        )
                    }
                } header: {
                    Text("比较范围")
                } footer: {
                    Text("只在同一榜单、同一评测口径内比较排名和得分。")
                }

                Section("选择模型") {
                    Picker("模型 A", selection: $firstSlug) {
                        Text("请选择").tag("")
                        ForEach(models) { entry in
                            Text(formatName(entry.displayName).0).tag(entry.slug)
                        }
                    }
                    Picker("模型 B", selection: $secondSlug) {
                        Text("请选择").tag("")
                        ForEach(models) { entry in
                            Text(formatName(entry.displayName).0).tag(entry.slug)
                        }
                    }
                }

                if firstEntry != nil || secondEntry != nil {
                    Section("对比结果") {
                        compareRow("厂商", firstEntry?.vendor ?? "-", secondEntry?.vendor ?? "-")
                        compareRow("排名", firstEntry?.rank.description ?? "-", secondEntry?.rank.description ?? "-")
                        compareRow("得分", firstEntry?.score.map { String(format: "%.1f", $0) } ?? "-", secondEntry?.score.map { String(format: "%.1f", $0) } ?? "-")
                        compareRow("榜单", activeBoard?.name ?? "-", activeBoard?.name ?? "-")
                        compareRow("参数", firstEntry?.paramsB.map { "\(formatNumber($0))B" } ?? "-", secondEntry?.paramsB.map { "\(formatNumber($0))B" } ?? "-")
                        compareRow("上下文", firstEntry?.contextWindow ?? "-", secondEntry?.contextWindow ?? "-")
                        compareRow("发布时间", firstEntry?.releaseDate ?? "-", secondEntry?.releaseDate ?? "-")
                        compareRow(
                            "输入价格",
                            formatPrice(firstEntry?.priceIn, currency: firstEntry?.currency),
                            formatPrice(secondEntry?.priceIn, currency: secondEntry?.currency)
                        )
                        compareRow(
                            "输出价格",
                            formatPrice(firstEntry?.priceOut, currency: firstEntry?.currency),
                            formatPrice(secondEntry?.priceOut, currency: secondEntry?.currency)
                        )
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("模型对比")
            .onAppear {
                if boardSlug.isEmpty { boardSlug = boards.first?.slug ?? "" }
                selectDefaultsIfNeeded()
            }
            .onChange(of: boardSlug) { _, _ in
                firstSlug = ""
                secondSlug = ""
                selectDefaultsIfNeeded()
            }
            .onChange(of: store.snapshot.boards) { _, _ in
                if !boards.contains(where: { $0.slug == boardSlug }) {
                    boardSlug = boards.first?.slug ?? ""
                }
                selectDefaultsIfNeeded()
            }
        }
    }

    private func selectDefaultsIfNeeded() {
        if !models.contains(where: { $0.slug == firstSlug }) {
            firstSlug = models.first?.slug ?? ""
        }
        if secondSlug == firstSlug || !models.contains(where: { $0.slug == secondSlug }) {
            secondSlug = models.first(where: { $0.slug != firstSlug })?.slug ?? ""
        }
    }

    private func sourceName(_ sourceId: String) -> String {
        store.snapshot.sources.first { $0.id == sourceId }?.name ?? sourceId
    }

    private func formatPrice(_ value: Double?, currency: String?) -> String {
        guard let value else { return "-" }
        let code = currency?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
        let amount = String(format: "%.2f", value)
        switch code {
        case "USD": return "$\(amount) / 1M"
        case "CNY", "RMB": return "¥\(amount) / 1M"
        case "EUR": return "€\(amount) / 1M"
        case "GBP": return "£\(amount) / 1M"
        case "JPY": return "¥\(amount) / 1M"
        case "": return "\(amount) / 1M"
        default: return "\(amount) \(code) / 1M"
        }
    }

    private func compareRow(_ title: String, _ first: String, _ second: String) -> some View {
        HStack(alignment: .top) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
            Text(first)
                .font(.subheadline.weight(.medium))
                .multilineTextAlignment(.trailing)
                .lineLimit(2)
            Text("vs")
                .font(.caption2)
                .foregroundStyle(.tertiary)
                .padding(.horizontal, 2)
            Text(second)
                .font(.subheadline.weight(.medium))
                .multilineTextAlignment(.trailing)
                .lineLimit(2)
        }
    }
}
