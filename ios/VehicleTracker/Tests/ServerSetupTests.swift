import Foundation
import Testing
@testable import VehicleTracker

@Suite struct ServerSetupTests {
    let api = FakeTrackerAPI()
    let defaults = UserDefaults(suiteName: "ServerSetupTests-\(UUID().uuidString)")!
    let link = URL(string: "obavehicletracker://setup?server=https%3A%2F%2Ftracker.example.org")!
    let linked = URL(string: "https://tracker.example.org")!
    let saved = URL(string: "https://positions.example.org")!

    func makeSession() -> TripSession {
        TripSession(settings: AppSettings(defaults: defaults), tokens: InMemoryTokenStore(),
                    store: InMemoryActiveTripStore(), locations: FakeLocationSource(), makeAPI: { _, _ in api })
    }

    @Test func confirmingFillsInTheServer() {
        let session = makeSession()
        let setup = ServerSetup(session: session)
        setup.open(link)
        #expect(setup.prompt == .confirm(server: linked, host: "tracker.example.org"))
        setup.confirm(linked)
        #expect(setup.prompt == nil)
        #expect(session.settings.serverURLString == "https://tracker.example.org")
    }

    @Test func cancellingLeavesTheServer() {
        defaults.set(saved.absoluteString, forKey: "serverURL")
        let session = makeSession()
        let setup = ServerSetup(session: session)
        setup.open(link)
        setup.dismiss()
        #expect(setup.prompt == nil)
        #expect(session.settings.serverURLString == saved.absoluteString)
    }

    @Test func aLinkWhileSignedInChangesNothing() async throws {
        let session = makeSession()
        try await session.signIn(serverURL: saved, email: "d@test.com", password: "pw")
        let setup = ServerSetup(session: session)
        setup.open(link)
        #expect(setup.prompt == .refusedSignedIn(host: "positions.example.org"))
        #expect(session.settings.serverURLString == saved.absoluteString)
    }

    @Test func aLinkWhileManagedChangesNothing() {
        defaults.set([AppSettings.managedServerURLKey: "https://managed.example.org"],
                     forKey: AppSettings.managedConfigurationKey)
        let session = makeSession()
        let setup = ServerSetup(session: session)
        setup.open(link)
        #expect(setup.prompt == .refusedManaged)
        #expect(session.settings.serverURLString == "")
    }

    @Test func confirmingAfterSigningInChangesNothing() async throws {
        let session = makeSession()
        let setup = ServerSetup(session: session)
        setup.open(link)
        try await session.signIn(serverURL: saved, email: "d@test.com", password: "pw")
        setup.confirm(linked)
        #expect(setup.prompt == nil)
        #expect(session.settings.serverURLString == saved.absoluteString)
    }

    @Test func confirmingAfterTheAgencySetsTheServerChangesNothing() async {
        let session = makeSession()
        let setup = ServerSetup(session: session)
        setup.open(link)
        defaults.set([AppSettings.managedServerURLKey: "https://managed.example.org"],
                     forKey: AppSettings.managedConfigurationKey)
        #expect(await eventually { session.settings.serverIsManaged })
        setup.confirm(linked)
        #expect(session.settings.serverURLString == "")
    }
}
