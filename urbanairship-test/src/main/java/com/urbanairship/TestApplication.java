/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.app.Application;

import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.analytics.AirshipEventFeed;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.audience.AudienceOverridesProvider;
import com.urbanairship.base.Supplier;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrar;
import com.urbanairship.contacts.Contact;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.PlatformUtils;

import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;

import androidx.test.core.app.ApplicationProvider;

@SuppressLint("VisibleForTests")
public class TestApplication extends Application implements TestLifecycleApplication {

    public ActivityLifecycleCallbacks callback;
    public PreferenceDataStore preferenceDataStore;

    public TestAirshipRuntimeConfig testRuntimeConfig;

    public static com.urbanairship.TestApplication getApplication() {
        return (com.urbanairship.TestApplication) ApplicationProvider.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        testRuntimeConfig = new TestAirshipRuntimeConfig();
        final AirshipConfigOptions airshipConfigOptions = testRuntimeConfig.getConfigOptions();

        this.preferenceDataStore = PreferenceDataStore.inMemoryStore(getApplicationContext());

        JobDispatcher dispatcher = new JobDispatcher(this, (context, jobInfo, delay) -> {});
        JobDispatcher.setInstance(dispatcher);

        PrivacyManager privacyManager = new PrivacyManager(preferenceDataStore, PrivacyManager.Feature.ALL);
        Supplier<PushProviders> pushProviders =
                () -> new TestPushProviders(testRuntimeConfig.getConfigOptions());

        UAirship.application = this;
        UAirship.isFlying = true;
        UAirship.isTakingOff = true;

        AudienceOverridesProvider audienceOverridesProvider = new AudienceOverridesProvider();
        UAirship.sharedAirship = new UAirship(airshipConfigOptions);
        UAirship.sharedAirship.preferenceDataStore = preferenceDataStore;
        UAirship.sharedAirship.localeManager = new LocaleManager(this, preferenceDataStore);
        UAirship.sharedAirship.runtimeConfig = testRuntimeConfig;
        UAirship.sharedAirship.permissionsManager = new PermissionsManager(this);
        ChannelRegistrar channelRegistrar = new ChannelRegistrar(getApplicationContext(), preferenceDataStore, testRuntimeConfig);
        UAirship.sharedAirship.channel = new AirshipChannel(this, preferenceDataStore, UAirship.sharedAirship.runtimeConfig, privacyManager, UAirship.sharedAirship.permissionsManager, UAirship.sharedAirship.localeManager, audienceOverridesProvider, channelRegistrar);

        UAirship.sharedAirship.analytics = new Analytics(
                this, preferenceDataStore, testRuntimeConfig, privacyManager, UAirship.sharedAirship.channel, UAirship.sharedAirship.localeManager, UAirship.sharedAirship.permissionsManager,
                new AirshipEventFeed(privacyManager, true)
        );
        UAirship.sharedAirship.applicationMetrics = new ApplicationMetrics(this, preferenceDataStore, privacyManager, new TestActivityMonitor());
        UAirship.sharedAirship.pushManager = new PushManager(this, preferenceDataStore, testRuntimeConfig, privacyManager, pushProviders, UAirship.sharedAirship.channel, UAirship.sharedAirship.analytics, UAirship.sharedAirship.permissionsManager);
        UAirship.sharedAirship.channelCapture = new ChannelCapture(this, airshipConfigOptions, UAirship.sharedAirship.channel, preferenceDataStore, new TestActivityMonitor());
        UAirship.sharedAirship.urlAllowList = UrlAllowList.createDefaultUrlAllowList(airshipConfigOptions);
        UAirship.sharedAirship.actionRegistry = new ActionRegistry();
        UAirship.sharedAirship.actionRegistry.registerDefaultActions(this);
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
