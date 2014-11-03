package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

@RunWith(RobolectricGradleTestRunner.class)
public class SystemNotificationFactoryTest {

    private SystemNotificationFactory factory;
    private Context context = UAirship.getApplicationContext();
    private PushMessage pushMessage;

    @Before
    public void setup() {
        factory = new SystemNotificationFactory(context);

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
