import Foundation
import CryptoKit

@MainActor
final class SnapshotStore: ObservableObject {
    static let defaultRemoteURL = "https://raw.githubusercontent.com/Aigooz/ai-rankboard/main/android/app/src/main/assets/leaderboards.json"

    @Published private(set) var snapshot: Snapshot = .empty
    @Published private(set) var isLoading = false
    @Published private(set) var message = ""
    @Published private(set) var lastCheckedAt: Date?

    private var decoder = JSONDecoder()
    private let fileManager = FileManager.default
    private let remoteURLKey = "remoteSnapshotURL"
    private let remoteTokenKey = "remoteSnapshotToken"
    private let lastRefreshKey = "lastSnapshotRefreshAt"

    var remoteURLString: String {
        get { UserDefaults.standard.string(forKey: remoteURLKey) ?? Self.defaultRemoteURL }
        set {
            UserDefaults.standard.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: remoteURLKey)
        }
    }

    var remoteTokenString: String {
        get { UserDefaults.standard.string(forKey: remoteTokenKey) ?? "" }
        set {
            UserDefaults.standard.set(newValue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: remoteTokenKey)
        }
    }

    init() {
        Task {
            await loadInitial()
            await refreshIfStale()
        }
    }

    func loadInitial() async {
        if loadDownloadedSnapshot() {
            return
        }
        loadBundledSnapshot()
    }

    func loadBundledSnapshot() {
        guard let url = Bundle.main.url(forResource: "leaderboards", withExtension: "json"),
              let data = try? Data(contentsOf: url) else {
            message = "内置榜单数据缺失"
            return
        }
        decodeAndApply(data, downloaded: false)
    }

    private func loadDownloadedSnapshot() -> Bool {
        let url = downloadedJSONURL
        guard let data = try? Data(contentsOf: url) else {
            return false
        }
        return decodeAndApply(data, downloaded: true)
    }

    @discardableResult
    private func decodeAndApply(_ data: Data, downloaded: Bool) -> Bool {
        if let decoded = try? decoder.decode(Snapshot.self, from: data) {
            snapshot = decoded
            message = downloaded ? "已加载远端快照" : "已加载内置快照"
            return true
        }
        message = "榜单数据解析失败"
        return false
    }

    func refresh() async {
        let urlString = remoteURLString.isEmpty
            ? Self.defaultRemoteURL
            : remoteURLString
        guard let url = URL(string: urlString) else {
            message = "远端快照地址无效"
            return
        }
        isLoading = true
        defer { isLoading = false }
        lastCheckedAt = Date()

        do {
            let (data, _) = try await URLSession.shared.data(for: request(for: url))
            let expectedHash = try await remoteHash(for: url)
            if let expectedHash, !verify(data, expectedHash: expectedHash) {
                message = "远端数据校验失败"
                return
            }
            if let existing = try? Data(contentsOf: downloadedJSONURL),
               let expectedHash = try await remoteHash(for: url),
               verify(existing, expectedHash: expectedHash) {
                message = "榜单数据已是最新"
                UserDefaults.standard.set(Date(), forKey: lastRefreshKey)
                return
            }
            guard let decoded = try? decoder.decode(Snapshot.self, from: data) else {
                message = "远端数据解析失败"
                return
            }
            guard decoded.schemaVersion >= 1, !decoded.boards.isEmpty else {
                message = "远端数据版本不支持"
                return
            }
            try? fileManager.createDirectory(at: documentsDirectory, withIntermediateDirectories: true)
            try data.write(to: downloadedJSONURL, options: .atomic)
            snapshot = decoded
            message = "榜单数据已更新"
            UserDefaults.standard.set(Date(), forKey: lastRefreshKey)
        } catch {
            message = "更新失败：\(error.localizedDescription)"
        }
    }

    private func remoteHash(for url: URL) async throws -> String? {
        guard let hashURL = URL(string: url.absoluteString + ".sha256") else { return nil }
        do {
            let (data, _) = try await URLSession.shared.data(for: request(for: hashURL))
            return String(data: data, encoding: .utf8)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .split(separator: " ")
                .first
                .map(String.init)
        } catch {
            return nil
        }
    }

    private func verify(_ data: Data, expectedHash: String) -> Bool {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() == expectedHash.lowercased()
    }

    private func request(for url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.setValue("AI-Rankboard-iOS", forHTTPHeaderField: "User-Agent")

        let token = remoteTokenString
        let host = url.host?.lowercased() ?? ""
        if !token.isEmpty, host == "raw.githubusercontent.com" || host == "github.com" {
            request.setValue("token \(token)", forHTTPHeaderField: "Authorization")
        }
        return request
    }

    private var documentsDirectory: URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    private var downloadedJSONURL: URL {
        documentsDirectory.appendingPathComponent("leaderboards.json")
    }

    private func refreshIfStale() async {
        let last = UserDefaults.standard.object(forKey: lastRefreshKey) as? Date
        if let last, Date().timeIntervalSince(last) < 6 * 60 * 60 {
            return
        }
        await refresh()
    }
}
