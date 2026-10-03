import Foundation
import SwiftUI

@MainActor
final class AppUpdateStore: ObservableObject {
    static let defaultManifestURL = URL(
        string: "https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/app-update.json"
    )!

    @Published private(set) var update: AppUpdate?
    @Published private(set) var isChecking = false
    @Published private(set) var message = ""
    @Published private(set) var lastCheckedAt: Date?
    @Published var isUpdatePresented = false

    private let lastCheckKey = "lastAppUpdateCheckAt"
    private let updateCheckInterval: TimeInterval = 6 * 60 * 60

    var currentVersionName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-"
    }

    var currentVersionCode: Int {
        let raw = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String
        return raw.flatMap(Int.init) ?? 0
    }

    func checkIfNeeded() async {
        let last = UserDefaults.standard.object(forKey: lastCheckKey) as? Date
        if let last, Date().timeIntervalSince(last) < updateCheckInterval {
            return
        }
        await check()
    }

    func check() async {
        guard !isChecking else { return }
        isChecking = true
        lastCheckedAt = Date()
        defer { isChecking = false }

        do {
            let (data, response) = try await URLSession.shared.data(for: request(for: Self.defaultManifestURL))
            guard let response = response as? HTTPURLResponse, (200..<300).contains(response.statusCode) else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? -1
                throw AppUpdateError.http(code)
            }
            let manifest = try JSONDecoder().decode(AppUpdateManifest.self, from: data)
            let latest = manifest.iOSUpdate

            if latest.versionCode <= currentVersionCode {
                update = nil
                message = "应用已是最新版本 v\(currentVersionName)"
            } else if latest.installURL == nil && latest.ipaURL == nil {
                update = nil
                message = "发现新版本，但更新清单缺少 iOS 安装地址"
            } else {
                update = latest
                message = "发现新版本 v\(latest.versionName)"
                isUpdatePresented = true
            }

            UserDefaults.standard.set(Date(), forKey: lastCheckKey)
        } catch {
            message = "应用更新检查失败：\(error.localizedDescription)"
        }
    }

    func open(_ update: AppUpdate) {
        if let urlString = update.installURL ?? update.ipaURL,
           let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }

    private func request(for url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("AI-Rankboard-iOS-Updater/\(currentVersionName)", forHTTPHeaderField: "User-Agent")
        return request
    }
}

struct AppUpdate: Identifiable {
    let versionCode: Int
    let versionName: String
    let notes: String?
    let installURL: String?
    let ipaURL: String?
    let sizeBytes: Int64?

    var id: Int { versionCode }

    var summary: String {
        var lines: [String] = []
        if let size = sizeBytes, size > 0 {
            lines.append("安装包大小：\(formatByteCount(size))")
        }
        if let notes, !notes.isEmpty {
            lines.append(notes)
        }
        return lines.joined(separator: "\n")
    }
}

private struct AppUpdateManifest: Decodable {
    let versionCode: Int?
    let versionName: String?
    let notes: String?
    let sizeBytes: Int64?
    let iOSVersionCode: Int?
    let iOSVersionName: String?
    let iOSNotes: String?
    let iOSIpaURL: String?
    let iOSInstallURL: String?
    let iOSSizeBytes: Int64?

    enum CodingKeys: String, CodingKey {
        case versionCode
        case versionName
        case notes
        case sizeBytes
        case iOSVersionCode = "iosVersionCode"
        case iOSVersionName = "iosVersionName"
        case iOSNotes = "iosNotes"
        case iOSIpaURL = "iosIpaUrl"
        case iOSInstallURL = "iosInstallUrl"
        case iOSSizeBytes = "iosSizeBytes"
    }

    var iOSUpdate: AppUpdate {
        AppUpdate(
            versionCode: iOSVersionCode ?? versionCode ?? 0,
            versionName: iOSVersionName ?? versionName ?? "",
            notes: iOSNotes ?? notes,
            installURL: iOSInstallURL,
            ipaURL: iOSIpaURL,
            sizeBytes: iOSSizeBytes
        )
    }
}

private func formatByteCount(_ bytes: Int64) -> String {
    ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
}

private enum AppUpdateError: LocalizedError {
    case http(Int)

    var errorDescription: String? {
        switch self {
        case .http(let code):
            return "HTTP \(code)"
        }
    }
}
