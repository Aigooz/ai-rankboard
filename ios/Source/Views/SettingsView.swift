import SwiftUI

struct SettingsView: View {
    @ObservedObject var store: SnapshotStore
    @ObservedObject var favorites: FavoritesStore
    @State private var remoteURL = ""
    @State private var remoteToken = ""

    private var snapshot: Snapshot { store.snapshot }

    var body: some View {
        NavigationStack {
            Form {
                Section("数据更新") {
                    TextField("远端快照地址", text: $remoteURL, prompt: Text(SnapshotStore.defaultRemoteURL))
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    SecureField("私有仓库访问令牌", text: $remoteToken)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    Button {
                        store.remoteURLString = remoteURL
                        store.remoteTokenString = remoteToken
                        Task { await store.refresh() }
                    } label: {
                        HStack {
                            if store.isLoading {
                                ProgressView()
                            } else {
                                Image(systemName: "arrow.down.circle")
                            }
                            Text("立即检查更新")
                        }
                    }
                    .disabled(store.isLoading)

                    if !store.message.isEmpty {
                        Text(store.message)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Text("默认从项目仓库同步快照。若仓库私有，填写具有读取权限的 GitHub Token。")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }

                Section("数据概览") {
                    row("快照时间", String(snapshot.generatedAt.prefix(19)))
                    row("Schema 版本", "v\(snapshot.schemaVersion)")
                    row("模型数量", "\(snapshot.models.count)")
                    row("榜单数量", "\(snapshot.boards.count)")
                    row("数据源数量", "\(max(snapshot.sources.count, 1))")
                    row("收藏数量", "\(favorites.slugs.count)")
                }

                Section("数据来源") {
                    ForEach(snapshot.sources.isEmpty ? defaultSources : snapshot.sources, id: \.id) { source in
                        if let url = URL(string: source.url), !source.url.isEmpty {
                            Link(destination: url) {
                                HStack {
                                    Text(source.name.isEmpty ? source.id : source.name)
                                    Spacer()
                                    Image(systemName: "arrow.up.right")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        } else {
                            Text(source.name.isEmpty ? source.id : source.name)
                        }
                    }
                }

                Section("关于") {
                    Link(destination: URL(string: "https://github.com/Aigooz/ai-rankboard")!) {
                        HStack {
                            Text("GitHub 项目")
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Link(destination: URL(string: "https://github.com/Aigooz/ai-rankboard/issues")!) {
                        HStack {
                            Text("反馈问题")
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("设置")
            .onAppear {
                remoteURL = store.remoteURLString
                remoteToken = store.remoteTokenString
            }
        }
    }

    private var defaultSources: [SnapshotSource] {
        [
            SnapshotSource(id: "modelsage", name: "ModelSage", url: "https://modelsage.cn/"),
            SnapshotSource(id: "livebench", name: "LiveBench", url: "https://livebench.ai/leaderboard"),
            SnapshotSource(id: "swebench", name: "SWE-bench", url: "https://www.swebench.com"),
        ]
    }

    private func row(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.trailing)
        }
    }
}
