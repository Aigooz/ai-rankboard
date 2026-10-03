import SwiftUI

struct ContentView: View {
    @StateObject private var store = SnapshotStore()
    @StateObject private var favorites = FavoritesStore()
    @StateObject private var appUpdate = AppUpdateStore()

    var body: some View {
        TabView {
            RankingsView(store: store, favorites: favorites)
                .tabItem {
                    Label("榜单", systemImage: "chart.bar.fill")
                }

            CompareView(store: store)
                .tabItem {
                    Label("对比", systemImage: "rectangle.split.2x1")
                }

            FavoritesView(snapshotStore: store, favorites: favorites)
                .tabItem {
                    Label("收藏", systemImage: "star")
                }

            SettingsView(store: store, favorites: favorites, appUpdate: appUpdate)
                .tabItem {
                    Label("设置", systemImage: "gearshape")
                }
        }
        .task {
            await appUpdate.checkIfNeeded()
        }
        .alert(
            "发现新版本",
            isPresented: $appUpdate.isUpdatePresented,
            presenting: appUpdate.update
        ) { update in
            Button("查看更新") {
                appUpdate.open(update)
            }
            Button("稍后", role: .cancel) {}
        } message: { update in
            Text(update.summary)
        }
    }
}
