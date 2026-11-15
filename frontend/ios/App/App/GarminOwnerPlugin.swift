import Capacitor
import SwiftUI
import UIKit

#if DEBUG && GARMIN_PERSONAL_OWNER
@objc(GarminOwnerPlugin)
final class GarminOwnerPlugin: CAPPlugin, CAPBridgedPlugin {
    let identifier = "GarminOwnerPlugin"
    let jsName = "GarminOwner"
    let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "open", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "erase", returnType: CAPPluginReturnPromise)
    ]

    @objc func open(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let owner = self.localOwner(call), let parent = self.bridge?.viewController,
                  parent.presentedViewController == nil else {
                call.reject("GARMIN_OWNER_UNAVAILABLE")
                return
            }
            let panel = GarminOwnerPanel(vault: GarminOwnerVault(account: owner), english: call.getBool("english") ?? false)
            let host = UIHostingController(rootView: panel)
            host.modalPresentationStyle = .fullScreen
            parent.present(host, animated: true)
            call.resolve()
        }
    }

    @objc func erase(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let owner = self.localOwner(call) else {
                call.reject("GARMIN_OWNER_UNAVAILABLE")
                return
            }
            do {
                try GarminOwnerVault(account: owner).erase()
                call.resolve()
            } catch {
                call.reject("GARMIN_OWNER_ERASE_FAILED")
            }
        }
    }

    private func localOwner(_ call: CAPPluginCall) -> UUID? {
        guard let url = bridge?.webView?.url, url.scheme == "capacitor", url.host == "localhost",
              let raw = call.getString("owner") else { return nil }
        return UUID(uuidString: raw)
    }
}

private struct GarminOwnerPanel: View {
    let vault: GarminOwnerVault
    let english: Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scene
    @State private var input = ""
    @State private var remember = false
    @State private var hasSavedKey = false
    @State private var activities: [GarminActivitySummary] = []
    @State private var message = ""
    @State private var pending: Task<Void, Never>?
    @State private var confirmErase = false

    var body: some View {
        NavigationView {
            Group {
                if scene == .active {
                    Form {
                        Section(title("På denne enheten", "On this device")) {
                            Text(title("Privat utviklermodus for dine egne Garmin-data. Henting skjer bare når du trykker på knappen. Garmin mottar nøkkelen direkte. Weather Agent får ingen kopi.",
                                       "Private developer mode for your own Garmin data. Refresh runs when you request it. Garmin receives the key directly. Weather Agent receives no copy."))
                            SecureField(title("Garmin Personal API-nøkkel", "Garmin Personal API key"), text: $input)
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                            Toggle(title("Husk ny nøkkel på denne enheten", "Remember new key on this device"), isOn: $remember)
                            Button(title("Hent aktiviteter på nytt", "Refresh activities"), action: refresh)
                                .disabled(pending != nil || (input.isEmpty && !hasSavedKey))
                            if pending != nil { ProgressView() }
                            if hasSavedKey {
                                Button(title("Glem lagret nøkkel", "Forget saved key"), action: forgetKey).disabled(pending != nil)
                            }
                            if !message.isEmpty { Text(message).accessibilityIdentifier("garmin-status") }
                        }
                        Section(title("Krypterte aktiviteter", "Encrypted activities")) {
                            if activities.isEmpty { Text(title("Ingen lokale aktiviteter.", "No local activities.")) }
                            ForEach(activities) { activity in
                                VStack(alignment: .leading) {
                                    Text(activity.activityName ?? title("Aktivitet", "Activity"))
                                    if let date = activity.startTimeLocal { Text(date).font(.caption) }
                                    if let seconds = activity.duration { Text("\(Int(seconds / 60)) min").font(.caption) }
                                }
                            }
                            Button(title("Slett lokal nøkkel og aktiviteter", "Delete local key and activities"), role: .destructive) {
                                confirmErase = true
                            }.disabled(pending != nil)
                        }
                    }
                } else {
                    Color(uiColor: .systemBackground).ignoresSafeArea()
                }
            }
            .navigationTitle(title("Privat Garmin", "Private Garmin"))
            .toolbar { ToolbarItem(placement: .cancellationAction) {
                Button(title("Lukk", "Close")) { dismiss() }
            } }
            .confirmationDialog(title("Slette alle lokale Garmin-data?", "Delete all local Garmin data?"),
                                isPresented: $confirmErase, titleVisibility: .visible) {
                Button(title("Slett", "Delete"), role: .destructive, action: erase)
                Button(title("Avbryt", "Cancel"), role: .cancel) {}
            }
        }
        .navigationViewStyle(.stack)
        .onAppear(perform: load)
        .onDisappear(perform: cancel)
        .onChange(of: scene) { value in
            if value != .active { cancel(); activities = [] }
            else { load() }
        }
    }

    private func title(_ nb: String, _ en: String) -> String { english ? en : nb }

    private func load() {
        guard UIApplication.shared.isProtectedDataAvailable else { return }
        do {
            try vault.prepareInstallation()
            hasSavedKey = try vault.credential() != nil
            activities = try vault.activities()
        } catch {
            message = title("Lås opp enheten for å lese Garmin-data.", "Unlock the device to read Garmin data.")
        }
    }

    private func cancel() {
        pending?.cancel()
        pending = nil
        input = ""
    }

    private func refresh() {
        do {
            let entered = input.trimmingCharacters(in: .whitespacesAndNewlines)
            let shouldSave = !entered.isEmpty && remember
            let key = try entered.isEmpty ? vault.credential() : entered
            input = ""
            guard let key, GarminOwnerClient.validKey(key) else {
                message = title("Skriv inn en gyldig Garmin-nøkkel.", "Enter a valid Garmin key.")
                return
            }
            message = ""
            pending = Task { @MainActor in
                defer { if !Task.isCancelled { pending = nil } }
                do {
                    let fresh = try await GarminOwnerClient.refresh(key: key)
                    try Task.checkCancellation()
                    activities = try vault.merge(fresh)
                    if shouldSave {
                        try vault.rememberCredential(key)
                        hasSavedKey = true
                    }
                    message = title("Aktiviteter lagret på denne enheten.", "Activities saved on this device.")
                } catch GarminVaultError.rejected(let status) {
                    if !Task.isCancelled {
                        switch status {
                        case 401, 403: message = title("Garmin avviste nøkkelen. Kontroller den i Garmin Connect.", "Garmin rejected the key. Check it in Garmin Connect.")
                        case 429: message = title("Garmin ber deg vente før neste oppslag.", "Garmin is rate limiting requests. Try again later.")
                        default: message = title("Garmin svarte med en feil.", "Garmin returned an error.")
                        }
                    }
                } catch {
                    if !Task.isCancelled {
                        message = title("Henting eller kryptert lagring feilet. Bruk en enhet med kode og prøv igjen.", "Refresh or encrypted storage failed. Use a device with a passcode and try again.")
                    }
                }
            }
        } catch {
            message = title("Kunne ikke åpne nøkkellageret.", "Could not open secure key storage.")
        }
    }

    private func forgetKey() {
        do { try vault.forgetCredential(); hasSavedKey = false }
        catch { message = title("Nøkkelen kunne ikke slettes.", "The key could not be deleted.") }
    }

    private func erase() {
        do { try vault.erase(); hasSavedKey = false; activities = []; input = ""; message = "" }
        catch { message = title("Lokal sletting kunne ikke fullføres.", "Local deletion could not be completed.") }
    }
}
#endif
