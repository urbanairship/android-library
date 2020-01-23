package com.urbanairship.accengage;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.accengage.notifications.AccengageNotificationProvider;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.AccengageModuleLoader;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.notifications.NotificationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class AccengageModuleLoaderImplTest {

    private AccengageModuleLoader accengageModuleLoader;

    @Before
    public void setup() {
        Application application = ApplicationProvider.getApplicationContext();
        AirshipChannel mockChannel = mock(AirshipChannel.class);
        Analytics mockAnalytics = mock(Analytics.class);
        PushManager mockPush = mock(PushManager.class);
        PreferenceDataStore preferenceDataStore = new PreferenceDataStore(application);

        accengageModuleLoader = new AccengageModuleLoaderFactoryImpl().build(application, preferenceDataStore, mockChannel, mockPush, mockAnalytics);
    }

    @Test
    public void testNotificationHandler() {
        assertEquals(1, accengageModuleLoader.getComponents().size());

        Accengage accengage = (Accengage)accengageModuleLoader.getComponents().iterator().next();
        assertNotNull(accengage);

        assertEquals(accengage.getNotificationProvider(), accengageModuleLoader.getAccengageNotificationHandler().getNotificationProvider());

        NotificationProvider notificationProvider = new AccengageNotificationProvider();

        accengage.setNotificationProvider(notificationProvider);

        assertEquals(notificationProvider, accengageModuleLoader.getAccengageNotificationHandler().getNotificationProvider());
        assertEquals(notificationProvider, accengage.getNotificationProvider());
    }

}
