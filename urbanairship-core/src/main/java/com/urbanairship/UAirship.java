/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.actions.DeepLinkListener;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.base.Supplier;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.config.RemoteAirshipUrlConfigProvider;
import com.urbanairship.contacts.Contact;
import com.urbanairship.images.DefaultImageLoader;
import com.urbanairship.images.ImageLoader;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.modules.Module;
import com.urbanairship.modules.Modules;
import com.urbanairship.modules.accengage.AccengageModule;
import com.urbanairship.modules.accengage.AccengageNotificationHandler;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.modules.location.LocationModule;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.push.PushManager;
import com.urbanairship.remoteconfig.RemoteConfigManager;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.util.AppStoreUtils;
import com.urbanairship.util.ProcessUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.content.pm.PackageInfoCompat;

/**
 * UAirship manages the shared state for all Airship
 * services. UAirship.takeOff() should be called to initialize
 * the class during <code>Application.onCreate()</code> or
 * by using {@link Autopilot}.
 */
public class UAirship {

    /**
     * Broadcast that is sent when UAirship is finished taking off.
     */
    @NonNull
    public static final String ACTION_AIRSHIP_READY = "com.urbanairship.AIRSHIP_READY";

    @NonNull
    public static final String EXTRA_CHANNEL_ID_KEY = "channel_id";

    @NonNull
    public static final String EXTRA_PAYLOAD_VERSION_KEY = "payload_version";

    @NonNull
    public static final String EXTRA_APP_KEY_KEY = "app_key";

    @NonNull
    public static final String EXTRA_AIRSHIP_DEEP_LINK_SCHEME = "uairship";

    @NonNull
    private static final String APP_SETTINGS_DEEP_LINK_HOST = "app_settings";

    @NonNull
    private static final String APP_STORE_DEEP_LINK_HOST = "app_store";

    @IntDef({ AMAZON_PLATFORM, ANDROID_PLATFORM, UNKNOWN_PLATFORM })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Platform {
    }

    /**
     * Amazon platform type. Only ADM transport will be allowed.
     */
    public static final int AMAZON_PLATFORM = 1;

    /**
     * Android platform type. Only FCM/HMS transport will be allowed.
     */
    public static final int ANDROID_PLATFORM = 2;

    /**
     * Unknown platform. Returns if all features have been disabled in {@link PrivacyManager}.
     */
    public static final int UNKNOWN_PLATFORM = -1;

    private final static Object airshipLock = new Object();
    volatile static boolean isFlying = false;
    volatile static boolean isTakingOff = false;
    volatile static boolean isMainProcess = false;

    static Application application;
    static UAirship sharedAirship;

    /**
     * Flag to enable printing take off's stacktrace. Useful when debugging exceptions related
     * to take off not being called first.
     */
    public static boolean LOG_TAKE_OFF_STACKTRACE = false;

    private static final List<CancelableOperation> pendingAirshipRequests = new ArrayList<>();

    private static boolean queuePendingAirshipRequests = true;

    private DeepLinkListener deepLinkListener;
    private final Map<Class, AirshipComponent> componentClassMap = new HashMap<>();

    List<AirshipComponent> components = new ArrayList<>();
    ActionRegistry actionRegistry;
    AirshipConfigOptions airshipConfigOptions;
    Analytics analytics;
    @SuppressWarnings("deprecation")
    ApplicationMetrics applicationMetrics;
    PreferenceDataStore preferenceDataStore;
    PushManager pushManager;
    AirshipChannel channel;
    AirshipLocationClient locationClient;
    UrlAllowList urlAllowList;
    RemoteData remoteData;
    RemoteConfigManager remoteConfigManager;
    ChannelCapture channelCapture;
    NamedUser namedUser;
    ImageLoader imageLoader;
    AccengageNotificationHandler accengageNotificationHandler;
    AirshipRuntimeConfig runtimeConfig;
    LocaleManager localeManager;
    PrivacyManager privacyManager;
    Contact contact;
    PermissionsManager permissionsManager;

    /**
     * Constructs an instance of UAirship.
     *
     * @param airshipConfigOptions The airship config options.
     * @hide
     */
    UAirship(@NonNull AirshipConfigOptions airshipConfigOptions) {
        this.airshipConfigOptions = airshipConfigOptions;
    }

    /**
     * Returns the shared UAirship singleton instance. This method will block
     * until airship is ready.
     *
     * @return The UAirship singleton.
     * @throws IllegalStateException if takeoff is not called prior to this method.
     */
    @NonNull
    public static UAirship shared() {
        synchronized (airshipLock) {
            if (!isTakingOff && !isFlying) {
                throw new IllegalStateException("Take off must be called before shared()");
            }

            //noinspection ConstantConditions
            return waitForTakeOff(0);
        }
    }

    /**
     * Requests the airship instance asynchronously.
     * <p>
     * This method calls through to {@link #shared(android.os.Looper, com.urbanairship.UAirship.OnReadyCallback)}
     * with a null looper.
     *
     * @param callback An optional callback
     * @return A cancelable object that can be used to cancel the callback.
     */
    @NonNull
    public static Cancelable shared(@NonNull OnReadyCallback callback) {
        return shared(null, callback);
    }

    /**
     * Waits for UAirship to takeOff and be ready.
     *
     * @param millis Time to wait for UAirship to be ready in milliseconds or {@code 0} to wait
     * forever.
     * @return The ready UAirship instance, or {@code null} if UAirship
     * is not ready by the specified wait time.
     * @hide
     */
    @Nullable
    public static UAirship waitForTakeOff(long millis) {
        synchronized (airshipLock) {
            if (isFlying) {
                return sharedAirship;
            }

              /*
                 From https://developer.android.com/reference/java/lang/Object.html#wait(long)

                 A thread can also wake up without being notified, interrupted, or timing out, a
                 so-called spurious wakeup. While this will rarely occur in practice, applications must
                 guard against it by testing for the condition that should have caused the thread to be
                 awakened, and continuing to wait if the condition is not satisfied.
             */

            try {
                if (millis > 0) {
                    long remainingTime = millis;
                    long startTime = SystemClock.elapsedRealtime();
                    while (!isFlying && remainingTime > 0) {
                        airshipLock.wait(remainingTime);
                        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
                        remainingTime = millis - elapsedTime;
                    }
                } else {
                    while (!isFlying) {
                        airshipLock.wait();
                    }
                }

                if (isFlying) {
                    return sharedAirship;
                }
            } catch (InterruptedException ignored) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
            }

            return null;
        }
    }

    /**
     * Requests the airship instance asynchronously.
     * <p>
     * If airship is ready, the callback will not be called immediately, the callback is still
     * dispatched to the specified looper. The blocking shared may unblock before any of the
     * asynchronous callbacks are executed.
     *
     * @param looper A Looper object whose message queue will be used for the callback,
     * or null to make callbacks on the calling thread or main thread if the current thread
     * does not have a looper associated with it.
     * @param callback An optional callback
     * @return A cancelable object that can be used to cancel the callback.
     */
    @NonNull
    public static Cancelable shared(@Nullable Looper looper, @NonNull final OnReadyCallback callback) {
        CancelableOperation cancelableOperation = new CancelableOperation(looper) {
            @Override
            public void onRun() {
                //noinspection ConstantConditions
                if (callback != null) {
                    callback.onAirshipReady(shared());
                }
            }
        };

        synchronized (pendingAirshipRequests) {
            if (queuePendingAirshipRequests) {
                pendingAirshipRequests.add(cancelableOperation);
            } else {
                cancelableOperation.run();
            }
        }

        return cancelableOperation;
    }

    /**
     * Take off with config loaded from the {@code airshipconfig.properties} file in the
     * assets directory. See {@link com.urbanairship.AirshipConfigOptions.Builder#applyDefaultProperties(Context)}.
     *
     * @param application The application (required)
     */
    @MainThread
    public static void takeOff(@NonNull Application application) {
        takeOff(application, null, null);
    }

    /**
     * Take off with a callback to perform airship configuration after
     * takeoff. The ready callback will be executed before the UAirship instance is returned by any
     * of the shared methods. The config will be loaded from {@code airshipconfig.properties} file in the
     * assets directory. See {@link com.urbanairship.AirshipConfigOptions.Builder#applyDefaultProperties(Context)}.
     *
     * @param application The application (required)
     * @param readyCallback Optional ready callback. The callback will be triggered on a background thread
     * that performs {@code takeOff}. If the callback takes longer than ~5 seconds it could cause ANRs within
     * the application.
     */
    @MainThread
    public static void takeOff(@NonNull Application application, @Nullable OnReadyCallback readyCallback) {
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
    @MainThread
    public static void takeOff(@NonNull Application application, @Nullable AirshipConfigOptions options) {
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
     * @param readyCallback Optional ready callback. The callback will be triggered on a background thread
     * that performs {@code takeOff}. If the callback takes longer than ~5 seconds it could cause ANRs within
     * the application.
     */
    @MainThread
    public static void takeOff(@NonNull final Application application, @Nullable final AirshipConfigOptions options, @Nullable final OnReadyCallback readyCallback) {
        // noinspection ConstantConditions
        if (application == null) {
            throw new IllegalArgumentException("Application argument must not be null");
        }

        if (Looper.myLooper() == null || Looper.getMainLooper() != Looper.myLooper()) {
            Logger.error("takeOff() must be called on the main thread!");
        }

        isMainProcess = ProcessUtils.isMainProcess(application);
        AirshipAppBootstrap.init(application);

        if (LOG_TAKE_OFF_STACKTRACE) {
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement element : new Exception().getStackTrace()) {
                sb.append("\n\tat ");
                sb.append(element.toString());
            }

            Logger.debug("Takeoff stack trace: %s", sb.toString());
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

            AirshipExecutors.threadPoolExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    executeTakeOff(application, options, readyCallback);
                }
            });
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
    private static void executeTakeOff(@NonNull Application application, @Nullable AirshipConfigOptions options, @Nullable OnReadyCallback readyCallback) {
        if (options == null) {
            options = new AirshipConfigOptions.Builder()
                    .applyDefaultProperties(application.getApplicationContext())
                    .build();
        }

        options.validate();

        Logger.setLogLevel(options.logLevel);
        Logger.setTag(UAirship.getAppName() + " - " + Logger.DEFAULT_TAG);

        Logger.info("Airship taking off!");
        Logger.info("Airship log level: %s", options.logLevel);
        Logger.info("UA Version: %s / App key = %s Production = %s", getVersion(), options.appKey, options.inProduction);
        Logger.verbose(BuildConfig.SDK_VERSION);

        sharedAirship = new UAirship(options);

        synchronized (airshipLock) {
            // IMPORTANT! Make sure we set isFlying before calling the readyCallback callback or
            // initializing any of the modules to prevent shared from deadlocking or adding
            // another pendingAirshipRequests.
            isFlying = true;
            isTakingOff = false;

            // Initialize the modules
            sharedAirship.init();

            Logger.info("Airship ready!");

            // Ready callback for setup
            if (readyCallback != null) {
                readyCallback.onAirshipReady(sharedAirship);
            }

            // Notify each component that airship is ready
            for (AirshipComponent component : sharedAirship.getComponents()) {
                component.onAirshipReady(sharedAirship);
            }

            // Fire any pendingAirshipRequests
            synchronized (pendingAirshipRequests) {
                queuePendingAirshipRequests = false;
                for (Runnable pendingRequest : pendingAirshipRequests) {
                    pendingRequest.run();
                }
                pendingAirshipRequests.clear();
            }

            // Send AirshipReady intent for other plugins that depend on Airship
            Intent readyIntent = new Intent(ACTION_AIRSHIP_READY)
                    .setPackage(UAirship.getPackageName())
                    .addCategory(UAirship.getPackageName());

            if (sharedAirship.runtimeConfig.getConfigOptions().extendedBroadcastsEnabled) {
                readyIntent.putExtra(EXTRA_CHANNEL_ID_KEY, sharedAirship.channel.getId());
                readyIntent.putExtra(EXTRA_APP_KEY_KEY, sharedAirship.runtimeConfig.getConfigOptions().appKey);
                readyIntent.putExtra(EXTRA_PAYLOAD_VERSION_KEY, 1);
            }

            application.sendBroadcast(readyIntent);

            // Notify any blocking shared
            airshipLock.notifyAll();
        }
    }

    /**
     * Cleans up and closes any connections or other resources.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static void land() {
        synchronized (airshipLock) {
            if (!isTakingOff && !isFlying) {
                return;
            }

            // Block until takeoff is finished
            UAirship airship = UAirship.shared();

            airship.tearDown();

            isFlying = false;
            isTakingOff = false;
            sharedAirship = null;
            application = null;
            queuePendingAirshipRequests = true;
        }
    }

    /**
     * Sets the deep link listener.
     *
     * @param listener the deep link listener.
     */
    public void setDeepLinkListener(@Nullable DeepLinkListener listener) {
        deepLinkListener = listener;
    }

    /**
     * Returns the Application's package name.
     *
     * @return The package name.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    @SuppressLint("UnknownNullness")
    public static String getPackageName() {
        return getApplicationContext().getPackageName();
    }

    /**
     * Returns the Application's package manager.
     *
     * @return The package manager.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    @NonNull
    public static PackageManager getPackageManager() {
        return getApplicationContext().getPackageManager();
    }

    /**
     * Returns the deep link listener if one has been set, otherwise null.
     *
     * @return The deep link listener.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    @Nullable
    public DeepLinkListener getDeepLinkListener() {
        return deepLinkListener;
    }

    /**
     * Returns the Application's <code>PackageInfo</code>
     *
     * @return The PackageInfo for this Application
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    @Nullable
    public static PackageInfo getPackageInfo() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Logger.warn(e, "UAirship - Unable to get package info.");
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
    @SuppressLint("UnknownNullness")
    public static ApplicationInfo getAppInfo() {
        return getApplicationContext().getApplicationInfo();
    }

    /**
     * Returns the current Application's name. Wraps
     * PackageManager's <code>getApplicationLabel()</code> method.
     *
     * @return The current Application's name
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    @SuppressLint("UnknownNullness")
    public static String getAppName() {
        if (getAppInfo() != null) {
            return getPackageManager().getApplicationLabel(getAppInfo()).toString();
        } else {
            return "";
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
     * Returns the current Application version.
     *
     * @return The version, or -1 if the package cannot be read.
     */
    public static long getAppVersion() {
        PackageInfo packageInfo = UAirship.getPackageInfo();

        if (packageInfo != null) {
            return PackageInfoCompat.getLongVersionCode(packageInfo);
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
    @NonNull
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
     * Tests if the current process is the main process.
     *
     * @return <code>true</code> if currently within the main process; <code>false</code> otherwise.
     */
    public static boolean isMainProcess() {
        return isMainProcess;
    }

    /**
     * Gets the image loader.
     *
     * @return The image loader.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public ImageLoader getImageLoader() {
        if (imageLoader == null) {
            imageLoader = new DefaultImageLoader(getApplicationContext());
        }
        return imageLoader;
    }

    /**
     * Airship runtime config.
     *
     * @return The Airship runtime config.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public AirshipRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    /**
     * Sets the image loader.
     *
     * @param imageLoader The image loader.
     */
    public void setImageLoader(@NonNull ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    /**
     * Returns the current Airship version.
     *
     * @return The Airship version number.
     */
    @NonNull
    public static String getVersion() {
        return BuildConfig.AIRSHIP_VERSION;
    }

    /**
     * Initializes UAirship instance.
     */
    private void init() {

        // Create and init the preference data store first
        this.preferenceDataStore = PreferenceDataStore.loadDataStore(getApplicationContext(), airshipConfigOptions);

        this.privacyManager = new PrivacyManager(preferenceDataStore, airshipConfigOptions.enabledFeatures);
        this.privacyManager.migrateData();


        this.permissionsManager = PermissionsManager.newPermissionsManager(application);

        this.localeManager = new LocaleManager(application, preferenceDataStore);

        Supplier<PushProviders> pushProviders = PushProviders.lazyLoader(application, airshipConfigOptions);

        DeferredPlatformProvider platformProvider = new DeferredPlatformProvider(getApplicationContext(), preferenceDataStore, privacyManager, pushProviders);
        RemoteAirshipUrlConfigProvider remoteAirshipUrlConfigProvider = new RemoteAirshipUrlConfigProvider(airshipConfigOptions, preferenceDataStore);
        this.runtimeConfig = new AirshipRuntimeConfig(platformProvider, airshipConfigOptions, remoteAirshipUrlConfigProvider);
        remoteAirshipUrlConfigProvider.addUrlConfigListener(new AirshipUrlConfig.Listener() {
            @Override
            public void onUrlConfigUpdated() {
                for (AirshipComponent component : components) {
                    component.onUrlConfigUpdated();
                }
            }
        });

        this.channel = new AirshipChannel(application, preferenceDataStore, runtimeConfig, privacyManager, localeManager);

        if (channel.getId() == null && "huawei".equalsIgnoreCase(Build.MANUFACTURER)) {
            remoteAirshipUrlConfigProvider.disableFallbackUrls();
        }

        components.add(channel);

        this.urlAllowList = UrlAllowList.createDefaultUrlAllowList(airshipConfigOptions);
        this.actionRegistry = new ActionRegistry();
        this.actionRegistry.registerDefaultActions(getApplicationContext());

        // Airship components
        this.analytics = new Analytics(application, preferenceDataStore, runtimeConfig, privacyManager, channel, localeManager, permissionsManager);
        components.add(this.analytics);

        //noinspection deprecation
        this.applicationMetrics = new ApplicationMetrics(application, preferenceDataStore, privacyManager);
        components.add(this.applicationMetrics);

        this.pushManager = new PushManager(application, preferenceDataStore, runtimeConfig, privacyManager, pushProviders, channel, analytics, permissionsManager);
        components.add(this.pushManager);

        this.channelCapture = new ChannelCapture(application, airshipConfigOptions, channel, preferenceDataStore, GlobalActivityMonitor.shared(application));
        components.add(this.channelCapture);

        this.remoteData = new RemoteData(application, preferenceDataStore, runtimeConfig, privacyManager, pushManager, localeManager, pushProviders);
        components.add(this.remoteData);

        this.remoteConfigManager = new RemoteConfigManager(application, preferenceDataStore, runtimeConfig, privacyManager, remoteData);
        this.remoteConfigManager.addRemoteAirshipConfigListener(remoteAirshipUrlConfigProvider);
        components.add(this.remoteConfigManager);

        this.contact = new Contact(application, preferenceDataStore, runtimeConfig, privacyManager, channel);
        components.add(this.contact);

        //noinspection deprecation
        this.namedUser = new NamedUser(application, preferenceDataStore, contact);
        components.add(this.namedUser);

        // Debug
        Module debugModule = Modules.debug(application, preferenceDataStore);
        processModule(debugModule);

        // Accengage
        AccengageModule accengageModule = Modules.accengage(application, airshipConfigOptions, preferenceDataStore, privacyManager, channel, pushManager);
        processModule(accengageModule);
        this.accengageNotificationHandler = accengageModule == null ? null : accengageModule.getAccengageNotificationHandler();

        // Message Center
        Module messageCenterModule = Modules.messageCenter(application, preferenceDataStore, privacyManager, channel, pushManager, getAirshipConfigOptions());
        processModule(messageCenterModule);

        // Location
        LocationModule locationModule = Modules.location(application, preferenceDataStore, privacyManager, channel, permissionsManager);
        processModule(locationModule);
        this.locationClient = locationModule == null ? null : locationModule.getLocationClient();

        // Automation
        Module automationModule = Modules.automation(application, preferenceDataStore, runtimeConfig,
                privacyManager, channel, pushManager, analytics, remoteData, contact);
        processModule(automationModule);

        // Ad Id
        Module adIdModule = Modules.adId(application, preferenceDataStore, runtimeConfig, privacyManager, analytics);
        processModule(adIdModule);

        // Chat
        Module chat = Modules.chat(application, preferenceDataStore, runtimeConfig, privacyManager, channel, pushManager);
        processModule(chat);

        // Preference Center
        Module preferenceCenter = Modules.preferenceCenter(application, preferenceDataStore, privacyManager, remoteData);
        processModule(preferenceCenter);

        for (AirshipComponent component : components) {
            component.init();
        }
    }

    private void processModule(@Nullable Module module) {
        if (module != null) {
            components.addAll(module.getComponents());
            module.registerActions(application, getActionRegistry());
        }
    }

    /**
     * Tears down the UAirship instance.
     */
    private void tearDown() {
        for (AirshipComponent component : getComponents()) {
            component.tearDown();
        }

        // Teardown the preference data store last
        preferenceDataStore.tearDown();
    }

    /**
     * Returns the current configuration options.
     *
     * @return The current configuration options.
     */
    @NonNull
    public AirshipConfigOptions getAirshipConfigOptions() {
        return airshipConfigOptions;
    }

    /**
     * Returns the {@link com.urbanairship.channel.NamedUser} instance.
     *
     * @return The {@link com.urbanairship.channel.NamedUser} instance.
     * @deprecated Use {@link Contact} instead.
     */
    @NonNull
    @Deprecated
    public NamedUser getNamedUser() {
        return namedUser;
    }

    /**
     * Returns the {@link com.urbanairship.push.PushManager} instance.
     *
     * @return The {@link com.urbanairship.push.PushManager} instance.
     */
    @NonNull
    public PushManager getPushManager() {
        return pushManager;
    }

    /**
     * Returns the {@link com.urbanairship.channel.AirshipChannel} instance.
     *
     * @return The {@link com.urbanairship.channel.AirshipChannel} instance.
     */
    @NonNull
    public AirshipChannel getChannel() {
        return channel;
    }

    /**
     * Returns the {@link AirshipLocationClient} instance.
     *
     * @return The {@link AirshipLocationClient} instance.
     * @hide
     */
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AirshipLocationClient getLocationClient() {
        return locationClient;
    }

    /**
     * Returns the RemoteData instance.
     *
     * @return The RemoteData instance.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public RemoteData getRemoteData() {
        return remoteData;
    }

    /**
     * Returns the UAirship {@link com.urbanairship.analytics.Analytics} instance.
     *
     * @return The {@link com.urbanairship.analytics.Analytics} instance.
     */
    @NonNull
    public Analytics getAnalytics() {
        return analytics;
    }

    /**
     * Returns the UAirship {@link com.urbanairship.permission.PermissionsManager} instance.
     *
     * @return The {@link com.urbanairship.permission.PermissionsManager} instance.
     */
    @NonNull
    public PermissionsManager getPermissionsManager() {
        return permissionsManager;
    }

    /**
     * Returns the {@link com.urbanairship.ApplicationMetrics} instance.
     *
     * @return The {@link com.urbanairship.ApplicationMetrics} instance.
     */
    @NonNull
    public ApplicationMetrics getApplicationMetrics() {
        return applicationMetrics;
    }

    /**
     * The URL allow list is used to determine if a URL is allowed to be used for various features, including:
     * Airship JS interface, open external URL action, wallet action, HTML in-app messages, and landing pages.
     *
     * @return The urlAllowList.
     */
    @NonNull
    public UrlAllowList getUrlAllowList() {
        return urlAllowList;
    }

    /**
     * The default Action Registry.
     *
     * @return The action registry.
     */
    @NonNull
    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    /**
     * Returns the {@link com.urbanairship.ChannelCapture} instance.
     *
     * @return The {@link com.urbanairship.ChannelCapture} instance.
     */
    @NonNull
    public ChannelCapture getChannelCapture() {
        return channelCapture;
    }

    /**
     * Returns the Accengage instance if available.
     *
     * @return The Accengage instance.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Nullable
    public AccengageNotificationHandler getAccengageNotificationHandler() {
        return accengageNotificationHandler;
    }

    /**
     * Returns the {@link Contact} instance.
     *
     * @return The {@link Contact} instance.
     */
    @NonNull
    public Contact getContact() {
        return contact;
    }

    /**
     * Returns the platform type.
     *
     * @return {@link #AMAZON_PLATFORM} for Amazon, {@link #ANDROID_PLATFORM} for Android (FCM/HMS),
     * or {@link #UNKNOWN_PLATFORM} if the platform has not been resolved in the past and all features
     * in the SDK are opted out.
     */
    @Platform
    public int getPlatformType() {
        return runtimeConfig.getPlatform();
    }

    /**
     * Returns a list of all the top level airship components.
     *
     * @return The list of all the top level airship components.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<AirshipComponent> getComponents() {
        return components;
    }

    /**
     * Gets an AirshipComponent by class.
     *
     * @param clazz The component class.
     * @return The component, or null if not found.
     * @hide
     */
    @SuppressWarnings("unchecked")
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public <T extends AirshipComponent> T getComponent(@NonNull Class<T> clazz) {
        AirshipComponent found = null;

        AirshipComponent cached = componentClassMap.get(clazz);
        if (cached != null) {
            found = cached;
        } else {
            for (AirshipComponent component : components) {
                if (component.getClass().equals(clazz)) {
                    found = component;
                    componentClassMap.put(clazz, found);
                    break;
                }
            }
        }

        if (found != null) {
            return (T) found;
        }

        return null;
    }

    /**
     * Gets an AirshipComponent by class or throws an exception if there is no AirshipComponent for the class.
     *
     * @param clazz The component class.
     * @return The component.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public <T extends AirshipComponent> T requireComponent(@NonNull Class<T> clazz) {
        T component = getComponent(clazz);
        if (component == null) {
            throw new IllegalArgumentException("Unable to find component " + clazz);
        }
        return component;
    }

    /**
     * Deep links. If the deep link is an `uairship://` it will be handled internally by the SDK.
     * All other deep links will be forwarded to the deep link listener.
     *
     * @param deepLink The deep link.
     * @return {@code true} if the deep link was handled, otherwise {@code false}.
     */
    @MainThread
    public boolean deepLink(@NonNull String deepLink) {
        Uri uri = Uri.parse(deepLink);
        if (EXTRA_AIRSHIP_DEEP_LINK_SCHEME.equals(uri.getScheme())) {
            if (handleAirshipDeeplink(uri, getApplicationContext())) {
                return true;
            }

            for (AirshipComponent component : getComponents()) {
                if (component.onAirshipDeepLink(uri)) {
                    return true;
                }
            }

            Logger.debug("Airship deep link not handled: %s", deepLink);
            return true;
        } else {
            DeepLinkListener deepLinkListener = getDeepLinkListener();
            return deepLinkListener != null && deepLinkListener.onDeepLink(deepLink);
        }
    }

    /**
     * Handle the Airship deep links for app_settings and app_store.
     * @param uri The deep link Uri.
     * @param context The application context.
     * @return {@code true} if the deep link was handled, otherwise {@code false}.
     */
    private boolean handleAirshipDeeplink(@NonNull Uri uri, @NonNull Context context) {
        switch (uri.getEncodedAuthority()) {
            case APP_SETTINGS_DEEP_LINK_HOST: {
                Intent appSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(appSettingsIntent);
                return true;
            }
            case APP_STORE_DEEP_LINK_HOST: {
                Intent appStoreIntent = AppStoreUtils.getAppStoreIntent(context, getPlatformType(), getAirshipConfigOptions())
                                                     .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(appStoreIntent);
                return true;
            }
            default:
                return false;
        }
    }

    /**
     * Deprecated. Use {@link PrivacyManager} instead.
     *
     * When enabled it will enable all privacy manager features. When disabled it will disable
     * all.
     *
     * @param enabled {@code true} to enable, {@code false} to disable.
     * @deprecated Enable/disable by using {@link PrivacyManager}.
     * This will enable or disable {@link PrivacyManager#FEATURE_ALL} features.
     */
    public void setDataCollectionEnabled(boolean enabled) {
        if (enabled) {
            this.privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_ALL);
        } else {
            this.privacyManager.setEnabledFeatures(PrivacyManager.FEATURE_NONE);
        }
    }

    /**
     * Deprecated. Use {@link PrivacyManager} instead.
     *
     * Checks if any features are enabled in the privacy manager.
     *
     * @return {@code true} if any feature is enabled, otherwise `false`.
     * @deprecated Enable/disable by using {@link PrivacyManager}.
     * This will call through to the privacy manager to check if any features are enabled.
     */
    @Deprecated
    public boolean isDataCollectionEnabled() {
        return privacyManager.isAnyFeatureEnabled();
    }

    /**
     * Returns the privacy manager.
     *
     * @return The privacy manager.
     */
    @NonNull
    public PrivacyManager getPrivacyManager() {
        return privacyManager;
    }

    /**
     * Sets a locale to be stored in UAirship.
     *
     * @param locale The new locale to use.
     */
    public void setLocaleOverride(@Nullable Locale locale) {
        this.localeManager.setLocaleOverride(locale);
    }

    /**
     * Get the locale stored in UAirship.
     *
     * @return The locale stored in UAirship, if none, return the default Locale from the device.
     */
    public Locale getLocale() {
        return this.localeManager.getLocale();
    }

    /**
     * Returns the {@link com.urbanairship.locale.LocaleManager} instance.
     *
     * @return The {@link com.urbanairship.locale.LocaleManager} instance.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public LocaleManager getLocaleManager() {
        return localeManager;
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
        void onAirshipReady(@NonNull UAirship airship);

    }


}
