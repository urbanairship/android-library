/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.urbanairship.UALog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts the Message Center user token at rest. */
internal interface UserTokenCipher {

    /** Returns the encrypted form of [token], or `null` if it could not be encrypted. */
    fun encrypt(token: String): String?

    /** Returns the plaintext for [encrypted], or `null` if it could not be decrypted. */
    fun decrypt(encrypted: String): String?
}

/** [UserTokenCipher] backed by an AES-GCM key held in the platform `AndroidKeyStore`. */
internal class KeystoreUserTokenCipher : UserTokenCipher {

    private val lock = Any()

    override fun encrypt(token: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        val payload = ByteArray(1 + iv.size + ciphertext.size)
        payload[0] = iv.size.toByte()
        iv.copyInto(payload, 1)
        ciphertext.copyInto(payload, 1 + iv.size)

        Base64.encodeToString(payload, Base64.NO_WRAP)
    } catch (e: Exception) {
        UALog.e(e) { "Failed to encrypt Message Center user token!" }
        null
    }

    override fun decrypt(encrypted: String): String? = try {
        val payload = Base64.decode(encrypted, Base64.NO_WRAP)
        val ivSize = payload[0].toInt()

        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (e: Exception) {
        UALog.e(e) { "Failed to decrypt Message Center user token!" }
        null
    }

    private fun secretKey(): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()
    }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.urbanairship.messagecenter.user_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
