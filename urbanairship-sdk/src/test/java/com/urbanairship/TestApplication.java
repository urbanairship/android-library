/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentProvider;

import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.automation.Automation;
import com.urbanairship.js.Whitelist;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.push.NamedUser;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.iam.InAppMessageManager;
import com.urbanairship.richpush.RichPushInbox;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.TestLifecycleApplication;
import org.robolectric.shadows.ShadowContentResolver;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

public class TestApplication extends Application implements TestLifecycleApplication {

    public ActivityLifecycleCallbacks callback;
    public PreferenceDataStore preferenceDataStore;

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

        AirshipConfigOptions airshipConfigOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("app_key")
                .setDevelopmentAppSecret("app_secret")
                .setInProduction(false)
                .build();

        setPlatform(UAirship.ANDROID_PLATFORM);

        UAirship.application = this;
        UAirship.isFlying = true;
        UAirship.isTakingOff = true;

        ContentProvider provider = new UrbanAirshipProvider();
        provider.onCreate();

        UAirship.sharedAirship = new UAirship(airshipConfigOptions);
        UAirship.sharedAirship.preferenceDataStore = preferenceDataStore;
        UAirship.sharedAirship.analytics = new Analytics(this, preferenceDataStore, airshipConfigOptions, UAirship.ANDROID_PLATFORM, ActivityMonitor.shared(getApplicationContext()));
        UAirship.sharedAirship.applicationMetrics = new ApplicationMetrics(this, preferenceDataStore, ActivityMonitor.shared(getApplicationContext()));
        UAirship.sharedAirship.inbox = new RichPushInbox(this, preferenceDataStore, ActivityMonitor.shared(getApplicationContext()));
        UAirship.sharedAirship.locationManager = new UALocationManager(this, preferenceDataStore, ActivityMonitor.shared(getApplicationContext()));
        UAirship.sharedAirship.inAppMessageManager = new InAppMessageManager(preferenceDataStore, ActivityMonitor.shared(getApplicationContext()));
        UAirship.sharedAirship.pushManager = new PushManager(this, preferenceDataStore, airshipConfigOptions);
        UAirship.sharedAirship.channelCapture = new ChannelCapture(this, airshipConfigOptions, UAirship.sharedAirship.pushManager, preferenceDataStore, ActivityMonitor.shared(getApplicationContext()));
        UAirship.sharedAirship.whitelist = Whitelist.createDefaultWhitelist(airshipConfigOptions);
        UAirship.sharedAirship.actionRegistry = new ActionRegistry();
        UAirship.sharedAirship.actionRegistry.registerDefaultActions();
        UAirship.sharedAirship.messageCenter = new MessageCenter();
        UAirship.sharedAirship.namedUser = new NamedUser(this, preferenceDataStore);
        UAirship.sharedAirship.automation = new Automation(this, airshipConfigOptions, UAirship.sharedAirship.analytics, preferenceDataStore, ActivityMonitor.shared(getApplicationContext()));

        ShadowContentResolver.registerProvider(UrbanAirshipProvider.getAuthorityString(this), provider);
    }

    public void setPlatform(int platform) {
        preferenceDataStore.put("com.urbanairship.application.device.PLATFORM", platform);
    }

    public static TestApplication getApplication() {
        return (TestApplication) RuntimeEnvironment.application;
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

    @Override
    public void afterTest(Method method) {
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

    public void setPushManager(PushManager pushManager) {
        UAirship.shared().pushManager = pushManager;
    }

    public void setLocationManager(UALocationManager locationManager) {
        UAirship.shared().locationManager = locationManager;
    }

    public void setInbox(RichPushInbox inbox) {
        UAirship.shared().inbox = inbox;
    }

    public void setAutomation(Automation automation) {
        UAirship.shared().automation = automation;
    }

    public void setChannelCapture(ChannelCapture channelCapture) {
        UAirship.shared().channelCapture = channelCapture;
    }
}
