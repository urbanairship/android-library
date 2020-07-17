/* Copyright Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.pm.ProviderInfo;

import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.js.Whitelist;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.PlatformUtils;

import org.robolectric.Robolectric;
import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import androidx.test.core.app.ApplicationProvider;

public class TestApplication extends Application implements TestLifecycleApplication {

    public PreferenceDataStore preferenceDataStore;

    private TestAirshipRuntimeConfig testRuntimeConfig;

    @Override
    public void onCreate() {
        super.onCreate();

        this.preferenceDataStore = new PreferenceDataStore(getApplicationContext());
        preferenceDataStore.executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };

        testRuntimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        AirshipConfigOptions airshipConfigOptions = testRuntimeConfig.getConfigOptions();

        UAirship.application = this;
        UAirship.isFlying = true;
        UAirship.isTakingOff = true;

        UAirship.sharedAirship = new UAirship(airshipConfigOptions);
        UAirship.sharedAirship.preferenceDataStore = preferenceDataStore;
        UAirship.sharedAirship.runtimeConfig = testRuntimeConfig;
        UAirship.sharedAirship.localeManager = new LocaleManager(this, preferenceDataStore);
        UAirship.sharedAirship.channel = new AirshipChannel(this, preferenceDataStore, UAirship.sharedAirship.runtimeConfig, UAirship.sharedAirship.localeManager);
        UAirship.sharedAirship.analytics = new Analytics(this, preferenceDataStore, testRuntimeConfig, UAirship.sharedAirship.channel,  UAirship.sharedAirship.localeManager);
        UAirship.sharedAirship.pushManager = new PushManager(this, preferenceDataStore, airshipConfigOptions, null, UAirship.sharedAirship.channel, UAirship.sharedAirship.analytics);
        UAirship.sharedAirship.whitelist = Whitelist.createDefaultWhitelist(airshipConfigOptions);

        ProviderInfo info = new ProviderInfo();
        info.authority = UrbanAirshipProvider.getAuthorityString(this);
        Robolectric.buildContentProvider(UrbanAirshipProvider.class).create(info);
    }

    public void setPlatform(int platform) {
        testRuntimeConfig.setPlatform(PlatformUtils.parsePlatform(platform));
    }

    public static TestApplication getApplication() {
        return (TestApplication) ApplicationProvider.getApplicationContext();
    }

    public void setApplicationMetrics(ApplicationMetrics metrics) {
        UAirship.shared().applicationMetrics = metrics;
    }

    @Override
    public void afterTest(Method method) {
    }

    @Override
    public void beforeTest(Method method) {
    }

    @Override
    public void prepareTest(Object test) {

    }

    public void setChannel(AirshipChannel channel) {
        UAirship.shared().channel = channel;
    }

    public void setPushManager(PushManager pushManager) {
        UAirship.shared().pushManager = pushManager;
    }

    public void setLocationClient(AirshipLocationClient locationClient) {
        UAirship.shared().locationClient = locationClient;
    }
}
