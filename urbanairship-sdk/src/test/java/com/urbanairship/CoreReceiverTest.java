/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionService;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.InteractiveNotificationEvent;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.push.iam.InAppMessageManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CoreReceiverTest extends BaseTestCase {

    Context context;
    CoreReceiver receiver;
    NotificationManager notificationManager;
    Analytics analytics;

    @Before
    public void before() {
        receiver = new CoreReceiver();
        context = mock(Context.class);
        notificationManager = mock(NotificationManager.class);
        analytics = mock(Analytics.class);

        TestApplication.getApplication().setAnalytics(analytics);
        when(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager);
    }

    /**
     * Test open notification proxy intent action.
     */
    @Test
    public void testOnNotificationOpenedProxy() {
        // Create the push message with the matching send ID
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "sendId");
        PushMessage message = new PushMessage(pushBundle);

        // Create the intent
        final Intent intent = new Intent()
                .setAction(PushManager.ACTION_NOTIFICATION_OPENED_PROXY)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150);

        receiver.onReceive(context, intent);

        // Verify the conversion send id was set
        verify(analytics).setConversionSendId("sendId");

        // Verify the ordered broadcast is sent to notify the apps intent receiver
        verify(context).sendOrderedBroadcast(argThat(new ArgumentMatcher<Intent>() {
            @Override
            public boolean matches(Intent other) {
                return PushManager.ACTION_NOTIFICATION_OPENED.equals(other.getAction()) &&
                        areEqual(intent.getExtras(), other.getExtras()) &&
                        UAirship.getPackageName().equals(other.getPackage()) &&
                        other.getCategories().contains(UAirship.getPackageName());
            }
        }), eq(UAirship.getUrbanAirshipPermission()));
    }

    /**
     * Test when a notification is opened it clears the pending in-app message.
     */
    @Test
    public void testOnNotificationOpenedProxyClearPendingInAppMessage() {
        InAppMessage inAppMessage = new InAppMessage.Builder()
                .setAlert("oh hi")
                .setExpiry(1000l)
                .setId("sendId")
                .create();

        // Set the pending in-app message
        InAppMessageManager inAppMessageManager = UAirship.shared().getInAppMessageManager();
        inAppMessageManager.setPendingMessage(inAppMessage);

        // Create the push message with the matching send ID
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "sendId");
        PushMessage message = new PushMessage(pushBundle);

        // Create the intent
        Intent intent = new Intent()
                .setAction(PushManager.ACTION_NOTIFICATION_OPENED_PROXY)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle());


        receiver.onReceive(context, intent);
        assertNull(inAppMessageManager.getPendingMessage());
    }

    /**
     * Test open notification button proxy intent action.
     */
    @Test
    public void testOnNotificationButtonOpenedProxy() {
        // Create the push message with the matching send ID
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "sendId");
        PushMessage message = new PushMessage(pushBundle);

        // Create the intent
        final Intent intent = new Intent()
                .setAction(PushManager.ACTION_NOTIFICATION_BUTTON_OPENED_PROXY)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true);

        receiver.onReceive(context, intent);

        // Verify the notification was dismissed
        verify(notificationManager).cancel(null, 150);

        // Verify the conversion send id was set
        verify(analytics).setConversionSendId("sendId");

        // Verify we added an interactive notification event
        verify(analytics).addEvent(any(InteractiveNotificationEvent.class));

        // Verify the ordered broadcast is sent to notify the apps intent receiver
        verify(context).sendOrderedBroadcast(argThat(new ArgumentMatcher<Intent>() {
            @Override
            public boolean matches(Intent other) {
                return PushManager.ACTION_NOTIFICATION_OPENED.equals(other.getAction()) &&
                        areEqual(intent.getExtras(), other.getExtras()) &&
                        UAirship.getPackageName().equals(other.getPackage()) &&
                        other.getCategories().contains(UAirship.getPackageName());
            }
        }), eq(UAirship.getUrbanAirshipPermission()));
    }

    /**
     * Test open background notification button proxy intent action does
     * not set the conversion send ID.
     */
    @Test
    public void testOnNotificationButtonOpenedProxyBackground() {
        // Create the push message with the matching send ID
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "sendId");
        PushMessage message = new PushMessage(pushBundle);

        // Create the intent
        final Intent intent = new Intent()
                .setAction(PushManager.ACTION_NOTIFICATION_BUTTON_OPENED_PROXY)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150)
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false);

        receiver.onReceive(context, intent);


        // Verify the conversion send id was set
        verify(analytics, Mockito.never()).setConversionSendId("sendId");
    }

    /**
     * Test notification dismissed proxy intent action.
     */
    @Test
    public void testOnNotificationDismissedProxy() throws PendingIntent.CanceledException {
        // Create the push message with the matching send ID
        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "sendId");
        PushMessage message = new PushMessage(pushBundle);

        PendingIntent deleteIntent = mock(PendingIntent.class);

        // Create the intent
        final Intent intent = new Intent()
                .setAction(PushManager.ACTION_NOTIFICATION_DISMISSED_PROXY)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150)
                .putExtra(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT, deleteIntent);

        receiver.onReceive(context, intent);

        // Verify the delete intent was sent
        verify(deleteIntent).send();

        // Verify the ordered broadcast is sent to notify the apps intent receiver
        verify(context).sendOrderedBroadcast(argThat(new ArgumentMatcher<Intent>() {
            @Override
            public boolean matches(Intent other) {
                return PushManager.ACTION_NOTIFICATION_DISMISSED.equals(other.getAction()) &&
                        areEqual(intent.getExtras(), other.getExtras()) &&
                        UAirship.getPackageName().equals(other.getPackage()) &&
                        other.getCategories().contains(UAirship.getPackageName());
            }
        }), eq(UAirship.getUrbanAirshipPermission()));
    }
}
