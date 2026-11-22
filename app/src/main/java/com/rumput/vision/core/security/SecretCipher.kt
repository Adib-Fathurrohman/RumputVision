package com.rumput.vision.core.security

/** Authenticated encryption for small secrets. Implementations must fail closed. */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /** @throws SecretCipherException if the data was tampered with or the key is unavailable. */
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

class SecretCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)
