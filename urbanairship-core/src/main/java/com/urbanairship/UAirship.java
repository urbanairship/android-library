/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.actions.DeepLinkListener;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.data.EventApiClient;
import com.urbanairship.analytics.data.EventManager;
import com.urbanairship.analytics.data.EventResolver;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.automation.Automation;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.iam.InAppActivityMonitor;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.iam.LegacyInAppMessageManager;
import com.urbanairship.images.DefaultImageLoader;
import com.urbanairship.images.ImageLoader;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.js.Whitelist;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushProvider;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.remoteconfig.RemoteConfigManager;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.util.PlatformUtils;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * UAirship manages the shared state for all Airship
 * services. UAirship.takeOff() should be called to initialize
 * the class on <code>Application.onCreate()</code>.
 */
public class UAirship {

    private static final String[] OPTIONAL_COMPONENTS = {
            "com.urbanairship.aaid.AdvertisingIdTracker",
            "com.urbanairship.debug.DebugManager"
    };

    /**
     * Broadcast that is sent when UAirship is finished taking off.
     */
    @NonNull
    public static final String ACTION_AIRSHIP_READY = "com.urbanairship.AIRSHIP_READY";

    @IntDef({ AMAZON_PLATFORM, ANDROID_PLATFORM })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Platform {}

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
     * Push provider class preference key.
     */
    private static final String PROVIDER_CLASS_KEY = "com.urbanairship.application.device.PUSH_PROVIDER";

    /**
     * Library version key
     */
    private static final String LIBRARY_VERSION_KEY = "com.urbanairship.application.device.LIBRARY_VERSION";

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

    final List<AirshipComponent> components = new ArrayList<>();
    ActionRegistry actionRegistry;
    AirshipConfigOptions airshipConfigOptions;
    Analytics analytics;
    ApplicationMetrics applicationMetrics;
    PreferenceDataStore preferenceDataStore;
    PushProvider pushProvider;
    PushManager pushManager;
    RichPushInbox inbox;
    AirshipChannel channel;
    UALocationManager locationManager;
    Whitelist whitelist;
    InAppMessageManager inAppMessageManager;
    LegacyInAppMessageManager legacyInAppMessageManager;
    RemoteData remoteData;
    RemoteConfigManager remoteConfigManager;
    ChannelCapture channelCapture;
    MessageCenter messageCenter;
    NamedUser namedUser;
    Automation automation;
    ImageLoader imageLoader;

    @Platform
    int platform;

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

            AirshipExecutors.THREAD_POOL_EXECUTOR.execute(new Runnable() {
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

            // Send AirshipReady intent for other plugins that depend on UA
            Intent readyIntent = new Intent(ACTION_AIRSHIP_READY)
                    .setPackage(UAirship.getPackageName())
                    .addCategory(UAirship.getPackageName());

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
    public static int getAppVersion() {
        PackageInfo packageInfo = UAirship.getPackageInfo();

        if (packageInfo != null) {
            return packageInfo.versionCode;
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
    public ImageLoader getImageLoader() {
        if (imageLoader == null) {
            imageLoader = new DefaultImageLoader(getApplicationContext());
        }
        return imageLoader;
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
        this.preferenceDataStore = new PreferenceDataStore(application);
        this.preferenceDataStore.init();

        PushProviders providers = PushProviders.load(application, airshipConfigOptions);

        this.platform = determinePlatform(providers);
        this.pushProvider = determinePushProvider(platform, providers);

        if (this.pushProvider != null) {
            Logger.info("Using push provider: %s", this.pushProvider);
        }

        TagGroupRegistrar tagGroupRegistrar = new TagGroupRegistrar(platform, airshipConfigOptions, preferenceDataStore);
        tagGroupRegistrar.migrateKeys();

        this.channel = new AirshipChannel(application, preferenceDataStore, airshipConfigOptions, platform, tagGroupRegistrar);
        components.add(channel);

        this.whitelist = Whitelist.createDefaultWhitelist(airshipConfigOptions);
        this.actionRegistry = new ActionRegistry();
        this.actionRegistry.registerDefaultActions(getApplicationContext());

        // Airship components
        this.analytics = Analytics.newBuilder(application)
                                  .setActivityMonitor(GlobalActivityMonitor.shared(application))
                                  .setConfigOptions(airshipConfigOptions)
                                  .setPreferenceDataStore(preferenceDataStore)
                                  .setAirshipChannel(channel)
                                  .setEventManager(EventManager.newBuilder()
                                                               .setEventResolver(new EventResolver(application))
                                                               .setActivityMonitor(GlobalActivityMonitor.shared(application))
                                                               .setJobDispatcher(JobDispatcher.shared(application))
                                                               .setPreferenceDataStore(preferenceDataStore)
                                                               .setApiClient(new EventApiClient(application))
                                                               .setBackgroundReportingIntervalMS(airshipConfigOptions.backgroundReportingIntervalMS)
                                                               .setJobAction(Analytics.ACTION_SEND)
                                                               .build())
                                  .build();
        components.add(this.analytics);

        this.applicationMetrics = new ApplicationMetrics(application, preferenceDataStore, GlobalActivityMonitor.shared(application));
        components.add(this.applicationMetrics);

        this.inbox = new RichPushInbox(application, preferenceDataStore, channel);
        components.add(this.inbox);

        this.locationManager = new UALocationManager(application, preferenceDataStore, GlobalActivityMonitor.shared(application));
        components.add(this.locationManager);

        this.pushManager = new PushManager(application, preferenceDataStore, airshipConfigOptions, this.pushProvider, channel);
        components.add(this.pushManager);

        this.namedUser = new NamedUser(application, preferenceDataStore, tagGroupRegistrar, channel);
        components.add(this.namedUser);

        this.channelCapture = new ChannelCapture(application, airshipConfigOptions, channel, preferenceDataStore, GlobalActivityMonitor.shared(application));
        components.add(this.channelCapture);

        this.messageCenter = new MessageCenter(application, preferenceDataStore);
        components.add(this.messageCenter);

        this.automation = new Automation(application, preferenceDataStore, airshipConfigOptions, analytics, GlobalActivityMonitor.shared(application));
        components.add(this.automation);

        this.remoteData = new RemoteData(application, preferenceDataStore, airshipConfigOptions, GlobalActivityMonitor.shared(application));
        components.add(this.remoteData);

        this.remoteConfigManager = new RemoteConfigManager(application, preferenceDataStore, this.remoteData);
        components.add(this.remoteConfigManager);

        this.inAppMessageManager = new InAppMessageManager(application, preferenceDataStore, airshipConfigOptions,
                analytics, this.remoteData, InAppActivityMonitor.shared(application), channel, tagGroupRegistrar);
        components.add(this.inAppMessageManager);

        this.legacyInAppMessageManager = new LegacyInAppMessageManager(application, preferenceDataStore, this.inAppMessageManager, this.analytics);
        components.add(this.legacyInAppMessageManager);

        for (String className : OPTIONAL_COMPONENTS) {
            AirshipComponent component = createOptionalComponent(className, application, preferenceDataStore);
            if (component != null) {
                components.add(component);
            }
        }

        for (AirshipComponent component : components) {
            component.init();
        }

        // Store the version
        String currentVersion = getVersion();
        String previousVersion = preferenceDataStore.getString(LIBRARY_VERSION_KEY, null);

        if (previousVersion != null && !previousVersion.equals(currentVersion)) {
            Logger.info("Airship library changed from %s to %s.", previousVersion, currentVersion);
        }

        // store current version as library version once check is performed
        this.preferenceDataStore.put(LIBRARY_VERSION_KEY, getVersion());
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
     */
    @NonNull
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
     * Returns the {@link com.urbanairship.richpush.RichPushInbox} instance.
     *
     * @return The {@link com.urbanairship.richpush.RichPushInbox} instance.
     */
    @NonNull
    public RichPushInbox getInbox() {
        return inbox;
    }

    /**
     * Returns the {@link com.urbanairship.location.UALocationManager} instance.
     *
     * @return The {@link com.urbanairship.location.UALocationManager} instance.
     */
    @NonNull
    public UALocationManager getLocationManager() {
        return locationManager;
    }

    /**
     * Returns the legacy {@link com.urbanairship.iam.LegacyInAppMessageManager} instance
     *
     * @return The legacy {@link com.urbanairship.iam.LegacyInAppMessageManager} instance.
     */
    @NonNull
    public LegacyInAppMessageManager getLegacyInAppMessageManager() {
        return legacyInAppMessageManager;
    }

    /**
     * Returns the {@link com.urbanairship.iam.InAppMessageManager} instance.
     *
     * @return The {@link com.urbanairship.iam.InAppMessageManager} instance.
     */
    @NonNull
    public InAppMessageManager getInAppMessagingManager() {
        return inAppMessageManager;
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
     * Returns the {@link com.urbanairship.ApplicationMetrics} instance.
     *
     * @return The {@link com.urbanairship.ApplicationMetrics} instance.
     */
    @NonNull
    public ApplicationMetrics getApplicationMetrics() {
        return applicationMetrics;
    }

    /**
     * The URL whitelist is used to determine if a URL is allowed to be used for various features, including:
     * Airship JS interface, open external URL action, wallet action, HTML in-app messages, and landing pages.
     *
     * @return The url whitelist.
     */
    @NonNull
    public Whitelist getWhitelist() {
        return whitelist;
    }

    /**
     * The default Action Registry.
     */
    @NonNull
    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    /**
     * The default Message Center.
     *
     * @return The default message center.
     */
    @NonNull
    public MessageCenter getMessageCenter() {
        return messageCenter;
    }

    /**
     * Returns the {@link com.urbanairship.automation.Automation} instance.
     *
     * @return The {@link com.urbanairship.automation.Automation} instance.
     */
    @NonNull
    public Automation getAutomation() {
        return automation;
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
     * Returns the platform type.
     *
     * @return {@link #AMAZON_PLATFORM} for Amazon or {@link #ANDROID_PLATFORM}
     * for Android.
     */
    @Platform
    public int getPlatformType() {
        return platform;
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
    @Nullable
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AirshipComponent getComponent(Class<? extends AirshipComponent> clazz) {
        for (AirshipComponent component : components) {
            if (component.getClass().equals(clazz)) {
                return component;
            }
        }
        return null;
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

    /**
     * Determines which push provider to use for the given platform.
     *
     * @param platform The providers platform.
     * @param providers The available providers.
     * @return The platform's best provider, or {@code null}.
     */
    @Nullable
    private PushProvider determinePushProvider(@Platform int platform, @NonNull PushProviders providers) {
        // Existing provider class
        String existingProviderClass = preferenceDataStore.getString(PROVIDER_CLASS_KEY, null);

        // Try to use the same provider
        if (!UAStringUtil.isEmpty(existingProviderClass)) {
            PushProvider provider = providers.getProvider(platform, existingProviderClass);
            if (provider != null) {
                return provider;
            }
        }

        // Find the best provider for the platform
        PushProvider provider = providers.getBestProvider(platform);
        if (provider != null) {
            preferenceDataStore.put(PROVIDER_CLASS_KEY, provider.getClass().toString());
        }

        return provider;
    }

    /**
     * Determines the platform on the device.
     *
     * @param providers The push providers.
     * @return The device platform.
     */
    @Platform
    private int determinePlatform(@NonNull PushProviders providers) {
        // Existing platform
        int existingPlatform = preferenceDataStore.getInt(PLATFORM_KEY, -1);
        if (PlatformUtils.isPlatformValid(existingPlatform)) {
            return PlatformUtils.parsePlatform(existingPlatform);
        }

        int platform;

        PushProvider bestProvider = providers.getBestProvider();
        if (bestProvider != null) {
            platform = PlatformUtils.parsePlatform(bestProvider.getPlatform());
            Logger.info("Setting platform to %s for push provider: %s", PlatformUtils.asString(platform), bestProvider);
        } else if (PlayServicesUtils.isGooglePlayStoreAvailable(getApplicationContext())) {
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
        return PlatformUtils.parsePlatform(platform);
    }

    @Nullable
    private AirshipComponent createOptionalComponent(String className, Context context, PreferenceDataStore dataStore) {
        try {
            Class<?> componentClass = Class.forName(className);
            Constructor<?> cons = componentClass.getConstructor(Context.class, PreferenceDataStore.class);
            Object component = cons.newInstance(context, dataStore);
            if (component instanceof AirshipComponent) {
                return (AirshipComponent) component;
            }
        } catch (InstantiationException e) {
            Logger.error(e, "Unable to create component %s", className);
        } catch (IllegalAccessException e) {
            Logger.error(e, "Unable to create component %s", className);
        } catch (NoSuchMethodException e) {
            Logger.error(e, "Unable to create component %s", className);
        } catch (InvocationTargetException e) {
            Logger.error(e, "Unable to create component %s", className);
        } catch (ClassNotFoundException e) {
            return null;
        }

        return null;
    }

}
