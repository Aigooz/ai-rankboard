import SwiftUI

struct ContentView: View {
    @StateObject private var store = SnapshotStore()
    @StateObject private var favorites = FavoritesStore()

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

            SettingsView(store: store, favorites: favorites)
                .tabItem {
                    Label("设置", systemImage: "gearshape")
                }
        }
    }
}
