package com.urbanairship.push.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.net.Uri;

import com.urbanairship.BaseTestCase;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;

public class NotificationChannelRegistryDataManagerTest extends BaseTestCase {

    private NotificationChannelRegistryDataManager dataManager;
    private NotificationChannelCompat channel1;
    private NotificationChannelCompat channel2;

    @Before
    public void setUp() {
        dataManager = new NotificationChannelRegistryDataManager(RuntimeEnvironment.application, "appKey", "test");
        channel1 = new NotificationChannelCompat("test", "Test Channel", NotificationManager.IMPORTANCE_HIGH);
        channel1.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        channel1.setSound(Uri.parse("cool://sound"));
        channel1.setGroup("group");
        channel1.setDescription("Test Notification Channel");

        channel2 = new NotificationChannelCompat("test2", "Test Channel 2", NotificationManager.IMPORTANCE_LOW);
        channel2.setLightColor(234);
        channel2.enableVibration(true);
        channel2.enableLights(true);
        channel2.setGroup("other group");
        channel2.setBypassDnd(true);
    }

    @After
    public void teardown() {
        dataManager.deleteChannels();
    }

    @Test
    public void testCreateChannel() {
        boolean success = dataManager.createChannel(channel1);
        Assert.assertTrue(success);
    }

    @Test
    public void testGetChannel() {
        dataManager.createChannel(channel1);
        Assert.assertEquals(channel1, dataManager.getChannel(channel1.getId()));
    }

    @Test
    public void testGetChannels() {
        dataManager.createChannel(channel1);
        dataManager.createChannel(channel2);

        Set<NotificationChannelCompat> channels = new HashSet<>();
        channels.add(channel1);
        channels.add(channel2);

        Set<NotificationChannelCompat> otherChannels = dataManager.getChannels();
        Assert.assertEquals(channels, otherChannels);
    }

    @Test
    public void testDeleteChannel() {
        dataManager.createChannel(channel1);
        Assert.assertTrue(dataManager.deleteChannel(channel1.getId()));
    }
}
