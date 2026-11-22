package com.rumput.vision.core.security

import android.content.Context
import android.security.keystore.KeyInfo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.SecretKeyFactory

/**
 * Runs against the real Android Keystore on a device or emulator: the JVM tests use a fake cipher.
 * Uses the app's key alias, so it shares the key with an installed debug build (as the app would).
 */
@RunWith(AndroidJUnit4::class)
class KeystoreCredentialStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cipher = KeystoreSecretCipher(context)
    private val aad = "profile-a".toByteArray()

    private lateinit var dir: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "keystore-test-${System.nanoTime()}").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun roundTripsAndRandomizesIv() {
        val plaintext = "s3cret-pässword".toByteArray()
        val first = cipher.encrypt(plaintext, aad)
        val second = cipher.encrypt(plaintext, aad)

        assertArrayEquals(plaintext, cipher.decrypt(first, aad))
        assertArrayEquals(plaintext, cipher.decrypt(second, aad))
        assertFalse("same IV and ciphertext twice", first.contentEquals(second))
        assertFalse("plaintext visible in blob", String(first, Charsets.ISO_8859_1).contains("s3cret"))
    }

    @Test
    fun rejectsWrongProfileTamperingAndUnknownFormat() {
        val blob = cipher.encrypt("password".toByteArray(), aad)

        assertThrows(SecretCipherException::class.java) { cipher.decrypt(blob, "profile-b".toByteArray()) }
        val tampered = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertThrows(SecretCipherException::class.java) { cipher.decrypt(tampered, aad) }
        val otherVersion = blob.copyOf().also { it[0] = 9 }
        assertThrows(SecretCipherException::class.java) { cipher.decrypt(otherVersion, aad) }
    }

    @Test
    fun keyIsNonExportableKeystoreAesKey() {
        cipher.encrypt(byteArrayOf(1), aad) // creates the key if needed
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (keyStore.getEntry("rumput_vision_credentials_v1", null) as KeyStore.SecretKeyEntry).secretKey

        assertEquals("AES", key.algorithm)
        assertNull("key material must not be exportable", key.encoded)
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(256, info.keySize)
        assertFalse(info.isUserAuthenticationRequired)
    }

    @Test
    fun credentialStorePersistsEncryptedAndBindsToProfile() = runBlocking {
        val file = File(dir, "creds.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val store = EncryptedCredentialStore(dataStore, cipher)

        store.update("p1") { StoredCredentials(password = "hunter2-secret", accessToken = "token-abc") }

        // A fresh instance (no cache) decrypts from disk.
        val reread = EncryptedCredentialStore(dataStore, cipher).read("p1")
        assertEquals("hunter2-secret", reread?.password)
        assertEquals("token-abc", reread?.accessToken)

        // Nothing secret is stored in plain text.
        val onDisk = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(onDisk.contains("hunter2"))
        assertFalse(onDisk.contains("token-abc"))

        // A blob copied to another profile fails authentication and is discarded.
        val blob = dataStore.data.first()[stringPreferencesKey("credentials.p1")]
        assertNotNull(blob)
        dataStore.edit { it[stringPreferencesKey("credentials.p2")] = blob!! }
        assertNull(EncryptedCredentialStore(dataStore, cipher).read("p2"))
        assertTrue(dataStore.data.first()[stringPreferencesKey("credentials.p2")] == null)

        // Clearing every secret deletes the entry.
        store.update("p1") { null }
        assertNull(EncryptedCredentialStore(dataStore, cipher).read("p1"))
        assertNull(dataStore.data.first()[stringPreferencesKey("credentials.p1")])
    }
}
