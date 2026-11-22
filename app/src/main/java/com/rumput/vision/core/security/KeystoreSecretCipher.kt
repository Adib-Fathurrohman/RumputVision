package com.rumput.vision.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM with a non-exportable Android Keystore key (StrongBox when available).
 *
 * Blob format: [version:1][iv:12][ciphertext+tag].
 */
@Singleton
class KeystoreSecretCipher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SecretCipher {

    private val lock = Any()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        check(iv.size == IV_LENGTH) { "Unexpected IV length" }
        byteArrayOf(FORMAT_VERSION) + iv + cipher.doFinal(plaintext)
    } catch (e: GeneralSecurityException) {
        throw SecretCipherException("Encryption failed", e)
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size <= 1 + IV_LENGTH || ciphertext[0] != FORMAT_VERSION) {
            throw SecretCipherException("Unsupported ciphertext format")
        }
        return try {
            val existingKey = existingKey() ?: throw SecretCipherException("Key not available")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey, GCMParameterSpec(TAG_LENGTH_BITS, ciphertext, 1, IV_LENGTH))
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext, 1 + IV_LENGTH, ciphertext.size - 1 - IV_LENGTH)
        } catch (e: GeneralSecurityException) {
            // Includes AEADBadTagException and KeyPermanentlyInvalidatedException.
            throw SecretCipherException("Decryption failed", e)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = (keyStore().getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun key(): SecretKey = synchronized(lock) { existingKey() ?: generateKey() }

    private fun generateKey(): SecretKey {
        val wantStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        return if (wantStrongBox) {
            try {
                generateKey(strongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                generateKey(strongBox = false)
            }
        } else {
            generateKey(strongBox = false)
        }
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rumput_vision_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
