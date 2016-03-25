package com.urbanairship.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;


public class BaseIntentReceiverTest extends BaseTestCase {

    Context context;
    PushMessage pushMessage;

    @Before
    public void setup() {
        context = TestApplication.getApplication();

        Bundle bundle = new Bundle();
        bundle.putString(PushMessage.EXTRA_ALERT, "ALERT!");
        pushMessage = new PushMessage(bundle);
    }

    /**
     * Test parsing the ACTION_CHANNEL_UPDATED intent calls onChannelRegistrationSucceeded.
     */
    @Test
    public void testOnChannelRegistrationSucceeded() {
        TestReceiver receiver = new TestReceiver() {
            @Override
            protected void onChannelRegistrationSucceeded(Context context, String channelId) {
                super.onChannelRegistrationSucceeded(context, channelId);

                assertNotNull(context);
                assertEquals(channelId, "channel id");
            }
        };

        Intent intent = new Intent(PushManager.ACTION_CHANNEL_UPDATED)
                .putExtra(PushManager.EXTRA_CHANNEL_ID, "channel id");

        receiver.onReceive(context, intent);

        assertTrue("onChannelRegistrationSucceeded should be  called", receiver.onChannelRegistrationSucceededCalled);
    }

    /**
     * Test parsing the ACTION_CHANNEL_UPDATED intents does not call onChannelRegistrationSucceeded
     * if the channel ID is missing.
     */
    @Test
    public void testOnChannelRegistrationSucceededMissingChannelId() {
        TestReceiver receiver = new TestReceiver();
        Intent intent = new Intent(PushManager.ACTION_CHANNEL_UPDATED);

        receiver.onReceive(context, intent);

        assertFalse("onChannelRegistrationSucceeded should not be called", receiver.onChannelRegistrationSucceededCalled);
    }

    /**
     * Test parsing the ACTION_CHANNEL_UPDATED intent calls onChannelRegistrationFailed.
     */
    @Test
    public void testOnChannelRegistrationFailed() {
        TestReceiver receiver = new TestReceiver() {
            @Override
            protected void onChannelRegistrationFailed(Context context) {
                super.onChannelRegistrationFailed(context);
                assertNotNull(context);
            }
        };

        Intent intent = new Intent(PushManager.ACTION_CHANNEL_UPDATED)
                .putExtra(PushManager.EXTRA_ERROR, true);

        receiver.onReceive(context, intent);

        assertTrue("onChannelRegistrationFailed not called", receiver.onChannelRegistrationFailedCalled);
    }

    /**
     * Test parsing the ACTION_PUSH_RECEIVED intent calls onPushReceived.
     */
    @Test
    public void testOnPushReceived() {
        TestReceiver receiver = new TestReceiver() {
            @Override
            protected void onPushReceived(Context context, PushMessage message, int notificationId) {
                super.onPushReceived(context, message, notificationId);

                assertNotNull(context);
                assertEquals(pushMessage, message);
                assertEquals(notificationId, 101);
            }
        };

        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushMessage.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 101);

        receiver.onReceive(context, intent);

        assertTrue("onPushReceived not called", receiver.onPushReceivedCalled);
    }


    /**
     * Test parsing the ACTION_PUSH_RECEIVED intent calls onBackgroundPushReceived if it
     * does not contain the notification ID extra.
     */
    @Test
    public void testOnBackgroundPushReceived() {
        TestReceiver receiver = new TestReceiver() {
            @Override
            protected void onBackgroundPushReceived(Context context, PushMessage message) {
                super.onBackgroundPushReceived(context, message);

                assertNotNull(context);
                assertEquals(pushMessage, message);
            }
        };

        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushMessage.getPushBundle());

        receiver.onReceive(context, intent);

        assertTrue("onBackgroundPushReceived not called", receiver.onBackgroundPushReceivedCalled);
    }

    /**
     * Test parsing the ACTION_PUSH_RECEIVED does not call either onBackgroundPushReceived
     * or onPushReceived if its missing the push bundle.
     */
    @Test
    public void testActionPushReceivedMissingPushBundle() {
        TestReceiver receiver = new TestReceiver();
        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED);
        receiver.onReceive(context, intent);

        assertFalse("onBackgroundPushReceived should not be called", receiver.onBackgroundPushReceivedCalled);
        assertFalse("onPushReceived should not be called", receiver.onPushReceivedCalled);
    }

    /**
     * Test parsing the ACTION_NOTIFICATION_OPENED intent calls onNotificationOpened when the intent
     * does not contain notification button ID.
     */
    @Test
    public void testOnNotificationOpened() {
        BroadcastReceiver.PendingResult result = Mockito.mock(BroadcastReceiver.PendingResult.class);
        TestReceiver receiver = new TestReceiver() {
            @Override
            protected boolean onNotificationOpened(Context context, PushMessage message, int notificationId) {
                super.onNotificationOpened(context, message, notificationId);

                assertNotNull(context);
                assertEquals(pushMessage, message);
                assertEquals(notificationId, 100);

                return true;
            }
        };

        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushMessage.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 100);

        receiver.onReceive(context, intent);

        assertTrue("onNotificationOpened not called", receiver.onNotificationOpenedCalled);
    }

    /**
     * Test parsing the ACTION_NOTIFICATION_OPENED intent calls onNotificationActionOpened when the intent
     * contains a notification action button ID.
     */
    @Test
    public void testOnNotificationActionOpened() {
        TestReceiver receiver = new TestReceiver() {
            @Override
            protected boolean onNotificationActionOpened(Context context, PushMessage message, int notificationId, String buttonId, boolean isForeground) {
                super.onNotificationActionOpened(context, message, notificationId, buttonId, isForeground);

                assertNotNull(context);
                assertEquals(pushMessage, message);
                assertEquals(notificationId, 100);
                assertEquals(buttonId, "button id");
                assertTrue(isForeground);

                return true;
            }
        };

        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushMessage.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 100)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "button id")
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true);


        receiver.onReceive(context, intent);

        assertTrue("onNotificationActionOpened not called", receiver.onNotificationActionOpenedCalled);
    }

    /**
     * Test parsing the ACTION_NOTIFICATION_OPENED does not call either onNotificationOpened
     * or onNotificationActionOpened if its missing the push bundle.
     */
    @Test
    public void testActionPushOpenedMissingPushBundle() {
        TestReceiver receiver = new TestReceiver();
        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED);
        receiver.onReceive(context, intent);

        assertFalse("onNotificationOpened should not be called", receiver.onNotificationOpenedCalled);
        assertFalse("onNotificationActionOpened should not be called", receiver.onNotificationActionOpenedCalled);
    }

    /**
     * Test parsing the ACTION_NOTIFICATION_DISMISSED intent calls onNotificationDismissed.
     */
    @Test
    public void testOnNotificationDismissed() {
        TestReceiver receiver = new TestReceiver() {
            @Override
            protected void onNotificationDismissed(Context context, PushMessage message, int notificationId) {
                super.onNotificationDismissed(context, message, notificationId);

                assertNotNull(context);
                assertEquals(pushMessage, message);
                assertEquals(notificationId, 101);
            }
        };

        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_DISMISSED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushMessage.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 101);

        receiver.onReceive(context, intent);

        assertTrue("onNotificationDismissed not called", receiver.onNotificationDismissedCalled);
    }


    /**
     * Test class that tracks what callbacks have been called.
     */
    private static class TestReceiver extends BaseIntentReceiver {
        boolean onChannelRegistrationSucceededCalled = false;
        boolean onChannelRegistrationFailedCalled = false;
        boolean onPushReceivedCalled = false;
        boolean onBackgroundPushReceivedCalled = false;
        boolean onNotificationOpenedCalled = false;
        boolean onNotificationActionOpenedCalled = false;
        boolean onNotificationDismissedCalled = false;

        @Override
        protected void onChannelRegistrationSucceeded(Context context, String channelId) {
            onChannelRegistrationSucceededCalled = true;
        }

        @Override
        protected void onChannelRegistrationFailed(Context context) {
            onChannelRegistrationFailedCalled = true;
        }

        @Override
        protected void onPushReceived(Context context, PushMessage message, int notificationId) {
            onPushReceivedCalled = true;
        }

        @Override
        protected void onBackgroundPushReceived(Context context, PushMessage message) {
            onBackgroundPushReceivedCalled = true;
        }

        @Override
        protected boolean onNotificationOpened(Context context, PushMessage message, int notificationId) {
            onNotificationOpenedCalled = true;
            return true;
        }

        @Override
        protected boolean onNotificationActionOpened(Context context, PushMessage message, int notificationId, String buttonId, boolean isForeground) {
            onNotificationActionOpenedCalled = true;
            return true;
        }

        @Override
        protected void onNotificationDismissed(Context context, PushMessage message, int notificationId) {
            onNotificationDismissedCalled = true;
        }
    }
}
