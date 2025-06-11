/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.channel.AirshipChannel
import io.mockk.every
import io.mockk.mockk
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
    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val mockChannel = mockk<AirshipChannel>()

    private val user = User(dataStore)
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

    /** Test user token is obfuscated when stored in preferences. */
    @Test
    public fun testUserTokenObfuscated() {
        user.setUser(UserCredentials(fakeUserId, fakeUserId))
        assertNotEquals(
            fakeToken,
            dataStore.getString("com.urbanairship.user.USER_TOKEN", fakeToken)
        )
    }

    /** Test migrate old token storage. */
    @Test
    public fun testMigrateToken() {
        dataStore.put("com.urbanairship.user.PASSWORD", fakeToken)
        dataStore.put("com.urbanairship.user.ID", fakeUserId)

        val newUser = User(dataStore)
        assertEquals("User ID should match", fakeUserId, newUser.id)
        assertEquals("User password should match", fakeToken, newUser.password)
        assertNull(dataStore.getString("com.urbanairship.user.PASSWORD", null))
    }

    /** Tests update user starts the rich push service and notifies the listener on success */
    @Test
    public fun testRichPushUpdateSuccess() {
        user.onUserUpdated(true)

        // Verify the listener received a success callback
        val result = requireNotNull(listener.lastUpdateUserResult)
        assertTrue("Listener should be notified of user update success.", result)
    }

    /** Tests update user starts the rich push service and notifies the listener on error */
    @Test
    public fun testRichPushUpdateError() {
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
        dataStore.put("com.urbanairship.user.REGISTERED_CHANNEL_ID", fakeChannelId)
    }

    /** Listener that captures the last update user result */
    private class TestUserListener : User.Listener {
        var lastUpdateUserResult: Boolean? = null

        override fun onUserUpdated(success: Boolean) {
            lastUpdateUserResult = success
        }
    }

    private companion object {
        private const val fakeUserId = "fakeUserId"
        private const val fakeToken = "fakeToken"
        private const val fakeChannelId = "fakeChannelId"
    }
}
