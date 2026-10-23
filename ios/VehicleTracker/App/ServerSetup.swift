import Foundation
import Observation
import OSLog

/// Takes setup links from the system and holds the alert each one leads to.
/// The phone window shows it; the CarPlay scene takes no links.
@MainActor @Observable
final class ServerSetup {
    /// The alert on screen, until the driver answers it.
    private(set) var prompt: ServerSetupLink.Decision?

    private let session: TripSession
    private let log = Logger(subsystem: "org.onebusaway.vehicletracker", category: "server-setup")

    init(session: TripSession) {
        self.session = session
    }

    func open(_ link: URL) {
        let signedInTo = session.phase == .signedOut ? nil : session.settings.serverURL
        let decision = ServerSetupLink.decide(link, signedInTo: signedInTo, isManaged: session.settings.serverIsManaged)
        switch decision {
        case .confirm: break
        case .refusedSignedIn: log.notice("setup link not used: signed in")
        case .refusedManaged: log.notice("setup link not used: the agency sets the server")
        case .invalid: log.notice("setup link not used: no server address the app may use")
        }
        prompt = decision
    }

    /// Fills in the sign-in screen's server. Checked again here, because the
    /// driver may have signed in, or the agency set the server, since the
    /// link arrived.
    func confirm(_ server: URL) {
        prompt = nil
        guard session.phase == .signedOut, !session.settings.serverIsManaged else {
            log.notice("setup link not used: signed in or managed before it was confirmed")
            return
        }
        session.settings.serverURLString = server.absoluteString
    }

    func dismiss() {
        prompt = nil
    }
}
