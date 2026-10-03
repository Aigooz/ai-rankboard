import SwiftUI

struct FavoritesView: View {
    @ObservedObject var snapshotStore: SnapshotStore
    @ObservedObject var favorites: FavoritesStore

    private var favoriteEntries: [Entry] {
        snapshotStore.snapshot.boards
            .flatMap { snapshotStore.snapshot.entries(board: $0.slug) }
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
            .filter { favorites.contains($0.slug) }
            .sorted { $0.rank < $1.rank }
    }

    var body: some View {
        NavigationStack {
            List {
                if favoriteEntries.isEmpty {
                    Text("还没有收藏模型")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, 32)
                } else {
                    ForEach(favoriteEntries) { entry in
                        NavigationLink {
                            ModelDetailView(
                                entry: entry,
                                snapshot: snapshotStore.snapshot,
                                favorites: favorites
                            )
                        } label: {
                            HStack(spacing: 10) {
                                VendorIcon(vendor: entry.vendor, size: 28)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(formatName(entry.displayName).0)
                                        .font(.subheadline.weight(.semibold))
                                        .lineLimit(1)
                                    Text(entry.vendor ?? "未知厂商")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text(entry.score.map { String(format: "%.1f", $0) } ?? "-")
                                    .font(.subheadline.weight(.bold).monospacedDigit())
                            }
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                favorites.toggle(entry.slug)
                            } label: {
                                Label("取消收藏", systemImage: "star.slash")
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("我的收藏")
        }
    }
}
