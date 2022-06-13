/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.app.Application;

import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.base.Supplier;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.contacts.Contact;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.modules.accengage.AccengageNotificationHandler;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.remoteconfig.RemoteConfigManager;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.util.PlatformUtils;

import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;

import androidx.test.core.app.ApplicationProvider;

@SuppressLint("VisibleForTests")
public class TestApplication extends Application implements TestLifecycleApplication {

    public ActivityLifecycleCallbacks callback;
    public PreferenceDataStore preferenceDataStore;

    private TestAirshipRuntimeConfig testRuntimeConfig;

    public static com.urbanairship.TestApplication getApplication() {
        return (com.urbanairship.TestApplication) ApplicationProvider.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        testRuntimeConfig = TestAirshipRuntimeConfig.newTestConfig();
        final AirshipConfigOptions airshipConfigOptions = testRuntimeConfig.getConfigOptions();

        this.preferenceDataStore = PreferenceDataStore.inMemoryStore(getApplicationContext());

        JobDispatcher dispatcher = new JobDispatcher(this, (context, jobInfo, delay) -> {});
        JobDispatcher.setInstance(dispatcher);

        PrivacyManager privacyManager = new PrivacyManager(preferenceDataStore, PrivacyManager.FEATURE_ALL);
        Supplier<PushProviders> pushProviders =
                () -> new TestPushProviders(testRuntimeConfig.getConfigOptions());

        UAirship.application = this;
        UAirship.isFlying = true;
        UAirship.isTakingOff = true;

        UAirship.sharedAirship = new UAirship(airshipConfigOptions);
        UAirship.sharedAirship.preferenceDataStore = preferenceDataStore;
        UAirship.sharedAirship.localeManager = new LocaleManager(this, preferenceDataStore);
        UAirship.sharedAirship.runtimeConfig = testRuntimeConfig;

        UAirship.sharedAirship.permissionsManager = PermissionsManager.newPermissionsManager(this);
        UAirship.sharedAirship.channel = new AirshipChannel(this, preferenceDataStore, UAirship.sharedAirship.runtimeConfig, privacyManager, UAirship.sharedAirship.localeManager);

        UAirship.sharedAirship.analytics = new Analytics(this, preferenceDataStore, testRuntimeConfig, privacyManager, UAirship.sharedAirship.channel, UAirship.sharedAirship.localeManager, UAirship.sharedAirship.permissionsManager);
        UAirship.sharedAirship.applicationMetrics = new ApplicationMetrics(this, preferenceDataStore, privacyManager, new TestActivityMonitor());
        UAirship.sharedAirship.pushManager = new PushManager(this, preferenceDataStore, testRuntimeConfig, privacyManager, pushProviders, UAirship.sharedAirship.channel, UAirship.sharedAirship.analytics, UAirship.sharedAirship.permissionsManager);
        UAirship.sharedAirship.channelCapture = new ChannelCapture(this, airshipConfigOptions, UAirship.sharedAirship.channel, preferenceDataStore, new TestActivityMonitor());
        UAirship.sharedAirship.urlAllowList = UrlAllowList.createDefaultUrlAllowList(airshipConfigOptions);
        UAirship.sharedAirship.actionRegistry = new ActionRegistry();
        UAirship.sharedAirship.actionRegistry.registerDefaultActions(this);
        UAirship.sharedAirship.remoteData = new RemoteData(this, preferenceDataStore, testRuntimeConfig, privacyManager, UAirship.sharedAirship.pushManager, UAirship.sharedAirship.localeManager, pushProviders);
        UAirship.sharedAirship.remoteConfigManager = new RemoteConfigManager(this, preferenceDataStore, testRuntimeConfig, privacyManager, UAirship.sharedAirship.remoteData);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        preferenceDataStore.tearDown();
    }

    public void setPlatform(int platform) {
        testRuntimeConfig.setPlatform(PlatformUtils.parsePlatform(platform));
    }

    public void setPrivacyManager(PrivacyManager privacyManager) {
        UAirship.shared().privacyManager = privacyManager;
    }

    public void setApplicationMetrics(ApplicationMetrics metrics) {
        UAirship.shared().applicationMetrics = metrics;
    }

    public void setContact(Contact contact) {
        UAirship.shared().contact = contact;
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
    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
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
