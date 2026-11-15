import CryptoKit
import Foundation
import Security
#if os(iOS)
import UIKit
#endif

struct GarminActivitySummary: Codable, Identifiable {
    let activityId: Int64
    let activityName: String?
    let startTimeLocal: String?
    let duration: Double?
    var id: Int64 { activityId }
}

enum GarminVaultError: Error {
    case unavailable
    case invalidData
    case rejected(Int)
}

struct GarminOwnerVault {
    private let account: UUID
    private let service = "no.weatheragent.turvaer.garmin-owner"

    init(account: UUID) {
        self.account = account
    }

    /// A reinstall loses the app container. Discard any Keychain items left by the old installation.
    func prepareInstallation() throws {
        let marker = try fileURL().appendingPathExtension("installed")
        guard !FileManager.default.fileExists(atPath: marker.path) else { return }
        try erase()
        #if os(iOS)
        try Data([1]).write(to: marker, options: [.atomic, .completeFileProtection])
        #else
        try Data([1]).write(to: marker, options: .atomic)
        #endif
    }

    func credential() throws -> String? {
        guard let data = try keychainRead("credential") else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func rememberCredential(_ credential: String) throws {
        try keychainWrite(Data(credential.utf8), name: "credential")
    }

    func forgetCredential() throws {
        try keychainDelete("credential")
    }

    func activities() throws -> [GarminActivitySummary] {
        let file = try fileURL()
        guard FileManager.default.fileExists(atPath: file.path) else { return [] }
        guard let key = try keychainRead("encryption") else { throw GarminVaultError.unavailable }
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size <= 512_000 else { throw GarminVaultError.invalidData }
        let sealed = try Data(contentsOf: file)
        let clear = try Self.open(sealed, key: SymmetricKey(data: key), account: account)
        return try JSONDecoder().decode([GarminActivitySummary].self, from: clear)
    }

    func merge(_ newActivities: [GarminActivitySummary]) throws -> [GarminActivitySummary] {
        var records: [Int64: GarminActivitySummary] = [:]
        for activity in try activities() + newActivities { records[activity.id] = activity }
        let combined = Array(records.values.sorted { $0.id > $1.id }.prefix(500))
        let clear = try JSONEncoder().encode(combined)
        guard clear.count <= 500_000 else { throw GarminVaultError.invalidData }
        let key = try encryptionKey()
        let sealed = try Self.seal(clear, key: key, account: account)
        let file = try fileURL()
        #if os(iOS)
        try sealed.write(to: file, options: [.atomic, .completeFileProtection])
        #else
        try sealed.write(to: file, options: .atomic)
        #endif
        return combined
    }

    func erase() throws {
        try keychainDelete("encryption")
        try keychainDelete("credential")
        let file = try fileURL()
        if FileManager.default.fileExists(atPath: file.path) {
            try FileManager.default.removeItem(at: file)
        }
    }

    static func seal(_ data: Data, key: SymmetricKey, account: UUID) throws -> Data {
        guard let combined = try AES.GCM.seal(data, using: key, authenticating: context(account)).combined
        else { throw GarminVaultError.invalidData }
        return combined
    }

    static func open(_ data: Data, key: SymmetricKey, account: UUID) throws -> Data {
        try AES.GCM.open(AES.GCM.SealedBox(combined: data), using: key, authenticating: context(account))
    }

    private static func context(_ account: UUID) -> Data {
        Data("garmin-activities-v1:\(account.uuidString)".utf8)
    }

    private func encryptionKey() throws -> SymmetricKey {
        if let existing = try keychainRead("encryption") { return SymmetricKey(data: existing) }
        let key = SymmetricKey(size: .bits256)
        try keychainWrite(key.withUnsafeBytes { Data($0) }, name: "encryption")
        return key
    }

    private func fileURL() throws -> URL {
        var directory = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask,
                                                    appropriateFor: nil, create: true)
            .appendingPathComponent("GarminOwnerVault", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var settings = URLResourceValues()
        settings.isExcludedFromBackup = true
        try directory.setResourceValues(settings)
        return directory.appendingPathComponent(account.uuidString + ".gcm")
    }

    private func keychainQuery(_ name: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: account.uuidString + "." + name, kSecAttrSynchronizable as String: false]
    }

    private func keychainRead(_ name: String) throws -> Data? {
        var query = keychainQuery(name)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var entry: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &entry)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = entry as? Data else { throw GarminVaultError.unavailable }
        return data
    }

    private func keychainWrite(_ data: Data, name: String) throws {
        let query = keychainQuery(name)
        let attrs: [String: Any] = [kSecValueData as String: data]
        let updated = SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
        if updated == errSecSuccess { return }
        guard updated == errSecItemNotFound else { throw GarminVaultError.unavailable }
        var entry = query.merging(attrs) { _, new in new }
        entry[kSecAttrAccessible as String] = kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
        guard SecItemAdd(entry as CFDictionary, nil) == errSecSuccess else { throw GarminVaultError.unavailable }
    }

    private func keychainDelete(_ name: String) throws {
        let result = SecItemDelete(keychainQuery(name) as CFDictionary)
        guard result == errSecSuccess || result == errSecItemNotFound else { throw GarminVaultError.unavailable }
    }
}

#if DEBUG && GARMIN_PERSONAL_OWNER
private final class GarminNoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

enum GarminOwnerClient {
    private static let origin = "https://personal.connectapi.garmin.com"

    static func validKey(_ key: String) -> Bool {
        key.range(of: #"^GCDC_[A-Za-z0-9._+/=-]{12,508}$"#, options: .regularExpression) != nil
    }

    static func refresh(key: String) async throws -> [GarminActivitySummary] {
        guard validKey(key) else { throw GarminVaultError.invalidData }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 20
        let session = URLSession(configuration: configuration, delegate: GarminNoRedirect(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }

        let tokenData = try await send(session, path: "/personalapitoken-service/personal-api/personal-api-key/token",
                                       method: "POST", authorization: "ApiKey " + key, limit: 8192)
        guard let token = String(data: tokenData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              token.range(of: #"^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$"#, options: .regularExpression) != nil
        else { throw GarminVaultError.invalidData }

        let data = try await send(session, path: "/activitylist-service/activities/search/activities?start=0&limit=20",
                                  method: "GET", authorization: "Bearer " + token, limit: 512_000)
        return try decode(data)
    }

    static func decode(_ data: Data) throws -> [GarminActivitySummary] {
        struct Wrapper: Decodable { let data: [GarminActivitySummary] }
        let decoder = JSONDecoder()
        let raw = try (try? decoder.decode([GarminActivitySummary].self, from: data))
            ?? decoder.decode(Wrapper.self, from: data).data
        let valid = raw.filter { row in
            row.id > 0 && (row.activityName?.count ?? 0) <= 150 && (row.startTimeLocal?.count ?? 0) <= 32
                && (row.duration.map { $0.isFinite && $0 >= 0 && $0 <= 604800 } ?? true)
        }
        if !raw.isEmpty && valid.isEmpty { throw GarminVaultError.invalidData }
        return Array(valid.prefix(20))
    }

    private static func send(_ session: URLSession, path: String, method: String,
                             authorization: String, limit: Int) async throws -> Data {
        guard let url = URL(string: origin + path) else { throw GarminVaultError.invalidData }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData)
        request.httpMethod = method
        request.setValue(authorization, forHTTPHeaderField: "Authorization")
        let (stream, reply) = try await session.bytes(for: request)
        guard let response = reply as? HTTPURLResponse, response.url == url else {
            throw GarminVaultError.invalidData
        }
        guard response.statusCode == 200 else { throw GarminVaultError.rejected(response.statusCode) }
        guard response.expectedContentLength <= Int64(limit) else { throw GarminVaultError.invalidData }
        var result = Data()
        for try await byte in stream {
            guard result.count < limit else { throw GarminVaultError.invalidData }
            result.append(byte)
        }
        return result
    }
}
#endif
