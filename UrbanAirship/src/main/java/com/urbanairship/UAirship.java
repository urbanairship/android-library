/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;

import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.amazon.ADMUtils;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.google.GCMUtils;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.js.Whitelist;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.richpush.RichPushManager;
import com.urbanairship.util.ManifestUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * UAirship manages the shared state for all Urban Airship
 * services. UAirship.takeOff() should be called to initialize
 * the class on <code>Application.onCreate()</code>.
 */
public class UAirship {

    /**
     * Amazon platform type. Only ADM transport will be allowed.
     */
    public static final int AMAZON_PLATFORM = 1;

    /**
     * Android platform type. Only GCM transport will be allowed.
     */
    public static final int ANDROID_PLATFORM = 2;

    /**
     * Platform preference key.
     */
    private static final String PLATFORM_KEY = "com.urbanairship.application.device.PLATFORM";

    /**
     * Library version key
     */
    private static final String LIBRARY_VERSION_KEY = "com.urbanairship.application.device.LIBRARY_VERSION";

    private final static Object airshipLock = new Object();
    volatile static boolean isFlying = false;
    volatile static boolean isTakingOff = false;
    static Application application;
    static UAirship sharedAirship;

    private static List<CancelableOperation> pendingAirshipRequests;

    ActionRegistry actionRegistry;
    AirshipConfigOptions airshipConfigOptions;
    Analytics analytics;
    ApplicationMetrics applicationMetrics;
    PreferenceDataStore preferenceDataStore;
    PushManager pushManager;
    RichPushManager richPushManager;
    UALocationManager locationManager;
    Whitelist whitelist;

    /**
     * Constructs an instance of UAirship.
     *
     * @param context The application context.
     * @param airshipConfigOptions The airship config options.
     * @param preferenceDataStore The preference data store.
     * @Hide
     */
    UAirship(Context context, AirshipConfigOptions airshipConfigOptions, PreferenceDataStore preferenceDataStore) {
        this.airshipConfigOptions = airshipConfigOptions;
        this.preferenceDataStore = preferenceDataStore;

        this.analytics = new Analytics(context, preferenceDataStore, airshipConfigOptions);
        this.applicationMetrics = new ApplicationMetrics(context, preferenceDataStore);
        this.richPushManager = new RichPushManager(preferenceDataStore);
        this.locationManager = new UALocationManager(context, preferenceDataStore);
        this.pushManager = new PushManager(context, preferenceDataStore);
        this.whitelist = Whitelist.createDefaultWhitelist(airshipConfigOptions);
        this.actionRegistry = new ActionRegistry();
    }

    /**
     * Returns the shared UAirship singleton instance. This method will block
     * until airship is ready.
     *
     * @return The UAirship singleton.
     * @throws IllegalStateException if takeoff is not called prior to this method.
     */
    public static UAirship shared() {
        synchronized (airshipLock) {
            if (isFlying) {
                return sharedAirship;
            }

            if (!isTakingOff) {
                throw new IllegalStateException("Take off must be called before shared()");
            }

            try {
                airshipLock.wait();
            } catch (InterruptedException e) {
                Logger.error("Failed to wait for UAirship instance.", e);
            }

            if (!isFlying) {
                return null;
            } else {
                return sharedAirship;
            }
        }
    }

    /**
     * Requests the airship instance asynchronously.
     * <p/>
     * This method calls through to {@link #shared(com.urbanairship.UAirship.OnReadyCallback, android.os.Looper)}
     * with a null looper.
     *
     * @param callback An optional callback
     * @return A cancelable object that can be used to cancel the callback.
     */
    public static Cancelable shared(OnReadyCallback callback) {
        return shared(callback, null);
    }

    /**
     * Requests the airship instance asynchronously.
     * <p/>
     * If airship is ready, the callback will not be called immediately, the callback is still
     * dispatched to the specified looper. The blocking shared may unblock before any of the
     * asynchronous callbacks are executed.
     *
     * @param callback An optional callback
     * @param looper A Looper object whose message queue will be used for the callback,
     * or null to make callbacks on the calling thread or main thread if the current thread
     * does not have a looper associated with it.
     * @return A cancelable object that can be used to cancel the callback.
     */
    public static Cancelable shared(final OnReadyCallback callback, Looper looper) {
        CancelableOperation cancelableOperation = new CancelableOperation(looper) {
            @Override
            public void onRun() {
                UAirship airship = shared();
                if (callback != null && airship != null) {
                    callback.onAirshipReady(airship);
                }
            }
        };

        synchronized (airshipLock) {
            if (isFlying) {
                cancelableOperation.run();
            } else {
                if (pendingAirshipRequests == null) {
                    pendingAirshipRequests = new ArrayList<>();
                }
                pendingAirshipRequests.add(cancelableOperation);
            }
        }

        return cancelableOperation;
    }

    /**
     * Take off with options defined in airshipconfig.properties
     *
     * @param application The application (required)
     */
    public static void takeOff(Application application) {
        takeOff(application, null, null);
    }

    /**
     * Take off with airshipconfig.properties and a callback to perform airship configuration after
     * takeoff. The ready callback will be executed before the UAirship instance is returned by any
     * of the shared methods.
     *
     * @param application The application (required)
     * @param readyCallback Optional ready callback.
     */
    public static void takeOff(Application application, OnReadyCallback readyCallback) {
        takeOff(application, null, readyCallback);
    }

    /**
     * Take off with defined AirshipConfigOptions.
     *
     * @param application The application (required)
     * @param options The launch options. If not null, the options passed in here
     * will override the options loaded from the <code>.properties</code> file. This parameter
     * is useful for specifying options at runtime.
     */
    public static void takeOff(Application application, AirshipConfigOptions options) {
        takeOff(application, options, null);
    }

    /**
     * Take off with a callback to perform airship configuration after takeoff. The
     * ready callback will be executed before the UAirship instance is returned by any of the shared
     * methods.
     *
     * @param application The application (required)
     * @param options The launch options. If not null, the options passed in here
     * will override the options loaded from the <code>.properties</code> file. This parameter
     * is useful for specifying options at runtime.
     * @param readyCallback Optional ready callback.
     */
    public static void takeOff(final Application application, final AirshipConfigOptions options, final OnReadyCallback readyCallback) {
        // the context argument is crucial to pretty much everything
        if (application == null) {
            throw new IllegalArgumentException("Application argument must not be null");
        }

        if (Looper.myLooper() != null && Looper.getMainLooper() == Looper.myLooper()) {
            // Workaround for https://code.google.com/p/android/issues/detail?id=20915.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                try {
                    Class.forName("android.os.AsyncTask");
                } catch (ClassNotFoundException e) {
                    Logger.error("AsyncTask workaround failed.", e);
                }
            }

        } else  {
            // In 6.0, we should throw an illegal state exception.
            // throw new IllegalStateException("takeOff() must be called on the main thread!");
            Logger.error("takeOff() must be called on the main thread!");
        }

        synchronized (airshipLock) {
            // airships only take off once!!
            if (isFlying || isTakingOff) {
                Logger.error("You can only call takeOff() once.");
                return;
            }

            Logger.info("Airship taking off!");

            isTakingOff = true;

            UAirship.application = application;
            UrbanAirshipProvider.init();

            Analytics.registerLifeCycleCallbacks(application);

            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    executeTakeOff(application, options, readyCallback);
                }
            });

            thread.start();
        }
    }

    /**
     * Actually performs takeOff. This is called from takeOff on a background thread.
     *
     * @param application The application (required)
     * @param options The launch options. If not null, the options passed in here will override the
     * options loaded from the <code>.properties</code> file. This parameter is useful for specifying options at runtime.
     * @param readyCallback Optional ready callback.
     */
    private static void executeTakeOff(Application application, AirshipConfigOptions options, OnReadyCallback readyCallback) {
        if (options == null) {
            options = AirshipConfigOptions.loadDefaultOptions(application.getApplicationContext());
        }

        if (!options.isValid()) {
            synchronized (airshipLock) {
                isTakingOff = false;
                airshipLock.notifyAll();
            }

            Logger.error("AirshipConfigOptions are not valid. Unable to take off! " +
                    "Check your airshipconfig.properties file for the errors listed above.");
            throw new IllegalArgumentException("Application configuration is invalid.");
        }

        // set sane log level based on production flag
        Logger.logLevel = options.getLoggerLevel();
        Logger.TAG = UAirship.getAppName() + " - UALib";

        Logger.info("Airship taking off!");
        Logger.info("Airship log level: " + Logger.logLevel);
        Logger.info("UA Version: " + getVersion() + " / App key = " + options.getAppKey() + " Production = " + options.inProduction);

        PreferenceDataStore preferenceDataStore = new PreferenceDataStore(application.getApplicationContext());
        preferenceDataStore.loadAll();

        sharedAirship = new UAirship(application.getApplicationContext(), options, preferenceDataStore);
        sharedAirship.init();

        String currentVersion = getVersion();
        String previousVersion = preferenceDataStore.getString(LIBRARY_VERSION_KEY, null);

        if (previousVersion != null && !previousVersion.equals(currentVersion)) {
            Logger.info("Urban Airship library changed from " + previousVersion +
                    " to " + currentVersion + ".");
        }

        // store current version as library version once check is performed
        preferenceDataStore.put(LIBRARY_VERSION_KEY, getVersion());

        // if in development mode, check the manifest and log manifest issues
        if (!options.inProduction) {
            sharedAirship.validateManifest();
        }

        synchronized (airshipLock) {
            // IMPORTANT! Make sure we set isFlying before calling the readyCallback callback to prevent
            // shared from deadlocking or adding another pendingAirshipRequests.
            isFlying = true;
            isTakingOff = false;

            Logger.info("Airship ready!");

            // Ready callback for setup
            if (readyCallback != null) {
                readyCallback.onAirshipReady(sharedAirship);
            }

            // Fire any pendingAirshipRequests
            if (pendingAirshipRequests != null) {
                List<CancelableOperation> pendingRequests = new ArrayList<>(pendingAirshipRequests);
                for (Runnable pendingRequest : pendingRequests) {
                    pendingRequest.run();
                }
                pendingAirshipRequests = null;
            }


            // Notify any blocking shared
            airshipLock.notifyAll();
        }
    }

    /**
     * Cleans up and closes any connections or other resources.
     */
    static void land() {
        synchronized (airshipLock) {
            if (!isTakingOff && !isFlying) {
                return;
            }

            Analytics.unregisterLifeCycleCallbacks(application);

            // Block until takeoff is finished
            UAirship airship = UAirship.shared();

            if (airship != null) {
                airship.tearDown();
            }

            isFlying = false;
            isTakingOff = false;
            sharedAirship = null;
            application = null;
        }
    }

    /**
     * Returns the Application's package name.
     *
     * @return The package name.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    public static String getPackageName() {
        return application.getPackageName();
    }

    /**
     * Returns the permission for sending Urban Airship push and registration broadcasts.
     *
     * @return The Urban Airship broadcast permission.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    public static String getUrbanAirshipPermission() {
        return application.getPackageName() + ".permission.UA_DATA";
    }

    /**
     * Returns the Application's package manager.
     *
     * @return The package manager.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    public static PackageManager getPackageManager() {
        return application.getPackageManager();
    }

    /**
     * Returns the Application's <code>PackageInfo</code>
     *
     * @return The PackageInfo for this Application
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    public static PackageInfo getPackageInfo() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.warn("UAirship - Unable to get package info.", e);
            return null;
        }
    }

    /**
     * Returns the current Application's ApplicationInfo. Wraps
     * PackageManager's <code>getApplicationInfo()</code> method.
     *
     * @return The shared ApplicationInfo object for this application.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    public static ApplicationInfo getAppInfo() {
        return application.getApplicationInfo();
    }

    /**
     * Returns the current Application's name. Wraps
     * PackageManager's <code>getApplicationLabel()</code> method.
     *
     * @return The current Application's name
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    public static String getAppName() {
        if (getAppInfo() != null) {
            return getPackageManager().getApplicationLabel(getAppInfo()).toString();
        } else {
            return null;
        }
    }

    /**
     * Returns the drawable ID for the current Application's icon.
     *
     * @return The drawable ID for the application's icon, or -1 if the ID cannot be found.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    public static int getAppIcon() {
        // ensure that the appInfo exists - there are some exceptional situations where the
        // package manager is confused and think that the app is not currently installed
        ApplicationInfo appInfo = getAppInfo();
        if (appInfo != null) {
            return appInfo.icon;
        } else {
            return -1;
        }
    }

    /**
     * Returns the current Application's context.
     *
     * @return The current application Context.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    public static Context getApplicationContext() {
        if (application == null) {
            throw new IllegalStateException("TakeOff must be called first.");
        }

        return application.getApplicationContext();
    }

    /**
     * Tests if UAirship has been initialized and is ready for use.
     *
     * @return <code>true</code> if UAirship is ready for use; <code>false</code> otherwise
     */
    public static boolean isFlying() {
        return isFlying;
    }

    /**
     * Tests if UAirship is currently taking off.
     *
     * @return <code>true</code> if UAirship is taking off; <code>false</code> otherwise
     */
    public static boolean isTakingOff() {
        return isTakingOff;
    }

    /**
     * Returns the current Urban Airship version.
     *
     * @return The Urban Airship version number.
     */
    public static String getVersion() {
        return BuildConfig.URBAN_AIRSHIP_VERSION;
    }

    /**
     * Initializes UAirship instance.
     */
    private void init() {
        // Initialize the managers
        ((BaseManager) this.richPushManager).init();
        ((BaseManager) this.pushManager).init();
        ((BaseManager) this.locationManager).init();

        this.actionRegistry.registerDefaultActions();
    }

    /**
     * Tears down the UAirship instance.
     */
    private void tearDown() {
        // Tear down the managers
        ((BaseManager) this.richPushManager).tearDown();
        ((BaseManager) this.pushManager).tearDown();
        ((BaseManager) this.locationManager).tearDown();
    }

    /**
     * Returns the current configuration options.
     *
     * @return The current configuration options.
     */
    public AirshipConfigOptions getAirshipConfigOptions() {
        return airshipConfigOptions;
    }

    /**
     * Returns the PushManager instance.
     *
     * @return The PushManager instance.
     */
    public PushManager getPushManager() {
        return pushManager;
    }

    /**
     * Returns the RichPushManager instance.
     *
     * @return The RichPushManager instance.
     */
    public RichPushManager getRichPushManager() {
        return richPushManager;
    }

    /**
     * Returns the UALocationManager instance.
     *
     * @return The UALocationManager instance.
     */
    public UALocationManager getLocationManager() {
        return locationManager;
    }

    /**
     * Returns the UAirship {@link com.urbanairship.analytics.Analytics} instance.
     *
     * @return The analytics instance.
     */
    public Analytics getAnalytics() {
        return analytics;
    }

    /**
     * Returns the {@link com.urbanairship.ApplicationMetrics} instance.
     *
     * @return The application metrics instance.
     */
    public ApplicationMetrics getApplicationMetrics() {
        return applicationMetrics;
    }

    /**
     * The URL whiteList used to determine when to inject the Urban Airship Javascript Interface.
     *
     * @return The url whitelist.
     */
    public Whitelist getWhitelist() {
        return whitelist;
    }

    /**
     * The default Action Registry.
     */
    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    /**
     * Returns the platform type. The platform type is determined only once
     * by the first statement that applies:
     * <p/>
     * <ol>
     * <li>Amazon if ADM is available</li>
     * <li>Android if Play Store is installed</li>
     * <li>Amazon if Manufacturer is "AMAZON"</li>
     * <li>Android</li>
     * </ol>
     *
     * @return {@value #AMAZON_PLATFORM} for Amazon or {@value #ANDROID_PLATFORM}
     * for Android.
     */
    public int getPlatformType() {
        int platform = preferenceDataStore.getInt(PLATFORM_KEY, -1);

        if (platform == -1) {
            if (ADMUtils.isADMAvailable()) {
                Logger.info("ADM available. Setting platform to Amazon.");
                platform = AMAZON_PLATFORM;
            } else if (PlayServicesUtils.isGooglePlayStoreAvailable()) {
                Logger.info("Google Play Store available. Setting platform to Android.");
                platform = ANDROID_PLATFORM;
            } else if ("amazon".equalsIgnoreCase(Build.MANUFACTURER)) {
                Logger.info("Build.MANUFACTURER is AMAZON. Setting platform to Amazon.");
                platform = AMAZON_PLATFORM;
            } else {
                Logger.info("Defaulting platform to Android.");
                platform = ANDROID_PLATFORM;
            }

            preferenceDataStore.put(PLATFORM_KEY, platform);
        }

        return platform;
    }

    /**
     * Logs any issues with the manifest.
     */
    private void validateManifest() {
        ManifestUtils.validateManifest(airshipConfigOptions);

        switch (sharedAirship.getPlatformType()) {
            case ANDROID_PLATFORM:
                if (airshipConfigOptions.isTransportAllowed(AirshipConfigOptions.GCM_TRANSPORT)) {
                    GCMUtils.validateManifest(airshipConfigOptions);
                } else {
                    Logger.error("Android platform detected but GCM transport is disabled. " +
                            "The device will not be able to receive push notifications.");
                }
                break;

            case AMAZON_PLATFORM:
                if (airshipConfigOptions.isTransportAllowed(AirshipConfigOptions.ADM_TRANSPORT)) {
                    ADMUtils.validateManifest();
                } else {
                    Logger.error("Amazon platform detected but ADM transport is disabled. " +
                            "The device will not be able to receive push notifications.");
                }
                break;
        }
    }

    /**
     * Callback interface used to notify app when UAirship is ready.
     */
    public interface OnReadyCallback {

        /**
         * Called when UAirship is ready.
         *
         * @param airship The UAirship instance.
         */
        public void onAirshipReady(UAirship airship);
    }
}
