/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.os.Bundle;
import android.os.ResultReceiver;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;

import junit.framework.Assert;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowIntent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RichPushUserTest extends BaseTestCase {

    private final String fakeUserId = "fakeUserId";
    private final String fakeToken = "fakeToken";

    RichPushUser user;
    PreferenceDataStore dataStore;
    ShadowApplication application;
    TestUserListener listener;

    @Before
    public void setUp() {
        dataStore = TestApplication.getApplication().preferenceDataStore;
        user = new RichPushUser(TestApplication.getApplication(), dataStore);
        listener = new TestUserListener();
        user.addListener(listener);

        application = Shadows.shadowOf(RuntimeEnvironment.application);
        application.clearStartedServices();
    }

    /**
     * Test isCreated returns true when user has been created.
     */
    @Test
    public void testIsCreatedTrue() {
        user.setUser(fakeUserId, fakeToken);
        assertTrue("Should return true.", RichPushUser.isCreated());
    }

    /**
     * Test isCreated returns false when user has not been created.
     */
    @Test
    public void testIsCreatedFalse() {
        // Clear any user or user token
        user.setUser(null, null);
        assertFalse("Should return false.", RichPushUser.isCreated());
    }

    /**
     * Test isCreated returns false when user token doesn't exist.
     */
    @Test
    public void testIsCreatedFalseNoUserToken() {
        user.setUser(fakeUserId, null);
        assertFalse("Should return false.", RichPushUser.isCreated());
    }

    /**
     * Test setting and getting the user credentials.
     */
    @Test
    public void testUser() throws JSONException {
        user.setUser(fakeUserId, fakeToken);

        assertEquals("User ID should match", fakeUserId, user.getId());
        assertEquals("User password should match", fakeToken, user.getPassword());
    }

    /**
     * Test setting and getting the user credentials.
     */
    @Test
    public void testUserMissingId() throws JSONException {
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

        user = new RichPushUser(TestApplication.getApplication(), dataStore);

        assertEquals("User ID should match", fakeUserId, user.getId());
        assertEquals("User password should match", fakeToken, user.getPassword());

        assertNull(dataStore.getString("com.urbanairship.user.PASSWORD", null));
    }


    /**
     * Test update
     */
    @Test
    public void testUpdateUserFalse() throws InterruptedException {
        ShadowApplication application = Shadows.shadowOf(RuntimeEnvironment.application);
        application.clearStartedServices();

        user.update(false);

        ShadowIntent intent = Shadows.shadowOf(application.peekNextStartedService());
        Assert.assertEquals(intent.getAction(), InboxIntentHandler.ACTION_RICH_PUSH_USER_UPDATE);
    }

    /**
     * Tests update user starts the rich push service and notifies the listener
     * on a success result
     */
    @Test
    public void testRichPushUpdateSuccess() {
        // Update the user
        user.update(true);

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(InboxIntentHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(InboxIntentHandler.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());

        // Verify the listener received a success callback
        assertTrue("Listener should be notified of user update success.",
                listener.lastUpdateUserResult);
    }

    /**
     * Tests update user starts the rich push service and notifies the listener
     * on an error result
     */
    @Test
    public void testRichPushUpdateError() {
        // Update the user
        user.update(true);

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(InboxIntentHandler.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(InboxIntentHandler.STATUS_RICH_PUSH_UPDATE_ERROR, new Bundle());

        // Verify the listener received a success callback
        assertFalse("Listener should be notified of user update failed.",
                listener.lastUpdateUserResult);
    }

    /**
     * Listener that captures the last update user result
     */
    private class TestUserListener implements RichPushUser.Listener {
        Boolean lastUpdateUserResult = null;

        @Override
        public void onUserUpdated(boolean success) {
            lastUpdateUserResult = success;
        }
    }

}
