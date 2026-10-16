package com.urbanairship.messagecenter

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class KeystoreUserTokenCipherTest {

    private val cipher = KeystoreUserTokenCipher()

    @After
    public fun tearDown() {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    @Test
    public fun testRoundTrip() {
        val encrypted = requireNotNull(cipher.encrypt(TOKEN))

        assertNotEquals(TOKEN, encrypted)
        assertEquals(TOKEN, cipher.decrypt(encrypted))
    }

    @Test
    public fun testCiphertextDoesNotContainToken() {
        val encrypted = requireNotNull(cipher.encrypt(TOKEN))

        assertTrue(
            "Ciphertext must not leak the token",
            !encrypted.contains(TOKEN)
        )
    }

    @Test
    public fun testEncryptionIsRandomized() {
        val first = requireNotNull(cipher.encrypt(TOKEN))
        val second = requireNotNull(cipher.encrypt(TOKEN))

        assertNotEquals("Each encryption should use a fresh IV", first, second)
        assertEquals(TOKEN, cipher.decrypt(first))
        assertEquals(TOKEN, cipher.decrypt(second))
    }

    @Test
    public fun testDecryptRejectsTamperedCiphertext() {
        val encrypted = requireNotNull(cipher.encrypt(TOKEN))

        val payload = Base64.decode(encrypted, Base64.NO_WRAP)
        payload[payload.size - 1] = (payload[payload.size - 1].toInt() xor 0xFF).toByte()

        assertNull(cipher.decrypt(Base64.encodeToString(payload, Base64.NO_WRAP)))
    }

    @Test
    public fun testDecryptRejectsGarbage() {
        assertNull(cipher.decrypt("not-a-valid-payload"))
    }

    @Test
    public fun testDecryptFailsAfterKeyIsLost() {
        val encrypted = requireNotNull(cipher.encrypt(TOKEN))
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS)

        assertNull(cipher.decrypt(encrypted))
    }

    private companion object {
        const val TOKEN = "fakeUserToken"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.urbanairship.messagecenter.user_token"
    }
}
