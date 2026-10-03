import SwiftUI

struct CompareView: View {
    @ObservedObject var store: SnapshotStore
    @State private var firstSlug = ""
    @State private var secondSlug = ""

    private var models: [Entry] {
        store.snapshot.boards
            .flatMap { store.snapshot.entries(board: $0.slug) }
            .reduce(into: [String: Entry]()) { partial, entry in
                if let current = partial[entry.slug] {
                    if entry.rank < current.rank {
                        partial[entry.slug] = entry
                    }
                } else {
                    partial[entry.slug] = entry
                }
            }
            .values
            .sorted { $0.displayName.lowercased() < $1.displayName.lowercased() }
    }

    private var firstEntry: Entry? { models.first { $0.slug == firstSlug } }
    private var secondEntry: Entry? { models.first { $0.slug == secondSlug } }

    var body: some View {
        NavigationStack {
            List {
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
                        compareRow("参数", firstEntry?.paramsB.map { "\(formatNumber($0))B" } ?? "-", secondEntry?.paramsB.map { "\(formatNumber($0))B" } ?? "-")
                        compareRow("上下文", firstEntry?.contextWindow ?? "-", secondEntry?.contextWindow ?? "-")
                        compareRow("发布时间", firstEntry?.releaseDate ?? "-", secondEntry?.releaseDate ?? "-")
                        compareRow(
                            "输入价格",
                            firstEntry?.priceIn.map { "¥\($0)/1M" } ?? "-",
                            secondEntry?.priceIn.map { "¥\($0)/1M" } ?? "-"
                        )
                        compareRow(
                            "输出价格",
                            firstEntry?.priceOut.map { "¥\($0)/1M" } ?? "-",
                            secondEntry?.priceOut.map { "¥\($0)/1M" } ?? "-"
                        )
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("模型对比")
            .onAppear {
                if firstSlug.isEmpty {
                    firstSlug = models.first?.slug ?? ""
                }
                if secondSlug.isEmpty {
                    secondSlug = models.dropFirst().first?.slug ?? ""
                }
            }
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
