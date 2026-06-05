package com.atomik.quickbeep.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptographyManager {
    private const val KEY_ALIAS = "AtomikQuickBeepSecretKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Mathematically bind the key to user authentication (fingerprint/face/lockscreen credentials)
            .setUserAuthenticationRequired(true)
            // Require authentication validity window of 300 seconds (5 minutes)
            // Once user authenticates (e.g. at launch), key can be used for 5 minutes.
            .setUserAuthenticationValidityDurationSeconds(300)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts a raw byte array using AES-GCM-256.
     * Prepends the 4-byte IV size and the variable-length IV to the cipher payload.
     */
    fun encryptData(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        
        val buffer = ByteBuffer.allocate(4 + iv.size + ciphertext.size)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    /**
     * Decrypts an encrypted byte array using AES-GCM-256.
     * Extracts the IV from the prepended header.
     */
    fun decryptData(encryptedData: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(encryptedData)
        val ivSize = buffer.int
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts a string value and encodes it in Base64 for database storage.
     */
    fun encryptString(value: String): String {
        if (value.isEmpty()) return value
        val cipherBytes = encryptData(value.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(cipherBytes, android.util.Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded GCM payload back to its original string format.
     */
    fun decryptString(value: String): String {
        if (value.isEmpty()) return value
        val cipherBytes = android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
        return String(decryptData(cipherBytes), Charsets.UTF_8)
    }
}
