package com.urbanairship.accengage.notifications;

import android.app.Application;
import android.app.Notification;
import android.content.Context;
import android.os.Bundle;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.accengage.AccengageMessage;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.notifications.NotificationArguments;
import com.urbanairship.push.notifications.NotificationResult;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.mockito.Mockito.mock;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class AccengageNotificationProviderTest {

    @Test
    public void testOnCreateNotificationArguments() {
        AirshipConfigOptions configOptions = mock(AirshipConfigOptions.class);

        Application application = ApplicationProvider.getApplicationContext();
        Context context = Mockito.spy(application);

        Bundle extras = new Bundle();
        extras.putString("a4scontent", "accengageContent");
        extras.putInt("a4ssysid", 77);
        extras.putInt("a4sid", 77);
        extras.putString("a4stitle", "title test");

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage message = AccengageMessage.fromAirshipPushMessage(pushMessage);

        AccengageNotificationProvider accengageNotificationProvider = new AccengageNotificationProvider(configOptions);

        NotificationArguments arguments = accengageNotificationProvider.onCreateNotificationArguments(context, pushMessage);

        Assert.assertEquals("com.urbanairship.default", arguments.getNotificationChannelId());
        Assert.assertEquals(77, arguments.getNotificationId());
    }

    @Test
    public void testOnCreateNotification() {
        AirshipConfigOptions configOptions = mock(AirshipConfigOptions.class);

        Application application = ApplicationProvider.getApplicationContext();
        Context context = Mockito.spy(application);

        Bundle extras = new Bundle();
        extras.putString("a4scontent", "accengageContent");
        extras.putInt("a4ssysid", 77);
        extras.putInt("a4sid", 77);
        extras.putString("a4stitle", "title test");
        extras.putString("a4sforeground", "true");

        PushMessage pushMessage = new PushMessage(extras);
        AccengageMessage message = AccengageMessage.fromAirshipPushMessage(pushMessage);

        AccengageNotificationProvider accengageNotificationProvider = new AccengageNotificationProvider(configOptions);

        NotificationArguments arguments = accengageNotificationProvider.onCreateNotificationArguments(context, pushMessage);
        NotificationResult result = accengageNotificationProvider.onCreateNotification(context, arguments);
        Notification notification = result.getNotification();

        Assert.assertEquals("com.urbanairship.default", arguments.getNotificationChannelId());
        Assert.assertEquals(77, arguments.getNotificationId());
        Assert.assertEquals("title test", String.valueOf(notification.extras.getCharSequence("android.title")));
        Assert.assertEquals("accengageContent", String.valueOf(notification.extras.getCharSequence("android.text")));
    }

}
