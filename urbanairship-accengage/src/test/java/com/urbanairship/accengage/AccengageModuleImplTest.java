package com.urbanairship.accengage;

import android.app.Application;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.accengage.notifications.AccengageNotificationProvider;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.accengage.AccengageModule;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.notifications.NotificationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class AccengageModuleImplTest {

    private AccengageModule accengageModuleLoader;
    private AirshipConfigOptions mockConfig;

    @Before
    public void setup() {
        Application application = ApplicationProvider.getApplicationContext();
        AirshipChannel mockChannel = mock(AirshipChannel.class);
        PushManager mockPush = mock(PushManager.class);
        mockConfig = mock(AirshipConfigOptions.class);

        PreferenceDataStore preferenceDataStore = PreferenceDataStore.inMemoryStore(application);
        PrivacyManager privacyManager = new PrivacyManager(preferenceDataStore, PrivacyManager.FEATURE_ALL);
        accengageModuleLoader = new AccengageModuleFactoryImpl().build(application, mockConfig, preferenceDataStore, privacyManager, mockChannel, mockPush);
    }

    @Test
    public void testNotificationHandler() {
        assertEquals(1, accengageModuleLoader.getComponents().size());

        Accengage accengage = (Accengage)accengageModuleLoader.getComponents().iterator().next();
        assertNotNull(accengage);

        assertEquals(accengage.getNotificationProvider(), accengageModuleLoader.getAccengageNotificationHandler().getNotificationProvider());

        NotificationProvider notificationProvider = new AccengageNotificationProvider(mockConfig);

        accengage.setNotificationProvider(notificationProvider);

        assertEquals(notificationProvider, accengageModuleLoader.getAccengageNotificationHandler().getNotificationProvider());
        assertEquals(notificationProvider, accengage.getNotificationProvider());
    }

}
