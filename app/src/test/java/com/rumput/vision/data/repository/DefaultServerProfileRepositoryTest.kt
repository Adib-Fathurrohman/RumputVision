package com.rumput.vision.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rumput.vision.core.database.RumputDatabase
import com.rumput.vision.core.security.EncryptedCredentialStore
import com.rumput.vision.core.security.StoredCredentials
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.testing.FakeSecretCipher
import com.rumput.vision.testing.profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Uses a real in-memory Room database (via Robolectric) so the SQL is exercised. */
@RunWith(AndroidJUnit4::class)
class DefaultServerProfileRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: RumputDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RumputDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.repository(): Pair<DefaultServerProfileRepository, EncryptedCredentialStore> {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tmp.newFile("c.preferences_pb").also { it.delete() }
        }
        val store = EncryptedCredentialStore(dataStore, FakeSecretCipher())
        return DefaultServerProfileRepository(db.serverProfileDao(), store) to store
    }

    @Test
    fun `first profile becomes active, later ones do not`() = runTest {
        val (repo, _) = repository()
        repo.saveProfile(profile(id = "a"), PasswordUpdate.Keep)
        repo.saveProfile(profile(id = "b", name = "Cabin"), PasswordUpdate.Keep)

        assertEquals("a", repo.observeActiveProfile().first()?.id)
        assertEquals(listOf("a", "b"), repo.observeProfiles().first().map { it.id })
    }

    @Test
    fun `setActiveProfile switches the single active profile`() = runTest {
        val (repo, _) = repository()
        repo.saveProfile(profile(id = "a"), PasswordUpdate.Keep)
        repo.saveProfile(profile(id = "b"), PasswordUpdate.Keep)

        repo.setActiveProfile("b")

        val profiles = repo.observeProfiles().first()
        assertEquals(listOf(false, true), profiles.map { it.isActive })
    }

    @Test
    fun `setActiveProfile ignores unknown ids`() = runTest {
        val (repo, _) = repository()
        repo.saveProfile(profile(id = "a"), PasswordUpdate.Keep)
        repo.setActiveProfile("missing")
        assertEquals("a", repo.observeActiveProfile().first()?.id)
    }

    @Test
    fun `deleting the active profile activates the next one and removes its credentials`() = runTest {
        val (repo, store) = repository()
        repo.saveProfile(profile(id = "a", authMode = AuthMode.FRIGATE_LOGIN, local = "https://a.home.arpa", username = "u"), PasswordUpdate.Set("pw"))
        repo.saveProfile(profile(id = "b"), PasswordUpdate.Keep)
        assertTrue(repo.hasStoredPassword("a"))

        repo.deleteProfile("a")

        assertEquals("b", repo.observeActiveProfile().first()?.id)
        assertNull(store.read("a"))
        assertNull(repo.getProfile("a"))
    }

    @Test
    fun `password updates are applied to the credential store`() = runTest {
        val (repo, store) = repository()
        val p = profile(id = "a", authMode = AuthMode.FRIGATE_LOGIN, local = "https://a.home.arpa", username = "u")
        store.update("a") { StoredCredentials(accessToken = "old-token") }

        repo.saveProfile(p, PasswordUpdate.Set("pw"))
        assertEquals(StoredCredentials(password = "pw"), store.read("a"))

        store.update("a") { it?.copy(accessToken = "new-token") }
        repo.saveProfile(p, PasswordUpdate.Clear)
        assertEquals(StoredCredentials(accessToken = "new-token"), store.read("a"))

        repo.saveProfile(p, PasswordUpdate.ClearAll)
        assertNull(store.read("a"))
        assertFalse(repo.hasStoredPassword("a"))
    }

    @Test
    fun `urls round-trip through the database`() = runTest {
        val (repo, _) = repository()
        repo.saveProfile(profile(id = "a", local = "http://192.168.100.22", remote = "https://nvr.tail1234.ts.net"), PasswordUpdate.Keep)
        val loaded = repo.getProfile("a")!!
        assertEquals("http://192.168.100.22", loaded.localUrl?.value)
        assertEquals("https://nvr.tail1234.ts.net", loaded.remoteUrl?.value)
        assertTrue(loaded.usesCleartext)
    }
}
