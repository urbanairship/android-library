/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.AirshipChannel;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class UserTest {

    private final String fakeUserId = "fakeUserId";
    private final String fakeToken = "fakeToken";
    private final String fakeChannelId = "fakeChannelId";

    private User user;
    private PreferenceDataStore dataStore;
    private TestUserListener listener;
    private AirshipChannel mockChannel;

    @Before
    public void setUp() {
        dataStore = new PreferenceDataStore(ApplicationProvider.getApplicationContext());
        mockChannel = Mockito.mock(AirshipChannel.class);
        user = new User(dataStore, mockChannel);

        listener = new TestUserListener();
        user.addListener(listener);
    }

    /**
     * Test isCreated returns true when user has been created.
     */
    @Test
    public void testIsCreatedTrue() {
        user.setUser(fakeUserId, fakeToken);
        assertTrue("Should return true.", user.isUserCreated());
    }

    /**
     * Test isCreated returns false when user has not been created.
     */
    @Test
    public void testIsCreatedFalse() {
        // Clear any user or user token
        user.setUser(null, null);
        assertFalse("Should return false.", user.isUserCreated());
    }

    /**
     * Test isCreated returns false when user token doesn't exist.
     */
    @Test
    public void testIsCreatedFalseNoUserToken() {
        user.setUser(fakeUserId, null);
        assertFalse("Should return false.", user.isUserCreated());
    }

    /**
     * Test setting and getting the user credentials.
     */
    @Test
    public void testUser() {
        user.setUser(fakeUserId, fakeToken);

        assertEquals("User ID should match", fakeUserId, user.getId());
        assertEquals("User password should match", fakeToken, user.getPassword());
    }

    /**
     * Test setting and getting the user credentials.
     */
    @Test
    public void testUserMissingId() {
        user.setUser(null, fakeToken);

        assertNull(user.getId());
        assertNull(user.getPassword());
    }

    /**
     * Test setting and getting the user credentials.
     */
    @Test
    public void testUserMissingToken() {
        user.setUser(fakeUserId, null);

        assertNull(user.getId());
        assertNull(user.getPassword());
    }

    /**
     * Test user token is obfuscated when stored in preferences.
     */
    @Test
    public void testUserTokenObfuscated() {
        user.setUser(fakeUserId, fakeUserId);

        assertNotEquals(fakeToken, dataStore.getString("com.urbanairship.user.USER_TOKEN", fakeToken));
    }

    /**
     * Test migrate old token storage.
     */
    @Test
    public void testMigrateToken() {
        dataStore.put("com.urbanairship.user.PASSWORD", fakeToken);
        dataStore.put("com.urbanairship.user.ID", fakeUserId);

        user = new User(dataStore, mockChannel);

        assertEquals("User ID should match", fakeUserId, user.getId());
        assertEquals("User password should match", fakeToken, user.getPassword());

        assertNull(dataStore.getString("com.urbanairship.user.PASSWORD", null));
    }

    /**
     * Tests update user starts the rich push service and notifies the listener
     * on a success result
     */
    @Test
    public void testRichPushUpdateSuccess() {
        user.onUserUpdated(true);

        // Verify the listener received a success callback
        assertTrue("Listener should be notified of user update success.", listener.lastUpdateUserResult);
    }

    /**
     * Tests update user starts the rich push service and notifies the listener
     * on an error result
     */
    @Test
    public void testRichPushUpdateError() {
        user.onUserUpdated(false);

        // Verify the listener received a success callback
        assertFalse("Listener should be notified of user update failed.",
                listener.lastUpdateUserResult);
    }

    /**
     * Tests if the user should be updated
     */
    @Test
    public void testShouldUpdate() {
        when(mockChannel.getId()).thenReturn("channel");
        assertTrue(user.shouldUpdate());
    }

    /**
     * Tests if the user should not be updated
     */
    @Test
    public void testShouldUpdateFalse() {
        // Set a channel ID
        when(mockChannel.getId()).thenReturn(fakeChannelId);
        dataStore.put("com.urbanairship.user.REGISTERED_CHANNEL_ID", fakeChannelId);
        assertFalse(user.shouldUpdate());
    }

    /**
     * Listener that captures the last update user result
     */
    private class TestUserListener implements User.Listener {

        Boolean lastUpdateUserResult = null;

        @Override
        public void onUserUpdated(boolean success) {
            lastUpdateUserResult = success;
        }

    }

}
