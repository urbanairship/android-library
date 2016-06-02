/* Copyright 2016 Urban Airship and Contributors */

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

public class SystemNotificationFactoryTest extends BaseTestCase {

    private SystemNotificationFactory factory;
    private PushMessage pushMessage;

    @Before
    public void setup() {
        factory = new SystemNotificationFactory(RuntimeEnvironment.application);

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
}
