/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.richpush;

import android.os.Bundle;
import android.os.ResultReceiver;

import com.urbanairship.TestApplication;

import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowIntent;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RichPushManagerTest extends RichPushBaseTestCase {

    RichPushUser user;
    ShadowApplication application;
    RichPushManager manager;

    RichPushManagerTestListener listener;

    @Override
    public void setUp() {
        super.setUp();

        this.listener = new RichPushManagerTestListener();
        manager = new RichPushManager(TestApplication.getApplication().preferenceDataStore);
        manager.addListener(listener);

        user = manager.getRichPushUser();

        application = Robolectric.shadowOf(Robolectric.application);
        application.clearStartedServices();
    }

    /**
     * Test isRichPushMessage with extras
     */
    @Test
    public void testIsRichPushMessageExtras() {
        Map<String, String> extras = new HashMap<>();
        extras.put("_uamid", "si");
        assertTrue(RichPushManager.isRichPushMessage(extras));

        extras.remove("_uamid");
        assertFalse(RichPushManager.isRichPushMessage(extras));
    }

    /**
     * Test isRichPushMessage with bundle
     */
    @Test
    public void testIsRichPushMessageBundle() {
        Bundle extras = new Bundle();
        extras.putString("_uamid", "si");
        assertTrue(RichPushManager.isRichPushMessage(extras));

        extras.remove("_uamid");
        assertFalse(RichPushManager.isRichPushMessage(extras));
    }

    /**
     * Test updateUserIfNecessary
     */
    @Test
    public void testUpdateUserIfNecessary() throws InterruptedException {
        // set last update time to be 25 hours ago
        long lastUpdateTime = System.currentTimeMillis() - (25 * 60 * 60 * 1000);
        user.setLastUpdateTime(lastUpdateTime);

        ShadowApplication application = Robolectric.shadowOf(Robolectric.application);
        application.clearStartedServices();

        manager.updateUserIfNecessary();

        ShadowIntent intent = Robolectric.shadowOf(application.peekNextStartedService());
        assertEquals(intent.getAction(), RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE);
    }

    /**
     * Test updateUserIfNecessary not needed
     */
    @Test
    public void testUpdateUserNotNecessary() throws InterruptedException {
        // set last update time to 1 hour ago
        long lastUpdateTime = System.currentTimeMillis() - (1 * 60 * 60 * 1000);
        user.setLastUpdateTime(lastUpdateTime);

        ShadowApplication application = Robolectric.shadowOf(Robolectric.application);
        application.clearStartedServices();

        manager.updateUserIfNecessary();
        assertNull(application.peekNextStartedService());
    }

    /**
     * Tests update user starts the rich push service and notifies the listener
     * on a success result
     */
    @Test
    public void testRichPushUpdateSuccess() {
        user.setLastUpdateTime(0);

        // Update the user
        manager.updateUser();

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());

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
        user.setLastUpdateTime(0);

        // Update the user
        manager.updateUser();

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR, new Bundle());

        // Verify the error result does not change the last update time
        assertTrue(0 == user.getLastUpdateTime());

        // Verify the listener received a success callback
        assertFalse("Listener should be notified of user update failed.",
                listener.lastUpdateUserResult);
    }

    /**
     * Test refresh messages on success
     */
    @Test
    public void testRefreshMessagesSuccess() {
        manager.refreshMessages();
        assertTrue(manager.isRefreshingMessages());

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());


        // Verify the listener received a success callback
        assertTrue("Listener should be notified of user refresh success.",
                listener.lastUpdateMessageResult);

        assertFalse(manager.isRefreshingMessages());
    }

    /**
     * Test refresh messages on error
     */
    @Test
    public void testRefreshMessagesError() {
        // Add a listener
        RichPushManagerTestListener listener = new RichPushManagerTestListener();
        manager.addListener(listener);

        manager.refreshMessages();
        assertTrue(manager.isRefreshingMessages());

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR, new Bundle());


        // Verify the listener received a success callback
        assertFalse("Listener should be notified of user refresh failure.",
                listener.lastUpdateMessageResult);

        assertFalse(manager.isRefreshingMessages());
    }

    /**
     * Test refreshing messages skips triggering the rich push service if already
     * refreshing.
     */
    @Test
    public void testRefreshMessagesAlreadyRefreshing() {
        // Start refreshing messages
        manager.refreshMessages(false);
        assertTrue(manager.isRefreshingMessages());


        // Try to refresh again
        manager.refreshMessages(false);
        assertTrue(manager.isRefreshingMessages());
        assertNull("Should not refresh messages again",
                application.peekNextStartedActivity());
    }

    /**
     * Test refreshing messages multiple times waits to notify
     * that refresh is complete til the last call has a response.
     */
    @Test
    public void testRefreshMessageResponse() {
        // Start refreshing messages
        manager.refreshMessages(true);
        assertTrue(manager.isRefreshingMessages());

        // Force it
        manager.refreshMessages(true);
        assertTrue(manager.isRefreshingMessages());

        // Send result with dated first receiver
        ResultReceiver firstReceiver = application.getNextStartedService()
                                                  .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);

        firstReceiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());
        assertTrue("First receiver is out of date and should not affect isRefreshing",
                manager.isRefreshingMessages());
        assertNull("First receiver should not notify listeners", listener.lastUpdateMessageResult);

        // Send result with last receiver
        ResultReceiver lastReceiver = application.getNextStartedService()
                                                 .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);

        lastReceiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());
        assertFalse(manager.isRefreshingMessages());
        assertTrue(listener.lastUpdateMessageResult);
    }

    /**
     * Test refresh messages with a callback
     */
    @Test
    public void testRefreshMessagesWithCallback() {
        RichPushManager.RefreshMessagesCallback callback = mock(RichPushManager.RefreshMessagesCallback.class);
        manager.refreshMessages(callback);

        assertTrue(manager.isRefreshingMessages());

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());

        verify(callback).onRefreshMessages(true);
    }

    /**
     * Test failed refresh message request with a callback
     */
    @Test
    public void testRefreshMessagesFailWithCallback() {
        RichPushManager.RefreshMessagesCallback callback = mock(RichPushManager.RefreshMessagesCallback.class);
        manager.refreshMessages(callback);

        assertTrue(manager.isRefreshingMessages());

        // Send result to the receiver
        ResultReceiver receiver = application.peekNextStartedService()
                                             .getParcelableExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER);
        receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR, new Bundle());

        verify(callback).onRefreshMessages(false);
    }

    /**
     * Listener that captures the last update user and update message results
     */
    private class RichPushManagerTestListener implements RichPushManager.Listener {
        Boolean lastUpdateUserResult = null;
        Boolean lastUpdateMessageResult = null;

        @Override
        public void onUpdateMessages(boolean success) {
            lastUpdateMessageResult = success;
        }

        @Override
        public void onUpdateUser(boolean success) {
            lastUpdateUserResult = success;
        }
    }
}
