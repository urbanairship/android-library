/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;

public class DefaultNotificationFactoryTest extends BaseTestCase {

    private DefaultNotificationFactory factory;
    private Context context = UAirship.getApplicationContext();
    private PushMessage pushMessage;

    @Before
    public void setup() {
        factory = new DefaultNotificationFactory(context);

        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        extras.putString(PushMessage.EXTRA_PUSH_ID, "0a2027a0-1766-11e4-9db0-90e2ba287ae5");

        pushMessage = new PushMessage(extras);
    }

    /**
     * Test createNotification returns null
     */
    @Test
    public void testBuildNotificationNull() {
        Bundle empty = new Bundle();
        PushMessage emptyPushMessage = new PushMessage(empty);

        Notification notification = factory.createNotification(emptyPushMessage, 1003);
        assertNull("Notification should be null", notification);
    }

    /**
     * Test createNotification
     */
    @Test
    public void testBuildNotification() {
        Notification notification = factory.createNotification(pushMessage, 1006);

        assertNotNull("Notification should not be null.", notification);
        assertEquals("Title should use app name by default.", 0, factory.getTitleId());
        assertEquals("Small icon should match.", UAirship.getAppIcon(), factory.getSmallIconId());
    }


    /**
     * Test getNextId returns the constant notification ID.
     */
    @Test
    public void testGetNextIdConstantNotificationId() {
        factory.setConstantNotificationId(200);
        assertEquals("Constant Notification IDs should match.", 200, factory.getNextId(pushMessage));
    }

    /**
     * Test constant notification ID.
     */
    @Test
    public void testConstantNotificationId() {
        factory.setConstantNotificationId(300);
        assertEquals("Constant Notification IDs should match.", 300, factory.getConstantNotificationId());
    }

    /**
     * Test not displaying a title.
     */
    @Test
    public void testNoTitle() {
        factory.setTitleId(-1);
        assertEquals("Should not have title.", -1, factory.getTitleId());
    }

    /**
     * Test displaying the application name as the title.
     */
    @Test
    public void testTitleAppName() {
        factory.setTitleId(0);
        assertEquals("Title should be app name.", 0, factory.getTitleId());
    }

    /**
     * Test small icon.
     */
    @Test
    public void testSmallIcon() {
        int testSmallIconId = 12345;
        factory.setSmallIconId(testSmallIconId);
        assertEquals("The small icons should match.", testSmallIconId, factory.getSmallIconId());
    }

    /**
     * Test large icon.
     */
    @Test
    public void testLargeIcon() {
        int testLargeIcon = 1;
        assertNotNull("testLargeIcon should not be null.", testLargeIcon);
        factory.setLargeIcon(testLargeIcon);
        assertSame("The large icons should match.", testLargeIcon, factory.getLargeIcon());
    }

    /**
     * Test accent color.
     */
    @Test
    public void testColor() {
        int testColor = 1;
        assertNotNull("testColor should not be null.", testColor);
        factory.setColor(testColor);
        assertSame("The accent colors should match.", testColor, factory.getColor());
    }

    /**
     * Test sound.
     */
    @Test
    public void testSound() {
        factory.setSound(Settings.System.DEFAULT_RINGTONE_URI);
        assertEquals("The sound should match.", Settings.System.DEFAULT_RINGTONE_URI, factory.getSound());
    }

    /**
     * Test sound setting from a notification.
     */
    @Test
    public void testPushMessageSound() {
        context = Mockito.spy(UAirship.getApplicationContext());
        Resources resources = Mockito.mock(Resources.class);
        Mockito.when(resources.getIdentifier(Mockito.eq("test_sound"), Mockito.anyString(), Mockito.anyString())).thenReturn(5);
        Mockito.when(context.getApplicationContext()).thenReturn(context);
        Mockito.when(context.getResources()).thenReturn(resources);

        // Additional config required for SDKs 21+
        Configuration config = new Configuration();
        config.setToDefaults();
        Mockito.when(resources.getConfiguration()).thenReturn(config);
        if (Build.VERSION.SDK_INT >= 23) {
            // Background cannot be translucent
            Mockito.when(resources.getColor(Mockito.anyInt(), Mockito.any(Resources.Theme.class))).thenReturn(0xFFFFFFFF);
        }

        Bundle extras = new Bundle();
        extras.putString(PushMessage.EXTRA_SOUND, "test_sound");
        extras.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        extras.putString(PushMessage.EXTRA_PUSH_ID, "0a2027a0-1766-11e4-9db0-90e2ba287ae5");
        pushMessage = new PushMessage(extras);

        factory = new DefaultNotificationFactory(context);
        factory.createNotification(pushMessage, 1);

        assertNull("Notification factory sound should not be overwritten.", factory.getSound());
    }

    /**
     * Test the DefaultNotificationFactory factory method.
     */
    @Test
    public void testNewFactory() {
        AirshipConfigOptions configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setProductionAppSecret("appSecret")
                .setProductionAppKey("appKey")
                .setNotificationIcon(10)
                .setNotificationAccentColor(20)
                .setNotificationChannel("test_channel")
                .build();

        DefaultNotificationFactory factory = DefaultNotificationFactory.newFactory(context, configOptions);
        assertEquals(10, factory.getSmallIconId());
        assertEquals(20, factory.getColor());
        assertEquals("test_channel", factory.getNotificationChannel());
    }
}
