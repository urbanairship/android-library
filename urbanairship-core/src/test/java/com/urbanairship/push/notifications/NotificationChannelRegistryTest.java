/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationManagerCompat;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Config;

import java.util.concurrent.Executor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
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

        channelRegistry = new NotificationChannelRegistry(context, dataManager, new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });

        channel = new NotificationChannel("test", "Test Channel", NotificationManagerCompat.IMPORTANCE_HIGH);
        otherChannel = new NotificationChannel("test2", "Test Channel 2", NotificationManagerCompat.IMPORTANCE_LOW);

        channelCompat = new NotificationChannelCompat("test", "Test Channel", NotificationManagerCompat.IMPORTANCE_HIGH);
        otherChannelCompat = new NotificationChannelCompat("test2", "Test Channel 2", NotificationManagerCompat.IMPORTANCE_LOW);
    }

    @Test
    @Config(sdk = 25)
    public void testGetNotificationChannelAsyncPreOreo() {
        when(dataManager.getChannel("test")).thenReturn(channelCompat);
        PendingResult<NotificationChannelCompat> result = channelRegistry.getNotificationChannel("test");

        verify(dataManager).getChannel("test");
        Assert.assertEquals(channelCompat, result.getResult());
    }

    @Test
    public void testGetNotificationChannelAsync() {
        when(notificationManager.getNotificationChannel("test")).thenReturn(channel);
        PendingResult<NotificationChannelCompat> result = channelRegistry.getNotificationChannel("test");

        verify(notificationManager).getNotificationChannel("test");
        Assert.assertEquals(channelCompat, result.getResult());
    }

    @Test
    @Config(sdk = 25)
    public void testCreateNotificationChannelPreOreo() {
        channelRegistry.createNotificationChannel(channelCompat);
        verify(dataManager).createChannel(channelCompat);
    }

    @Test
    public void testCreateNotificationChannel() {
        channelRegistry.createNotificationChannel(channelCompat);
        verify(notificationManager).createNotificationChannel(channel);
    }

    @Test
    public void testCreateDeferredChannel() {
        channelRegistry.createDeferredNotificationChannel(channelCompat);
        verifyZeroInteractions(notificationManager);
    }

    @Test
    public void testGetNotificationChannelCreatesRealChannel() {
        when(dataManager.getChannel(channelCompat.getId())).thenReturn(channelCompat);
        channelRegistry.getNotificationChannel(channelCompat.getId());
        verify(notificationManager).createNotificationChannel(channelCompat.toNotificationChannel());
    }

    @Test
    @Config(sdk = 25)
    public void testDeleteNotificationChannelPreOreo() {
        channelRegistry.deleteNotificationChannel("test");
        verify(dataManager).deleteChannel("test");
    }

    @Test
    public void testDeleteNotificationChannel() {
        channelRegistry.deleteNotificationChannel("test");
        verify(notificationManager).deleteNotificationChannel("test");
    }

}
