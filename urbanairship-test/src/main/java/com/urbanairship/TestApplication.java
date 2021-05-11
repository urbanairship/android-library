/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.ProviderInfo;

import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.modules.accengage.AccengageNotificationHandler;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.push.PushManager;
import com.urbanairship.remoteconfig.RemoteConfigManager;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.util.PlatformUtils;

import org.robolectric.Robolectric;
import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;

import androidx.test.core.app.ApplicationProvider;

public class TestApplication extends Application implements TestLifecycleApplication {

    public ActivityLifecycleCallbacks callback;
    public PreferenceDataStore preferenceDataStore;

    private TestAirshipRuntimeConfig testRuntimeConfig;

    @Override
    public void onCreate() {
        super.onCreate();

        testRuntimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        final AirshipConfigOptions airshipConfigOptions = testRuntimeConfig.getConfigOptions();

        this.preferenceDataStore = new PreferenceDataStore(getApplicationContext());
        preferenceDataStore.init(airshipConfigOptions);

        UAirship.application = this;
        UAirship.isFlying = true;
        UAirship.isTakingOff = true;

        UAirship.sharedAirship = new UAirship(airshipConfigOptions);
        UAirship.sharedAirship.preferenceDataStore = preferenceDataStore;
        UAirship.sharedAirship.localeManager = new LocaleManager(this, preferenceDataStore);
        UAirship.sharedAirship.runtimeConfig = testRuntimeConfig;

        UAirship.sharedAirship.channel = new AirshipChannel(this, preferenceDataStore, UAirship.sharedAirship.runtimeConfig, UAirship.sharedAirship.localeManager);

        UAirship.sharedAirship.analytics = new Analytics(this, preferenceDataStore, testRuntimeConfig, UAirship.sharedAirship.channel, UAirship.sharedAirship.localeManager);
        UAirship.sharedAirship.applicationMetrics = new ApplicationMetrics(this, preferenceDataStore, new TestActivityMonitor());
        UAirship.sharedAirship.pushManager = new PushManager(this, preferenceDataStore, airshipConfigOptions, new TestPushProvider(), UAirship.sharedAirship.channel, UAirship.sharedAirship.analytics);
        UAirship.sharedAirship.channelCapture = new ChannelCapture(this, airshipConfigOptions, UAirship.sharedAirship.channel, preferenceDataStore, new TestActivityMonitor());
        UAirship.sharedAirship.urlAllowList = UrlAllowList.createDefaultUrlAllowList(airshipConfigOptions);
        UAirship.sharedAirship.actionRegistry = new ActionRegistry();
        UAirship.sharedAirship.actionRegistry.registerDefaultActions(this);
        UAirship.sharedAirship.namedUser = new NamedUser(this, preferenceDataStore, testRuntimeConfig, UAirship.sharedAirship.channel);
        UAirship.sharedAirship.remoteData = new RemoteData(this, preferenceDataStore, testRuntimeConfig, UAirship.sharedAirship.pushManager, UAirship.sharedAirship.localeManager);
        UAirship.sharedAirship.remoteConfigManager = new RemoteConfigManager(this, preferenceDataStore, UAirship.sharedAirship.remoteData);

        ProviderInfo info = new ProviderInfo();
        info.authority = UrbanAirshipProvider.getAuthorityString(this);
        Robolectric.buildContentProvider(UrbanAirshipProvider.class).create(info);
    }

    public void setPlatform(int platform) {
        testRuntimeConfig.setPlatform(PlatformUtils.parsePlatform(platform));
    }

    public static com.urbanairship.TestApplication getApplication() {
        return (com.urbanairship.TestApplication) ApplicationProvider.getApplicationContext();
    }

    public void setApplicationMetrics(ApplicationMetrics metrics) {
        UAirship.shared().applicationMetrics = metrics;
    }

    public void setNamedUser(NamedUser namedUser) {
        UAirship.shared().namedUser = namedUser;
    }

    public void setAnalytics(Analytics analytics) {
        UAirship.shared().analytics = analytics;
    }

    public void setOptions(AirshipConfigOptions options) {
        UAirship.shared().airshipConfigOptions = options;
    }

    public void setAccengageNotificationHandler(AccengageNotificationHandler notificationHandler) {
        UAirship.shared().accengageNotificationHandler = notificationHandler;
    }

    @Override
    public void afterTest(Method method) {
        preferenceDataStore.tearDown();
    }

    @Override
    public void beforeTest(Method method) {
    }

    @Override
    public void prepareTest(Object test) {

    }

    @Override
    @SuppressLint("NewApi")
    public void registerActivityLifecycleCallbacks(
            Application.ActivityLifecycleCallbacks callback) {
        super.registerActivityLifecycleCallbacks(callback);
        this.callback = callback;
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

    public void setChannelCapture(ChannelCapture channelCapture) {
        UAirship.shared().channelCapture = channelCapture;
    }

}
