package com.rumput.vision.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rumput.vision.testing.FakeSecretCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedCredentialStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cipher = FakeSecretCipher()

    private fun TestScope.newDataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        tmp.newFile("creds.preferences_pb").also { it.delete() }
    }

    @Test
    fun `round-trips credentials and never stores plaintext`() = runTest {
        val dataStore = newDataStore()
        val store = EncryptedCredentialStore(dataStore, cipher)

        store.update("p1") { StoredCredentials(password = "hunter2", accessToken = "jwt-token") }

        assertEquals(StoredCredentials(password = "hunter2", accessToken = "jwt-token"), store.read("p1"))
        val raw = dataStore.data.first().asMap().values.joinToString()
        assertFalse(raw.contains("hunter2"))
        assertFalse(raw.contains("jwt-token"))
    }

    @Test
    fun `update transforms existing credentials and empty result deletes them`() = runTest {
        val dataStore = newDataStore()
        val store = EncryptedCredentialStore(dataStore, cipher)
        store.update("p1") { StoredCredentials(password = "a", accessToken = "t") }

        store.update("p1") { it?.copy(password = null) }
        assertEquals(StoredCredentials(accessToken = "t"), store.read("p1"))

        store.update("p1") { it?.copy(accessToken = null) }
        assertNull(store.read("p1"))
        assertEquals(0, dataStore.data.first().asMap().size)
    }

    @Test
    fun `a blob moved to another profile fails closed and is removed`() = runTest {
        val dataStore = newDataStore()
        val store = EncryptedCredentialStore(dataStore, cipher)
        store.update("p1") { StoredCredentials(password = "hunter2") }
        val blob = dataStore.data.first()[stringPreferencesKey("credentials.p1")]!!
        dataStore.edit { it[stringPreferencesKey("credentials.p2")] = blob }

        assertNull(store.read("p2"))
        assertNull(dataStore.data.first()[stringPreferencesKey("credentials.p2")])
        assertEquals("hunter2", store.read("p1")?.password)
    }

    @Test
    fun `undecryptable data (lost keystore key) is discarded`() = runTest {
        val dataStore = newDataStore()
        val store = EncryptedCredentialStore(dataStore, cipher)
        store.update("p1") { StoredCredentials(password = "hunter2") }

        cipher.failAll = true
        assertNull(store.read("p1"))
        cipher.failAll = false
        assertNull(store.read("p1"))
    }

    @Test
    fun `garbage base64 is treated as missing`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[stringPreferencesKey("credentials.p1")] = "not base64 !!" }
        assertNull(EncryptedCredentialStore(dataStore, cipher).read("p1"))
    }

    @Test
    fun `toString masks secrets`() {
        val text = StoredCredentials(password = "hunter2", accessToken = "jwt").toString()
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("jwt,"))
    }
}
