import Foundation
import Security
@testable import VehicleTracker

/// A refresh the test answers by hand. Every call is recorded and waits until
/// `answer` is called; from then on every call, waiting or new, gets that
/// answer, so a test that expected one call never hangs on a second.
@MainActor final class ScriptedRefresh {
    private(set) var presented: [String] = []
    private var reply: TokenRefresher.RefreshReply?
    private var waiting: [CheckedContinuation<TokenRefresher.RefreshReply, Never>] = []

    /// Answers every call at once with `reply`, if given.
    init(answering reply: TokenRefresher.RefreshReply? = nil) {
        self.reply = reply
    }

    nonisolated var call: TokenRefresher.Refresh {
        { [self] refreshToken in await receive(refreshToken) }
    }

    func answer(_ reply: TokenRefresher.RefreshReply) {
        self.reply = reply
        for continuation in waiting { continuation.resume(returning: reply) }
        waiting.removeAll()
    }

    private func receive(_ refreshToken: String) async -> TokenRefresher.RefreshReply {
        presented.append(refreshToken)
        if let reply { return reply }
        return await withCheckedContinuation { waiting.append($0) }
    }
}

/// An in-memory store that counts its reads and can be told to fail, like
/// the Keychain does when an item can't be read or written.
nonisolated final class ScriptedTokenStore: TokenStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var token: StoredToken?
    private var _loads = 0
    private var _failsLoad = false
    private var _failsSave = false

    init(_ initial: StoredToken? = nil) { token = initial }

    var loads: Int { lock.withLock { _loads } }
    var failsLoad: Bool {
        get { lock.withLock { _failsLoad } }
        set { lock.withLock { _failsLoad = newValue } }
    }
    var failsSave: Bool {
        get { lock.withLock { _failsSave } }
        set { lock.withLock { _failsSave = newValue } }
    }

    func load() throws -> StoredToken? {
        try lock.withLock {
            _loads += 1
            if _failsLoad { throw KeychainError.status(errSecInteractionNotAllowed) }
            return token
        }
    }

    func save(_ token: StoredToken) throws {
        try lock.withLock {
            if _failsSave { throw KeychainError.status(errSecInteractionNotAllowed) }
            self.token = token
        }
    }

    func clear() throws { lock.withLock { token = nil } }
}
