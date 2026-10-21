package org.onebusaway.vehicletracker.data

import app.cash.turbine.test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64

/**
 * Exercises [EncryptedSessionStore] against a real file-backed DataStore and a [FakeCryptor],
 * covering the upgrade from the build that stored the driver's JWT in the clear. These run on the
 * JVM; `KeystoreCryptorTest` covers the parts that need a device.
 */
class SessionStoreTest {
    @get:Rule val tempFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After fun tearDown() = scopes.forEach { it.cancel() }

    /** DataStore allows one live instance per file, so every test gets its own file and scope. */
    private fun newDataStore(
        file: File = File(tempFolder.newFolder(), "session.preferences_pb"),
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Job() + Dispatchers.IO).also { scopes += it },
        produceFile = { file },
    )

    /** Writes the keys exactly as the build before the encrypted store did. */
    private suspend fun seedLegacySession(dataStore: DataStore<Preferences>, token: String = TOKEN) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("server_url")] = SERVER
            prefs[stringPreferencesKey("token")] = token
            prefs[longPreferencesKey("token_issued_at")] = ISSUED_AT
        }
    }

    @Test fun `a saved login reads back unchanged`() = runTest {
        val store = EncryptedSessionStore(newDataStore(), FakeCryptor())

        store.saveLogin(SERVER, TOKENS)

        assertEquals(Session(SERVER, TOKEN, ISSUED_AT, REFRESH_TOKEN, EXPIRES_AT), store.session.first())
    }

    @Test fun `neither token reaches the file in the clear`() = runTest {
        val file = File(tempFolder.newFolder(), "session.preferences_pb")
        val dataStore = newDataStore(file)

        EncryptedSessionStore(dataStore, FakeCryptor()).saveLogin(SERVER, TOKENS)

        val prefs = dataStore.data.first()
        assertNull("the plaintext key must not survive a save", prefs[LEGACY_TOKEN_KEY])
        assertNotEquals(TOKEN, prefs[TOKEN_KEY])
        assertEquals(FakeCryptor().encrypt(REFRESH_TOKEN), prefs[REFRESH_TOKEN_KEY])
        val onDisk = file.readText(Charsets.ISO_8859_1)
        assertFalse("the token appears verbatim in $file", onDisk.contains(TOKEN))
        assertFalse("the refresh token appears verbatim in $file", onDisk.contains(REFRESH_TOKEN))
    }

    @Test fun `clearing the token removes both tokens and their times, and keeps the server url`() = runTest {
        val dataStore = newDataStore()
        val store = EncryptedSessionStore(dataStore, FakeCryptor())
        store.saveLogin(SERVER, TOKENS)

        store.clearToken()

        assertEquals(Session(SERVER, null, null), store.session.first())
        assertEquals(setOf("server_url"), dataStore.data.first().asMap().keys.map { it.name }.toSet())
    }

    @Test fun `the session flow re-emits after each write`() = runTest {
        val store = EncryptedSessionStore(newDataStore(), FakeCryptor())

        store.session.test {
            assertEquals(Session(null, null, null), awaitItem())
            store.saveLogin(SERVER, TOKENS)
            assertEquals(Session(SERVER, TOKEN, ISSUED_AT, REFRESH_TOKEN, EXPIRES_AT), awaitItem())
            store.clearToken()
            assertEquals(Session(SERVER, null, null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a legacy plaintext token is encrypted and stays readable`() = runTest {
        val dataStore = newDataStore()
        seedLegacySession(dataStore)

        val session = EncryptedSessionStore(dataStore, FakeCryptor()).session.first()

        assertEquals("the driver must not be signed out by the upgrade", TOKEN, session.token)
        val prefs = dataStore.data.first()
        assertNull("the plaintext key must be gone afterwards", prefs[LEGACY_TOKEN_KEY])
        assertEquals(FakeCryptor().encrypt(TOKEN), prefs[TOKEN_KEY])
    }

    @Test fun `migration does not overwrite a token that is already encrypted`() = runTest {
        val dataStore = newDataStore()
        val cryptor = FakeCryptor()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("server_url")] = SERVER
            prefs[TOKEN_KEY] = cryptor.encrypt("newer-token")
            prefs[LEGACY_TOKEN_KEY] = "stale-token"
            prefs[longPreferencesKey("token_issued_at")] = ISSUED_AT
        }

        val session = EncryptedSessionStore(dataStore, cryptor).session.first()

        assertEquals("newer-token", session.token)
        assertNull(dataStore.data.first()[LEGACY_TOKEN_KEY])
    }

    @Test fun `migration is a no-op on a fresh install`() = runTest {
        val dataStore = newDataStore()

        val session = EncryptedSessionStore(dataStore, FakeCryptor()).session.first()

        assertEquals(Session(null, null, null), session)
        assertTrue("nothing should be written on a fresh install", dataStore.data.first().asMap().isEmpty())
    }

    @Test fun `a failed migration leaves the driver logged in`() = runTest {
        val dataStore = newDataStore()
        seedLegacySession(dataStore)

        val session = EncryptedSessionStore(dataStore, FakeCryptor().apply { failEncrypt = true }).session.first()

        // The driver keeps working on the plaintext token and the next launch retries.
        assertEquals(TOKEN, session.token)
        val prefs = dataStore.data.first()
        assertEquals("the legacy token must survive a failed migration", TOKEN, prefs[LEGACY_TOKEN_KEY])
        assertFalse("nothing partial should be committed", prefs.contains(TOKEN_KEY))
    }

    @Test fun `an undecryptable token reads as logged out`() = runTest {
        val dataStore = newDataStore()
        val cryptor = FakeCryptor()
        EncryptedSessionStore(dataStore, cryptor).saveLogin(SERVER, TOKENS)

        cryptor.failDecrypt = true
        val session = EncryptedSessionStore(dataStore, cryptor).session.first()

        // A null token is what sends the driver back to login.
        assertNull(session.token)
        assertFalse(session.hasFreshToken(ISSUED_AT))
        // Nothing is erased on a failure that may be transient.
        assertEquals(SERVER, session.serverUrl)
        assertTrue(dataStore.data.first().contains(TOKEN_KEY))
    }

    @Test fun `an undecryptable refresh token reads as none and leaves the access token readable`() = runTest {
        val dataStore = newDataStore()
        EncryptedSessionStore(dataStore, FakeCryptor()).saveLogin(SERVER, TOKENS)
        dataStore.edit { prefs -> prefs[REFRESH_TOKEN_KEY] = "written under a key that is gone" }

        val session = EncryptedSessionStore(dataStore, FakeCryptor()).session.first()

        assertNull(session.refreshToken)
        assertEquals(TOKEN, session.token)
        assertEquals(EXPIRES_AT, session.accessExpiresAtEpochSec)
    }

    @Test fun `a session saved by the build before refresh reads back with no refresh token and no expiry`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("server_url")] = SERVER
            prefs[TOKEN_KEY] = FakeCryptor().encrypt(TOKEN)
            prefs[longPreferencesKey("token_issued_at")] = ISSUED_AT
        }

        val session = EncryptedSessionStore(dataStore, FakeCryptor()).session.first()

        assertEquals(Session(SERVER, TOKEN, ISSUED_AT, refreshToken = null, accessExpiresAtEpochSec = null), session)
    }

    @Test fun `saveLogin writes nothing when encryption fails`() = runTest {
        val dataStore = newDataStore()
        val store = EncryptedSessionStore(dataStore, FakeCryptor().apply { failEncrypt = true })

        val failure = runCatching { store.saveLogin(SERVER, TOKENS) }.exceptionOrNull()

        assertTrue("expected a GeneralSecurityException, got $failure", failure is GeneralSecurityException)
        assertTrue("a half-written session is worse than none", dataStore.data.first().asMap().isEmpty())
    }

    @Test fun `an unchecked cryptor failure is not mistaken for a logged-out driver`() = runTest {
        val dataStore = newDataStore()
        seedLegacySession(dataStore)
        // The store's catches are narrow on purpose, so a Cryptor that breaks its contract
        // surfaces instead of being quietly read as "no token". Keeping real Keystore failures
        // inside that contract is KeystoreCryptor's job — it wraps every Keystore call, and
        // `normalizingKeystoreFailures` below covers the conversion itself.
        val cryptor = FakeCryptor().apply {
            failEncrypt = true
            encryptFailure = ProviderException("Failed to communicate with keystore service")
        }

        val failure = runCatching {
            EncryptedSessionStore(dataStore, cryptor).session.first()
        }.exceptionOrNull()

        assertTrue("expected the ProviderException to surface, got $failure", failure is ProviderException)
    }

    @Test fun `an unchecked keystore failure is reported as a GeneralSecurityException`() {
        val failure = runCatching {
            normalizingKeystoreFailures("boom") { throw ProviderException("Keystore key generation failed") }
        }.exceptionOrNull()

        assertTrue("expected a GeneralSecurityException, got $failure", failure is GeneralSecurityException)
        assertTrue("the cause must survive for the log", failure?.cause is ProviderException)
    }

    @Test fun `normalizing leaves other failures alone so the retry still sees them`() {
        val original = GeneralSecurityException("unreadable")

        val failure = runCatching { normalizingKeystoreFailures("boom") { throw original } }.exceptionOrNull()

        assertSame(original, failure)
    }

    @Test fun `an unreadable session file reads as logged out`() = runTest {
        val store = EncryptedSessionStore(FailingDataStore(IOException("session file is gone")), FakeCryptor())

        assertEquals(Session(null, null, null), store.session.first())
    }

    @Test fun `a non-IO storage failure is not swallowed`() = runTest {
        val store = EncryptedSessionStore(FailingDataStore(IllegalStateException("a bug, not a bad disk")), FakeCryptor())

        val failure = runCatching { store.session.first() }.exceptionOrNull()

        assertTrue("expected an IllegalStateException, got $failure", failure is IllegalStateException)
    }

    @Test fun `hasFreshToken holds one second before 24h and lapses at 24h`() {
        val session = Session(SERVER, TOKEN, ISSUED_AT)

        assertTrue(session.hasFreshToken(ISSUED_AT + 24 * 3600 - 1))
        assertFalse(session.hasFreshToken(ISSUED_AT + 24 * 3600))
    }

    /** Stands in for storage that cannot be read at all, so the failure paths are reachable. */
    private class FailingDataStore(private val failure: Throwable) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw failure
    }

    private companion object {
        const val SERVER = "https://transit.example.org/"
        const val TOKEN = "eyJhbGciOiJIUzI1NiJ9.driver42.signature"
        const val ISSUED_AT = 1_785_886_200L
        const val REFRESH_TOKEN = "3f9a17c0d2e84b65a1f0c9e7b3d24a68"
        const val EXPIRES_AT = ISSUED_AT + 24 * 3600
        val TOKENS = IssuedTokens(TOKEN, REFRESH_TOKEN, EXPIRES_AT, ISSUED_AT)

        val TOKEN_KEY = stringPreferencesKey("token_enc")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token_enc")
        val LEGACY_TOKEN_KEY = stringPreferencesKey("token")
    }

    @Test fun `the driver id is the sub of the server's token`() {
        assertEquals("2", Session("http://localhost/", jwtFor("2"), 0L).driverId)
        assertNull(Session("http://localhost/", null, null).driverId)
    }

    @Test fun `a token that is not a readable JWT has no driver id`() {
        val noSub = "e30." + Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1}""".toByteArray()) + ".sig"
        assertNull(jwtSubject("jwt"))
        assertNull(jwtSubject("e30.!!!.sig"))
        assertNull(jwtSubject("e30.WzFd.sig")) // [1]: JSON, but not an object
        assertNull(jwtSubject(noSub))
    }
}
