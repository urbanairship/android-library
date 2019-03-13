package com.urbanairship.push.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Looper;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipLoopers;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotificationChannelRegistryTest extends BaseTestCase {

    NotificationChannelRegistry channelRegistry;
    NotificationChannelRegistryDataManager dataManager;
    NotificationManager notificationManager;
    Context context;
    NotificationChannel channel;
    NotificationChannel otherChannel;
    NotificationChannelCompat channelCompat;
    NotificationChannelCompat otherChannelCompat;

    @Before
    public void setUp() {
        context = mock(Context.class);
        notificationManager = mock(NotificationManager.class);
        dataManager = mock(NotificationChannelRegistryDataManager.class);
        when(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager);

        AirshipConfigOptions options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appkey")
                .setDevelopmentAppSecret("appsecret")
                .build();

        channelRegistry = new NotificationChannelRegistry(context, dataManager);

        channel = new NotificationChannel("test", "Test Channel", NotificationManager.IMPORTANCE_HIGH);
        otherChannel = new NotificationChannel("test2", "Test Channel 2", NotificationManager.IMPORTANCE_LOW);

        channelCompat = new NotificationChannelCompat("test", "Test Channel", NotificationManager.IMPORTANCE_HIGH);
        otherChannelCompat = new NotificationChannelCompat("test2", "Test Channel 2", NotificationManager.IMPORTANCE_LOW);
    }

    @Test
    @Config(sdk = 25)
    public void testGetNotificationChannelAsyncPreOreo() {
        when(dataManager.getChannel("test")).thenReturn(channelCompat);
        PendingResult<NotificationChannelCompat> result = channelRegistry.getNotificationChannel("test");

        runLooperTasks();

        verify(dataManager).getChannel("test");
        Assert.assertEquals(channelCompat, result.getResult());
    }

    @Test
    public void testGetNotificationChannelAsync() {
        when(notificationManager.getNotificationChannel("test")).thenReturn(channel);
        PendingResult<NotificationChannelCompat> result = channelRegistry.getNotificationChannel("test");

        runLooperTasks();

        verify(notificationManager).getNotificationChannel("test");
        Assert.assertEquals(channelCompat, result.getResult());
    }

    @Test
    @Config(sdk = 25)
    public void testCreateNotificationChannelPreOreo() {
        channelRegistry.createNotificationChannel(channelCompat);
        runLooperTasks();
        verify(dataManager).createChannel(channelCompat);
    }

    @Test
    public void testCreateNotificationChannel() {
        channelRegistry.createNotificationChannel(channelCompat);
        runLooperTasks();
        verify(notificationManager).createNotificationChannel(channel);
    }

    @Test
    @Config(sdk = 25)
    public void testDeleteNotificationChannelPreOreo() {
        channelRegistry.deleteNotificationChannel("test");
        runLooperTasks();
        verify(dataManager).deleteChannel("test");
    }

    @Test
    public void testDeleteNotificationChannel() {
        channelRegistry.deleteNotificationChannel("test");
        runLooperTasks();
        verify(notificationManager).deleteNotificationChannel("test");
    }

    /**
     * Helper method to run all the looper tasks.
     */
    private void runLooperTasks() {
        ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
        ShadowLooper backgroundLooper = Shadows.shadowOf(AirshipLoopers.getBackgroundLooper());

        do {
            mainLooper.runToEndOfTasks();
            backgroundLooper.runToEndOfTasks();
        }
        while (mainLooper.getScheduler().areAnyRunnable() || backgroundLooper.getScheduler().areAnyRunnable());
    }
}
