/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

public class NotificationFactoryTest extends BaseTestCase {

    private NotificationFactory factory;
    private PushMessage pushMessage;

    @Before
    public void setup() {
        factory = new NotificationFactory(RuntimeEnvironment.application);

        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!!!");
        extras.putString(PushMessage.EXTRA_PUSH_ID, "0a2027a0-1766-11e4-9db0-90e2ba287ae8");

        pushMessage = new PushMessage(extras);
    }

    /**
     * Test createNotification returns null
     */
    @Test
    public void testBuildNotificationNull() {
        Bundle empty = new Bundle();
        PushMessage emptyPushMessage = new PushMessage(empty);

        Notification notification = factory.createNotification(emptyPushMessage, 1004);
        assertNull("Notification should be null", notification);
    }

    /**
     * Test createNotification
     */
    @Test
    public void testBuildNotification() {
        Notification notification = factory.createNotification(pushMessage, 1005);

        assertNotNull("Notification should not be null.", notification);
        assertEquals("Title should use app name by default.", 0, factory.getTitleId());
        assertEquals("Small icon should match.", UAirship.getAppIcon(), factory.getSmallIconId());
    }

    /**
     * Test the default notification ID.
     */
    @Test
    public void testNotificationId() {
        int notificationId = factory.getNextId(pushMessage);
        int nextId = factory.getNextId(pushMessage);

        // Verify the IDs are sequential
        assertEquals(nextId, notificationId + 1);
    }

    /**
     * Test using a constant notification ID.
     */
    @Test
    public void testConstantNotificationId() {
        factory.setConstantNotificationId(100);

        assertEquals(100, factory.getNextId(pushMessage));
        assertEquals(100, factory.getNextId(pushMessage));
    }

    /**
     * Test a push message with a notification ID returns {@link NotificationFactory#TAG_NOTIFICATION_ID}
     */
    @Test
    public void testNotificationTagId() {
        Bundle bundle = pushMessage.getPushBundle();
        bundle.putString(PushMessage.EXTRA_NOTIFICATION_TAG, "cool");

        pushMessage = new PushMessage(bundle);

        assertEquals(NotificationFactory.TAG_NOTIFICATION_ID, factory.getNextId(pushMessage));
        assertEquals(NotificationFactory.TAG_NOTIFICATION_ID, factory.getNextId(pushMessage));
    }
}
