/* Copyright Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.pm.ProviderInfo;

import org.robolectric.Robolectric;
import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;

public class TestApplication extends Application implements TestLifecycleApplication {

    private PreferenceDataStore preferenceDataStore;
    private TestAirshipRuntimeConfig testRuntimeConfig;

    @Override
    public void onCreate() {
        super.onCreate();

        this.preferenceDataStore = new PreferenceDataStore(getApplicationContext());
        testRuntimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        AirshipConfigOptions airshipConfigOptions = testRuntimeConfig.getConfigOptions();

        UAirship.application = this;
        UAirship.isFlying = true;
        UAirship.isTakingOff = true;

        UAirship.sharedAirship = new UAirship(airshipConfigOptions);
        UAirship.sharedAirship.preferenceDataStore = preferenceDataStore;
        UAirship.sharedAirship.runtimeConfig = testRuntimeConfig;
        ProviderInfo info = new ProviderInfo();
        info.authority = UrbanAirshipProvider.getAuthorityString(this);
        Robolectric.buildContentProvider(UrbanAirshipProvider.class).create(info);
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
}
