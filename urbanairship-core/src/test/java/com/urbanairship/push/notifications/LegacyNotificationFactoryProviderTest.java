/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LegacyNotificationFactoryProvider}
 */
public class LegacyNotificationFactoryProviderTest extends BaseTestCase {

    private LegacyNotificationFactoryProvider provider;
    private NotificationFactory mockFactory;
    private PushMessage pushMessage;
    private Context context;

    @Before
    public void setup() {
        context = TestApplication.getApplication();
        mockFactory = mock(NotificationFactory.class);
        provider = new LegacyNotificationFactoryProvider(mockFactory);


        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        extras.putString(PushMessage.EXTRA_PUSH_ID, "0a2027a0-1766-11e4-9db0-90e2ba287ae5");
        extras.putString(PushMessage.EXTRA_NOTIFICATION_TAG, "tag");

        pushMessage = new PushMessage(extras);
    }

    /**
     * Test arguments pulls data from the factory.
     */
    @Test
    public void testArguments() {
        when(mockFactory.requiresLongRunningTask(pushMessage)).thenReturn(true);
        when(mockFactory.getNextId(pushMessage)).thenReturn(1100);

        NotificationArguments arguments = provider.onCreateNotificationArguments(context, pushMessage);

        assertEquals(1100, arguments.getNotificationId());
        assertEquals("tag", arguments.getNotificationTag());
        assertTrue(arguments.getRequiresLongRunningTask());
        assertEquals(pushMessage, arguments.getMessage());
    }

    /**
     * Test the factory OK result is converted to the new OK result.
     */
    @Test
    public void testOkResult() {
        Notification notification = new NotificationCompat.Builder(RuntimeEnvironment.application, "some-channel")
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .build();

        when(mockFactory.getNextId(pushMessage)).thenReturn(1100);
        NotificationArguments arguments = provider.onCreateNotificationArguments(context, pushMessage);

        when(mockFactory.createNotificationResult(arguments.getMessage(), arguments.getNotificationId(), arguments.getRequiresLongRunningTask()))
                .thenReturn(NotificationFactory.Result.notification(notification));

        NotificationResult result = provider.onCreateNotification(context, arguments);

        assertEquals(notification, result.getNotification());
        assertEquals(NotificationResult.OK, result.getStatus());
    }

    /**
     * Test the factory cancel result is converted to the new cancel result.
     */
    @Test
    public void testCancelResult() {
        when(mockFactory.getNextId(pushMessage)).thenReturn(1100);
        NotificationArguments arguments = provider.onCreateNotificationArguments(context, pushMessage);

        when(mockFactory.createNotificationResult(arguments.getMessage(), arguments.getNotificationId(), arguments.getRequiresLongRunningTask()))
                .thenReturn(NotificationFactory.Result.cancel());

        NotificationResult result = provider.onCreateNotification(context, arguments);

        assertEquals(NotificationResult.CANCEL, result.getStatus());
    }

    /**
     * Test the factory retry result is converted to the new retry result.
     */
    @Test
    public void testRetryResult() {
        when(mockFactory.getNextId(pushMessage)).thenReturn(1100);
        NotificationArguments arguments = provider.onCreateNotificationArguments(context, pushMessage);

        when(mockFactory.createNotificationResult(arguments.getMessage(), arguments.getNotificationId(), arguments.getRequiresLongRunningTask()))
                .thenReturn(NotificationFactory.Result.retry());

        NotificationResult result = provider.onCreateNotification(context, arguments);

        assertEquals(NotificationResult.RETRY, result.getStatus());
    }
}