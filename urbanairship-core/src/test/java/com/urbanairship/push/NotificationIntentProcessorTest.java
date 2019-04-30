/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.InteractiveNotificationEvent;
import com.urbanairship.iam.LegacyInAppMessageManager;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationIntentProcessor} tests.
 */
public class NotificationIntentProcessorTest extends BaseTestCase {

    private Context context;
    private NotificationManager notificationManager;
    private Analytics analytics;
    private LegacyInAppMessageManager legacyInAppMessageManager;
    private final Executor executor = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private NotificationListener notificationListener;

    private Intent responseIntent;
    private Intent dismissIntent;
    private PushMessage message;
    private Intent launchIntent;

    @Before
    public void before() {
        context = mock(Context.class);
        notificationManager = mock(NotificationManager.class);
        analytics = mock(Analytics.class);
        legacyInAppMessageManager = mock(LegacyInAppMessageManager.class);
        notificationListener = mock(NotificationListener.class);

        PushManager pushManager = mock(PushManager.class);
        when(pushManager.getNotificationListener()).thenReturn(notificationListener);

        TestApplication.getApplication().setLegacyInAppMessageManager(legacyInAppMessageManager);
        TestApplication.getApplication().setAnalytics(analytics);
        TestApplication.getApplication().setPushManager(pushManager);

        when(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager);

        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "sendId");
        pushBundle.putString(PushMessage.EXTRA_METADATA, "metadata");
        message = new PushMessage(pushBundle);

        responseIntent = new Intent()
                .setAction(PushManager.ACTION_NOTIFICATION_RESPONSE)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150)
                .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, "TAG");

        dismissIntent = new Intent()
                .setAction(PushManager.ACTION_NOTIFICATION_DISMISSED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, pushBundle)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, 150)
                .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, "TAG");

        launchIntent = new Intent()
                .setAction("LAUNCH");

        PackageManager packageManager = mock(PackageManager.class);
        when(packageManager.getLaunchIntentForPackage(anyString())).thenReturn(launchIntent);
        when(context.getPackageManager()).thenReturn(packageManager);
    }

    /**
     * Test notification response.
     */
    @Test
    public void testNotificationResponse() throws ExecutionException, InterruptedException {
        when(notificationListener.onNotificationOpened(any(NotificationInfo.class))).thenReturn(false);

        final Boolean result = processIntent(responseIntent);
        assertTrue(result);

        // Verify the conversion id and metadata was set
        verify(analytics).setConversionSendId(message.getSendId());
        verify(analytics).setConversionMetadata(message.getMetadata());

        // Verify the legacy in-app manager was notified
        verify(legacyInAppMessageManager).onPushResponse(message);

        // Verify the application was launched
        verifyApplicationLaunched();

        // Verify the listener was called
        verify(notificationListener).onNotificationOpened(any(NotificationInfo.class));
    }

    /**
     * Test notification response when the listener returns true.
     */
    @Test
    public void testNotificationResponseListenerStartsApp() throws ExecutionException, InterruptedException {
        when(notificationListener.onNotificationOpened(any(NotificationInfo.class))).thenReturn(true);

        final Boolean result = processIntent(responseIntent);
        assertTrue(result);

        // Verify the application was not auto launched
        verify(context, never()).startActivity(any(Intent.class));
    }

    /**
     * Test foreground action response.
     */
    @Test
    public void testForegroundActionResponse() throws ExecutionException, InterruptedException {
        when(notificationListener.onNotificationForegroundAction(any(NotificationInfo.class), any(NotificationActionButtonInfo.class)))
                .thenReturn(false);

        // Update the response intent to contain action info
        responseIntent
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true);

        final Boolean result = processIntent(responseIntent);
        assertTrue(result);

        // Verify the conversion id and metadata was set
        verify(analytics).setConversionSendId(message.getSendId());
        verify(analytics).setConversionMetadata(message.getMetadata());

        // Verify the legacy in-app manager was notified
        verify(legacyInAppMessageManager).onPushResponse(message);

        // Verify the notification was dismissed
        verify(notificationManager).cancel("TAG", 150);

        // Verify the application was launched
        verifyApplicationLaunched();

        // Verify we added an interactive notification event
        verify(analytics).addEvent(any(InteractiveNotificationEvent.class));

        // Verify the listener was notified
        verify(notificationListener).onNotificationForegroundAction(any(NotificationInfo.class),
                any(NotificationActionButtonInfo.class));
    }

    /**
     * Test foreground action response when the listener returns true.
     */
    @Test
    public void testForegroundActionResponseListenerStartsApp() throws ExecutionException, InterruptedException {
        when(notificationListener.onNotificationForegroundAction(any(NotificationInfo.class), any(NotificationActionButtonInfo.class)))
                .thenReturn(true);

        // Update the response intent to contain action info
        responseIntent
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, true);

        final Boolean result = processIntent(responseIntent);
        assertTrue(result);

        // Verify the application was not auto launched
        verify(context, never()).startActivity(any(Intent.class));
    }

    /**
     * Test background action response.
     */
    @Test
    public void testBackgroundActionResponse() throws ExecutionException, InterruptedException {
        // Update the response intent to contain action info
        responseIntent
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID, "buttonId")
                .putExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false);

        final Boolean result = processIntent(responseIntent);
        assertTrue(result);

        // Verify the notification was dismissed
        verify(notificationManager).cancel("TAG", 150);

        // Verify the application was not auto launched
        verify(context, never()).startActivity(any(Intent.class));

        // Verify we added an interactive notification event
        verify(analytics).addEvent(any(InteractiveNotificationEvent.class));

        // Verify the listener was notified
        verify(notificationListener).onNotificationBackgroundAction(any(NotificationInfo.class),
                any(NotificationActionButtonInfo.class));
    }

    /**
     * Test notification dismissed.
     */
    @Test
    public void testNotificationDismissed() throws ExecutionException, InterruptedException {

        final Boolean result = processIntent(dismissIntent);
        assertTrue(result);

        // Verify the application was not auto launched
        verify(context, never()).startActivity(any(Intent.class));

        // Verify the listener was notified
        verify(notificationListener).onNotificationDismissed(any(NotificationInfo.class));
    }

    /**
     * Test invalid intents.
     */
    @Test
    public void testInvalidIntents() throws ExecutionException, InterruptedException {
        // Missing action
        assertFalse(processIntent(new Intent()));

        // Missing push data
        assertFalse(processIntent(new Intent().setAction(PushManager.ACTION_NOTIFICATION_RESPONSE)));
        assertFalse(processIntent(new Intent().setAction(PushManager.ACTION_NOTIFICATION_DISMISSED)));
    }

    private void verifyApplicationLaunched() {
        verify(context).startActivity(launchIntent);
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP, launchIntent.getFlags());
        assertBundlesEquals(message.getPushBundle(), launchIntent.getBundleExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE));
    }

    private Boolean processIntent(Intent intent) throws ExecutionException, InterruptedException {
        return new NotificationIntentProcessor(UAirship.shared(), context, intent, executor)
                .process()
                .get();
    }

}
