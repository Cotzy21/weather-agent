import CryptoKit
import Foundation

@main
struct WeatherGarminVaultCheck {
    static func main() throws {
        let payload = Data("synthetic encryption QA; no health data".utf8)
        let key = SymmetricKey(size: .bits256)
        let account = UUID()
        let first = try GarminOwnerVault.seal(payload, key: key, account: account)
        let second = try GarminOwnerVault.seal(payload, key: key, account: account)
        precondition(first != second)
        precondition(first.range(of: payload) == nil)
        let decoded = try GarminOwnerVault.open(first, key: key, account: account)
        precondition(decoded == payload)
        do {
            _ = try GarminOwnerVault.open(first, key: key, account: UUID())
            fatalError("wrong account unexpectedly worked")
        } catch {}
        var tampered = first
        tampered[tampered.count - 1] ^= 1
        do {
            _ = try GarminOwnerVault.open(tampered, key: key, account: account)
            fatalError("tampering unexpectedly worked")
        } catch {}
        precondition(GarminOwnerClient.validKey("GCDC_test_fixture_but_not_a_live_key"))
        precondition(!GarminOwnerClient.validKey("GCDC_invalid\r\nHeader: injected"))
        let parsed = try GarminOwnerClient.decode(Data(#"{"data":[{"activityId":111,"duration":60},{"activityId":-1,"duration":50}]}"#.utf8))
        precondition(parsed.count == 1 && parsed[0].activityId == 111)
        print("AES-GCM round trip, randomized nonce, tamper rejection, account binding, key validation and response parsing passed")
    }
}
