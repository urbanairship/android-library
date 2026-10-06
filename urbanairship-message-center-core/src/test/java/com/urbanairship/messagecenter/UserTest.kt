/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.channel.AirshipChannel
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class UserTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStore = PreferenceStore.inMemoryStore(context)
    private val mockChannel = mockk<AirshipChannel>()

    private val tokenCipher = TestUserTokenCipher()
    private val user = User(dataStore, tokenCipher)
    private val listener = TestUserListener()

    @Before
    public fun setUp() {
        user.addListener(listener)
    }

    /** Test isCreated returns true when user has been created. */
    @Test
    public fun testIsCreatedTrue() {
        user.setUser(UserCredentials(fakeUserId, fakeToken))
        assertTrue("Should return true.", user.isUserCreated)
    }

    /** Test isCreated returns false when user has not been created. */
    @Test
    public fun testIsCreatedFalse() {
        // Clear any user or user token
        user.setUser(null)
        assertFalse("Should return false.", user.isUserCreated)
    }

    /** Test isCreated returns false when user token doesn't exist. */
    @Test
    public fun testIsCreatedFalseNoUserToken() {
        user.setUser(UserCredentials(fakeUserId, ""))
        assertFalse("Should return false.", user.isUserCreated)
    }

    /** Test setting and getting the user credentials. */
    @Test
    public fun testUser() {
        user.setUser(UserCredentials(fakeUserId, fakeToken))
        assertEquals("User ID should match", fakeUserId, user.id)
        assertEquals("User password should match", fakeToken, user.password)
    }

    /** Test setting and getting the user credentials. */
    @Test
    public fun testUserMissingId() {
        user.setUser(UserCredentials("", fakeToken))
        assertNull(user.id)
        assertNull(user.password)
    }

    /** Test setting and getting the user credentials. */
    @Test
    public fun testUserMissingToken() {
        user.setUser(UserCredentials(fakeUserId, ""))
        assertNull(user.id)
        assertNull(user.password)
    }

    /** Test user token is encrypted when stored in preferences. */
    @Test
    public fun testUserTokenEncrypted() {
        user.setUser(UserCredentials(fakeUserId, fakeToken))

        val stored = requireNotNull(dataStore.get(userTokenKey))
        assertNotEquals("Token must not be stored in the clear", fakeToken, stored)
        assertTrue("Token should be marked as encrypted", stored.startsWith("v1:"))
        assertEquals(fakeToken, user.password)
    }

    /** Test the token is not recoverable by the legacy XOR decode once encrypted. */
    @Test
    public fun testUserTokenNotLegacyObfuscation() {
        user.setUser(UserCredentials(fakeUserId, fakeToken))

        val stored = requireNotNull(dataStore.get(userTokenKey))
        assertNotEquals(legacyXorHexEncode(fakeToken, fakeUserId), stored)
    }

    /** Test a token stored in the legacy XOR format is still readable and is upgraded in place. */
    @Test
    public fun testLegacyTokenMigratesToEncrypted() {
        dataStore.put(userIdKey, fakeUserId)
        dataStore.put(userTokenKey, legacyXorHexEncode(fakeToken, fakeUserId))

        assertEquals("Legacy token should still decode", fakeToken, user.password)

        val stored = requireNotNull(dataStore.get(userTokenKey))
        assertTrue("Legacy token should be re-stored encrypted", stored.startsWith("v1:"))
        assertEquals("Token should survive the upgrade", fakeToken, user.password)
    }

    /** Test a token that cannot be decrypted yields no password rather than garbage. */
    @Test
    public fun testUndecryptableTokenYieldsNullPassword() {
        user.setUser(UserCredentials(fakeUserId, fakeToken))
        tokenCipher.failDecrypt = true

        assertNull(user.password)
    }

    /** Test a token that cannot be encrypted is not persisted in the clear. */
    @Test
    public fun testUnencryptableTokenIsNotStored() {
        tokenCipher.failEncrypt = true
        user.setUser(UserCredentials(fakeUserId, fakeToken))

        assertNull(dataStore.get(userTokenKey))
        assertNull(user.password)
        assertFalse(user.isUserCreated)
    }

    /** Tests update user starts the rich push service and notifies the listener on success */
    @Test
    public fun testRichPushUpdateSuccess(): TestResult = runTest  {
        user.onUserUpdated(true)

        // Verify the listener received a success callback
        val result = requireNotNull(listener.lastUpdateUserResult)
        assertTrue("Listener should be notified of user update success.", result)
    }

    @Test
    public fun testUserUpdatedFlow(): TestResult = runTest  {
        user.userUpdated.test {
            user.onUserUpdated(true)
            assertEquals(true, awaitItem())

            user.onUserUpdated(false)
            assertEquals(false, awaitItem())

            user.onUserUpdated(false)
            ensureAllEventsConsumed()
        }
    }

    /** Tests update user starts the rich push service and notifies the listener on error */
    @Test
    public fun testRichPushUpdateError(): TestResult = runTest {
        user.onUserUpdated(false)

        // Verify the listener received a success callback
        val result = requireNotNull(listener.lastUpdateUserResult)
        assertFalse("Listener should be notified of user update failed.", result)
    }

    /** Tests if the user should be updated */
    @Test
    public fun testShouldUpdate() {
        every { mockChannel.id } returns "channel"
    }

    /** Tests if the user should not be updated */
    @Test
    public fun testShouldUpdateFalse() {
        // Set a channel ID
        every { mockChannel.id } returns fakeChannelId
        dataStore.put(SyncPrefKey.string("com.urbanairship.user.REGISTERED_CHANNEL_ID"), fakeChannelId)
    }

    /** Listener that captures the last update user result */
    private class TestUserListener : User.Listener {
        var lastUpdateUserResult: Boolean? = null

        override fun onUserUpdated(success: Boolean) {
            lastUpdateUserResult = success
        }
    }

    /** Reversible stand-in for the Keystore cipher, which isn't available under Robolectric. */
    private class TestUserTokenCipher(
        var failEncrypt: Boolean = false,
        var failDecrypt: Boolean = false
    ) : UserTokenCipher {

        override fun encrypt(token: String): String? = if (failEncrypt) {
            null
        } else {
            Base64.encodeToString(token.toByteArray(), Base64.NO_WRAP)
        }

        override fun decrypt(encrypted: String): String? = if (failDecrypt) {
            null
        } else {
            try {
                String(Base64.decode(encrypted, Base64.NO_WRAP))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    private companion object {
        private const val fakeUserId = "fakeUserId"
        private const val fakeToken = "fakeToken"
        private const val fakeChannelId = "fakeChannelId"

        private val userIdKey = SyncPrefKey.string("com.urbanairship.user.ID")
        private val userTokenKey = SyncPrefKey.string("com.urbanairship.user.USER_TOKEN")

        /** Produces the pre-encryption on-disk token format, so migration can be exercised. */
        private fun legacyXorHexEncode(input: String, key: String): String {
            val inputBytes = input.toByteArray()
            val keyBytes = key.toByteArray()
            val out = ByteArray(inputBytes.size)
            for (i in inputBytes.indices) {
                out[i] = (inputBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return buildString(out.size * 2) {
                for (b in out) append(String.format("%02x", b))
            }
        }
    }
}
