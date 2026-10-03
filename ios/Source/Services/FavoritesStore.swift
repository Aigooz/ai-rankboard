import Foundation

@MainActor
final class FavoritesStore: ObservableObject {
    @Published private(set) var slugs: Set<String> = []

    private let key = "favoriteModelSlugs"

    init() {
        slugs = Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
    }

    func contains(_ slug: String) -> Bool {
        slugs.contains(slug)
    }

    func toggle(_ slug: String) {
        if slugs.contains(slug) {
            slugs.remove(slug)
        } else {
            slugs.insert(slug)
        }
        UserDefaults.standard.set(Array(slugs).sorted(), forKey: key)
    }
}
