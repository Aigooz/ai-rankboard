import Foundation

struct SnapshotSource: Codable, Hashable {
    var id: String = ""
    var name: String = ""
    var url: String = ""
}

struct Snapshot: Codable, Hashable {
    var schemaVersion = 0
    var generatedAt = ""
    var sources: [SnapshotSource] = []
    var boards: [Board] = []
    var entriesByBoard: [String: [Entry]] = [:]
    var models: [String: ModelDetail] = [:]

    static let empty = Snapshot()

    func board(_ slug: String) -> Board? {
        boards.first { $0.slug == slug }
    }

    func entries(board slug: String) -> [Entry] {
        entriesByBoard[slug] ?? []
    }

    func entries(dimension: String) -> [Entry] {
        boards
            .filter { $0.dimension == dimension }
            .flatMap { entries(board: $0.slug) }
            .reduce(into: [String: Entry]()) { result, entry in
                if let current = result[entry.slug] {
                    if entry.rank < current.rank || (entry.rank == current.rank && entry.score ?? -1 > current.score ?? -1) {
                        result[entry.slug] = entry
                    }
                } else {
                    result[entry.slug] = entry
                }
            }
            .values
            .sorted { $0.rank < $1.rank }
    }
}

struct Board: Codable, Hashable, Identifiable {
    var slug: String = ""
    var name: String = ""
    var dimension: String = ""
    var sourceId: String = "modelsage"
    var scoreType: String?
    var url: String?
    var lastSuccessAt: String?
    var modelCount = 0

    var id: String { slug }
}

struct Entry: Codable, Hashable, Identifiable {
    var slug: String = ""
    var displayName: String = ""
    var vendor: String?
    var paramsB: Double?
    var license: String?
    var contextWindow: String?
    var releaseDate: String?
    var rank = 0
    var score: Double?
    var scoreCi: Double?
    var votes: Int?
    var priceIn: Double?
    var priceOut: Double?
    var currency: String = "CNY"
    var fetchedAt: String = ""
    var sourceUrl: String?

    var id: String { slug }
}

struct ModelDetail: Codable, Hashable, Identifiable {
    var slug: String = ""
    var displayName: String = ""
    var vendor: String?
    var paramsB: Double?
    var license: String?
    var contextWindow: String?
    var sourceUrl: String?
    var releaseDate: String?

    var id: String { slug }
}

private extension KeyedDecodingContainer {
    func decodeDefaultedString(_ key: Key, defaultValue: String) throws -> String {
        (try decodeIfPresent(String.self, forKey: key) ?? defaultValue).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func decodeDefaultedInt(_ key: Key, defaultValue: Int) throws -> Int {
        try decodeIfPresent(Int.self, forKey: key) ?? defaultValue
    }
}

extension Snapshot {
    private enum CodingKeys: String, CodingKey {
        case schemaVersion
        case generatedAt
        case sources
        case boards
        case entriesByBoard
        case models
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try container.decodeDefaultedInt(.schemaVersion, defaultValue: 0)
        generatedAt = try container.decodeDefaultedString(.generatedAt, defaultValue: "")
        sources = try container.decodeIfPresent([SnapshotSource].self, forKey: .sources) ?? []
        boards = try container.decodeIfPresent([Board].self, forKey: .boards) ?? []
        entriesByBoard = try container.decodeIfPresent([String: [Entry]].self, forKey: .entriesByBoard) ?? [:]
        models = try container.decodeIfPresent([String: ModelDetail].self, forKey: .models) ?? [:]
    }
}

extension Board {
    private enum CodingKeys: String, CodingKey {
        case slug
        case name
        case dimension
        case sourceId = "source_id"
        case scoreType = "score_type"
        case url
        case lastSuccessAt = "last_success_at"
        case modelCount = "model_count"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        slug = try container.decodeDefaultedString(.slug, defaultValue: "")
        name = try container.decodeDefaultedString(.name, defaultValue: "")
        dimension = try container.decodeDefaultedString(.dimension, defaultValue: "")
        sourceId = try container.decodeDefaultedString(.sourceId, defaultValue: "modelsage")
        scoreType = try container.decodeIfPresent(String.self, forKey: .scoreType)
        url = try container.decodeIfPresent(String.self, forKey: .url)
        lastSuccessAt = try container.decodeIfPresent(String.self, forKey: .lastSuccessAt)
        modelCount = try container.decodeDefaultedInt(.modelCount, defaultValue: 0)
    }
}

extension Entry {
    private enum CodingKeys: String, CodingKey {
        case slug
        case displayName = "display_name"
        case vendor
        case paramsB = "params_b"
        case license
        case contextWindow = "context_window"
        case releaseDate = "release_date"
        case rank
        case score
        case scoreCi = "score_ci"
        case votes
        case priceIn = "price_in"
        case priceOut = "price_out"
        case currency
        case fetchedAt = "fetched_at"
        case sourceUrl = "source_url"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        slug = try container.decodeDefaultedString(.slug, defaultValue: "")
        displayName = try container.decodeDefaultedString(.displayName, defaultValue: "")
        vendor = try container.decodeIfPresent(String.self, forKey: .vendor)
        paramsB = try container.decodeIfPresent(Double.self, forKey: .paramsB)
        license = try container.decodeIfPresent(String.self, forKey: .license)
        contextWindow = try container.decodeIfPresent(String.self, forKey: .contextWindow)
        releaseDate = try container.decodeIfPresent(String.self, forKey: .releaseDate)
        rank = try container.decodeDefaultedInt(.rank, defaultValue: 0)
        score = try container.decodeIfPresent(Double.self, forKey: .score)
        scoreCi = try container.decodeIfPresent(Double.self, forKey: .scoreCi)
        votes = try container.decodeIfPresent(Int.self, forKey: .votes)
        priceIn = try container.decodeIfPresent(Double.self, forKey: .priceIn)
        priceOut = try container.decodeIfPresent(Double.self, forKey: .priceOut)
        currency = try container.decodeDefaultedString(.currency, defaultValue: "CNY")
        fetchedAt = try container.decodeDefaultedString(.fetchedAt, defaultValue: "")
        sourceUrl = try container.decodeIfPresent(String.self, forKey: .sourceUrl)
    }
}

extension ModelDetail {
    private enum CodingKeys: String, CodingKey {
        case slug
        case displayName = "display_name"
        case vendor
        case paramsB = "params_b"
        case license
        case contextWindow = "context_window"
        case sourceUrl = "source_url"
        case releaseDate = "release_date"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        slug = try container.decodeDefaultedString(.slug, defaultValue: "")
        displayName = try container.decodeDefaultedString(.displayName, defaultValue: "")
        vendor = try container.decodeIfPresent(String.self, forKey: .vendor)
        paramsB = try container.decodeIfPresent(Double.self, forKey: .paramsB)
        license = try container.decodeIfPresent(String.self, forKey: .license)
        contextWindow = try container.decodeIfPresent(String.self, forKey: .contextWindow)
        sourceUrl = try container.decodeIfPresent(String.self, forKey: .sourceUrl)
        releaseDate = try container.decodeIfPresent(String.self, forKey: .releaseDate)
    }
}
