package com.urbanairship;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class AirshipReceiverTest extends BaseTestCase {

    private Context context;
    private Bundle pushBundle;
    private Bundle remoteInput;
    private int callbackCount;
    private AirshipReceiver receiver;

    @Before
    public void setup() {
        callbackCount = 0;
        context = TestApplication.getApplication();

        pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_ALERT, "ALERT!");

        remoteInput = new Bundle();
        remoteInput.putString("COOL", "STORY");
    }

    /**
     * Test parsing the ACTION_CHANNEL_UPDATED intent calls onChannelRegistrationSucceeded.
     */
    @Test
    public void testOnChannelRegistrationSucceeded() {
        Intent intent = new Intent(PushManager.ACTION_CHANNEL_UPDATED)
                .putExtra(PushManager.EXTRA_CHANNEL_ID, "channel id");

        receiver = new AirshipReceiver() {
            @Override
            protected void onChannelRegistrationSucceeded(@NonNull Context context, @NonNull String channelId) {
                callbackCount++;
                assertNotNull(context);
                assertEquals("channel id", channelId);
            }
        };

        receiver.onReceive(context, intent);
        assertEquals(1, callbackCount);
    }

    /**
     * Test parsing the ACTION_CHANNEL_UPDATED intents does not call onChannelRegistrationSucceeded
     * if the channel ID is missing.
     */
    @Test
    public void testOnChannelRegistrationSucceededMissingChannelId() {
        Intent intent = new Intent(PushManager.ACTION_CHANNEL_UPDATED);

        receiver = new AirshipReceiver() {
            @Override
            protected void onChannelRegistrationSucceeded(@NonNull Context context, @NonNull String channelId) {
                fail();
            }
        };

        receiver.onReceive(context, intent);
    }

    /**
     * Test parsing the ACTION_CHANNEL_UPDATED intent calls onChannelRegistrationFailed.
     */
    @Test
    public void testOnChannelRegistrationFailed() {
        Intent intent = new Intent(PushManager.ACTION_CHANNEL_UPDATED)
                .putExtra(PushManager.EXTRA_ERROR, true);

        receiver = new AirshipReceiver() {
            @Override
            protected void onChannelRegistrationFailed(@NonNull Context context) {
                callbackCount++;
                assertNotNull(context);
            }
        };

        receiver.onReceive(context, intent);
        assertEquals(1, callbackCount);
    }

    /**
     * Test parsing the ACTION_PUSH_RECEIVED intent calls onNotificationPosted and onPushReceived if
     * the bundle contains a notification ID.
     */
    @Test
    public void testOnNotificationPosted() {
        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 101);

        receiver = new AirshipReceiver() {

            @Override
            protected void onPushReceived(@NonNull Context context, @NonNull PushMessage message, boolean notificationPosted) {
                callbackCount++;

                assertEquals(pushBundle, message.getPushBundle());
                assertTrue(notificationPosted);
            }

            @Override
            protected void onNotificationPosted(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {
                callbackCount++;

                assertNotNull(context);
                assertEquals(pushBundle, notificationInfo.getMessage().getPushBundle());
                assertEquals(101, notificationInfo.getNotificationId());
            }
        };

        receiver.onReceive(context, intent);
        assertEquals(2, callbackCount);
    }

    /**
     * Test parsing the ACTION_PUSH_RECEIVED intent calls only onPushReceived if
     * the bundle does not contain a notification ID.
     */
    @Test
    public void testOnPushReceived() {
        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle);

        receiver = new AirshipReceiver() {

            @Override
            protected void onPushReceived(@NonNull Context context, @NonNull PushMessage message, boolean notificationPosted) {
                callbackCount++;

                assertEquals(pushBundle, message.getPushBundle());
                assertFalse(notificationPosted);
            }

            @Override
            protected void onNotificationPosted(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {
                fail();
            }
        };

        receiver.onReceive(context, intent);
        assertEquals(1, callbackCount);
    }

    /**
     * Test parsing the ACTION_PUSH_RECEIVED does not call either onBackgroundPushReceived
     * or onNotificationPosted if its missing the push bundle.
     */
    @Test
    public void testActionPushReceivedMissingPushBundle() {
        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED);

        receiver = new AirshipReceiver() {
            @Override
            protected void onPushReceived(@NonNull Context context, @NonNull PushMessage message, boolean notificationPosted) {
                fail();
            }

            @Override
            protected void onNotificationPosted(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {
                fail();
            }
        };

        receiver.onReceive(context, intent);
    }

    /**
     * Test parsing the ACTION_NOTIFICATION_OPENED intent calls onNotificationOpened when the intent
     * does not contain notification button ID.
     */
    @Test
    public void testOnNotificationOpened() {
        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 100);

        receiver = new AirshipReceiver() {
            @Override
            protected boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {
                callbackCount++;

                assertNotNull(context);
                assertEquals(pushBundle, notificationInfo.getMessage().getPushBundle());
                assertEquals(100, notificationInfo.getNotificationId());
                return true;
            }
        };

        receiver.onReceive(context, intent);
        assertEquals(1, callbackCount);
    }

    /**
     * Test parsing the ACTION_NOTIFICATION_OPENED intent calls onNotificationOpened with the button info
     * when the intent contains a notification action button ID.
     */
    @Test
    public void testOnNotificationActionOpened() {
        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 100)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "button id")
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true)
                .putExtra(AirshipReceiver.EXTRA_REMOTE_INPUT, remoteInput);


        receiver = new AirshipReceiver() {
            @Override
            protected boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo, @NonNull ActionButtonInfo buttonInfo) {
                callbackCount++;

                assertNotNull(context);
                assertEquals(pushBundle, notificationInfo.getMessage().getPushBundle());
                assertEquals(100, notificationInfo.getNotificationId());
                assertEquals("button id", buttonInfo.getButtonId());
                assertEquals(remoteInput, buttonInfo.getRemoteInput());
                assertTrue(buttonInfo.isForeground());
                return true;
            }
        };

        receiver.onReceive(context, intent);
        assertEquals(1, callbackCount);
    }

    /**
     * Test parsing the ACTION_NOTIFICATION_OPENED does not call either onNotificationOpened
     * or onNotificationActionOpened if its missing the push bundle.
     */
    @Test
    public void testActionPushOpenedMissingPushBundle() {
        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_OPENED);

        receiver = new AirshipReceiver() {
            @Override
            protected boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {
                fail();
                return true;
            }

            @Override
            protected boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo, @NonNull ActionButtonInfo buttonInfo) {
                fail();
                return true;
            }
        };

        receiver.onReceive(context, intent);
    }

    /**
     * Test parsing the ACTION_NOTIFICATION_DISMISSED intent calls onNotificationDismissed.
     */
    @Test
    public void testOnNotificationDismissed() {
        Intent intent = new Intent(PushManager.ACTION_NOTIFICATION_DISMISSED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 101);

        receiver = new AirshipReceiver() {
            @Override
            protected void onNotificationDismissed(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {
                callbackCount++;

                assertNotNull(context);
                assertEquals(pushBundle, notificationInfo.getMessage().getPushBundle());
                assertEquals(101, notificationInfo.getNotificationId());
            }
        };

        receiver.onReceive(context, intent);
        assertEquals(1, callbackCount);
    }



}
