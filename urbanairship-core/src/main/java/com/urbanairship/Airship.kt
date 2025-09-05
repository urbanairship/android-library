/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.Airship.Companion.shared
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.actions.DeepLinkListener
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.audience.AudienceEvaluator
import com.urbanairship.audience.AudienceOverridesProvider
import com.urbanairship.cache.AirshipCache
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.channel.ChannelRegistrar
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.config.RemoteConfigObserver
import com.urbanairship.contacts.Contact
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.http.DefaultRequestSession
import com.urbanairship.http.RequestException
import com.urbanairship.images.AirshipGlideImageLoader
import com.urbanairship.images.ImageLoader
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.inputvalidation.DefaultInputValidator
import com.urbanairship.locale.LocaleManager
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.modules.Module
import com.urbanairship.modules.Modules
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.push.PushManager
import com.urbanairship.remoteconfig.RemoteConfigManager
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.util.AppStoreUtils
import com.urbanairship.util.ProcessUtils
import java.util.Locale
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Airship manages the shared state for all Airship
 * services. Airship.takeOff() should be called to initialize
 * the class during `Application.onCreate()` or
 * by using [Autopilot].
 *
 */
public class Airship @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) @VisibleForTesting public constructor(
    airshipConfigOptions: AirshipConfigOptions
) {

    public enum class Platform(internal val rawValue: Int) {
        /**
         * Amazon platform type. Only ADM transport will be allowed.
         */
        AMAZON(1),

        /**
         * Android platform type. Only FCM/HMS transport will be allowed.
         */
        ANDROID(2),

        /**
         * Unknown platform. Returns if all features have been disabled in [PrivacyManager].
         */
        UNKNOWN(-1);

        public val stringValue: String
            get() {
                return when(this) {
                    AMAZON -> "amazon"
                    ANDROID -> "android"
                    UNKNOWN -> "unknown"
                }
            }

        internal val deviceType: String
            get() {
                when(this) {
                    UNKNOWN -> throw RequestException("Invalid platform")
                    else -> return this.stringValue
                }
            }

        internal companion object {
            fun fromRawValue(rawValue: Int): Platform {
                return entries.find { it.rawValue == rawValue } ?: UNKNOWN
            }
        }
    }

    /**
     * Returns the deep link listener if one has been set, otherwise null.
     * @throws java.lang.IllegalStateException if takeOff has not been called.
     */
    @JvmField
    public var deepLinkListener: DeepLinkListener? = null

    /**
     * Returns the current configuration options.
     */
    public var airshipConfigOptions: AirshipConfigOptions = airshipConfigOptions
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    private val componentClassMap = mutableMapOf<Class<*>, AirshipComponent>()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    internal val components = MutableStateFlow<List<AirshipComponent>>(emptyList())

    /**
     * The default Action Registry.
     */
    public lateinit var actionRegistry: ActionRegistry
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    /**
     * The Airship [com.urbanairship.analytics.Analytics] instance.
     */
    public lateinit var analytics: Analytics
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    /**
     * The [com.urbanairship.ApplicationMetrics] instance.
     */
    @Suppress("deprecation")
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public lateinit var applicationMetrics: ApplicationMetrics

    public lateinit var preferenceDataStore: PreferenceDataStore
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public set

    /**
     * The [com.urbanairship.push.PushManager] instance.
     */
    public lateinit var pushManager: PushManager
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    /**
     * The [com.urbanairship.channel.AirshipChannel] instance.
     */
    public lateinit var channel: AirshipChannel
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    /**
     * The URL allow list is used to determine if a URL is allowed to be used for various features, including:
     * Airship JS interface, open external URL action, wallet action, HTML in-app messages, and landing pages.
     */
    public lateinit var urlAllowList: UrlAllowList
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    internal lateinit var remoteData: RemoteData
        private set

    private lateinit var remoteConfigManager: RemoteConfigManager

    /**
     * The input validation used by Preference Center and Scenes.
     */
    public lateinit var inputValidator: AirshipInputValidation.Validator
        private set

    private lateinit var meteredUsageManager: AirshipMeteredUsage

    /**
     * The [com.urbanairship.ChannelCapture] instance.
     */
    public lateinit var channelCapture: ChannelCapture
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    private var imageLoader: ImageLoader? = null

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public lateinit var runtimeConfig: AirshipRuntimeConfig

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public lateinit var localeManager: LocaleManager

    /**
     * The privacy manager.
     */
    public lateinit var privacyManager: PrivacyManager
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    /**
     * The [Contact] instance.
     */
    public lateinit var contact: Contact
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public set

    /**
     * The Airship [com.urbanairship.permission.PermissionsManager] instance.
     */
    public lateinit var permissionsManager: PermissionsManager
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) @VisibleForTesting
        public set
    private lateinit var experimentManager: ExperimentManager

    /**
     * Gets the image loader.
     *
     * @return The image loader.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getImageLoader(): ImageLoader {
        return imageLoader ?: run {
            val result = AirshipGlideImageLoader
            imageLoader = result
            result
        }
    }

    /**
     * Initializes Airship instance.
     */
    private fun init() {
        // Create and init the preference data store first

        this.preferenceDataStore = PreferenceDataStore.loadDataStore(
            context = applicationContext,
            configOptions = airshipConfigOptions
        )

        val remoteConfigObserver = RemoteConfigObserver(preferenceDataStore)

        this.privacyManager = PrivacyManager(
            dataStore = preferenceDataStore,
            defaultEnabledFeatures = airshipConfigOptions.enabledFeatures,
            configObserver = remoteConfigObserver,
            resetEnabledFeatures = airshipConfigOptions.resetEnabledFeatures
        )

        val application = application ?: throw IllegalStateException("Airship not initialized")
        this.permissionsManager = PermissionsManager(application)

        this.localeManager = LocaleManager(
            context = application,
            preferenceDataStore = preferenceDataStore
        )

        val pushProviders = PushProviders.lazyLoader(application, airshipConfigOptions)

        val audienceOverridesProvider = AudienceOverridesProvider()
        val platformProvider = DeferredPlatformProvider(
            context = application,
            dataStore = preferenceDataStore,
            privacyManager = privacyManager,
            pushProviders = pushProviders
        )

        val requestSession = DefaultRequestSession(airshipConfigOptions, platformProvider)
        runtimeConfig = AirshipRuntimeConfig(
            configOptionsProvider = { airshipConfigOptions },
            requestSession = requestSession,
            configObserver = remoteConfigObserver,
            platformProvider = platformProvider
        )
        val channelRegistrar = ChannelRegistrar(
            context = applicationContext,
            dataStore = preferenceDataStore,
            runtimeConfig = runtimeConfig,
            privacyManager = privacyManager
        )

        channel = AirshipChannel(
            context = application,
            dataStore = preferenceDataStore,
            runtimeConfig = runtimeConfig,
            privacyManager = privacyManager,
            permissionsManager = permissionsManager,
            localeManager = localeManager,
            audienceOverridesProvider = audienceOverridesProvider,
            channelRegistrar = channelRegistrar
        )
        requestSession.channelAuthTokenProvider = channel.authTokenProvider
        val localComponents = mutableListOf<AirshipComponent>(channel)

        this.urlAllowList = UrlAllowList.createDefaultUrlAllowList(airshipConfigOptions)
        this.actionRegistry = ActionRegistry().also { it.registerDefaultActions(applicationContext) }

        val eventFeed = AirshipEventFeed(
            privacyManager = privacyManager,
            isAnalyticsEnabled = airshipConfigOptions.analyticsEnabled
        )

        // Airship components
        this.analytics = Analytics(
            context = application,
            dataStore = preferenceDataStore,
            runtimeConfig = runtimeConfig,
            privacyManager = privacyManager,
            channel = channel,
            localeManager = localeManager,
            permissionsManager = permissionsManager,
            eventFeed = eventFeed
        )
        localComponents.add(analytics)

        this.inputValidator = DefaultInputValidator(runtimeConfig)

        this.applicationMetrics = ApplicationMetrics(
            context = application,
            preferenceDataStore = preferenceDataStore,
            privacyManager = privacyManager
        )
        localComponents.add(applicationMetrics)

        this.pushManager = PushManager(
            context = application,
            preferenceDataStore = preferenceDataStore,
            config = runtimeConfig,
            privacyManager = privacyManager,
            pushProvidersSupplier = pushProviders,
            airshipChannel = channel,
            analytics = analytics,
            permissionsManager = permissionsManager
        )
        localComponents.add(pushManager)

        this.channelCapture = ChannelCapture(
            context = application,
            configOptions = airshipConfigOptions,
            airshipChannel = channel,
            preferenceDataStore = preferenceDataStore,
            activityMonitor = GlobalActivityMonitor.shared(application)
        )
        localComponents.add(channelCapture)

        this.contact = Contact(
            context = application,
            preferenceDataStore = preferenceDataStore,
            config = runtimeConfig,
            privacyManager = privacyManager,
            airshipChannel = channel,
            localeManager = localeManager,
            audienceOverridesProvider = audienceOverridesProvider,
            pushManager = pushManager,
            smsValidator = inputValidator
        )
        localComponents.add(contact)

        requestSession.contactAuthTokenProvider = contact.authTokenProvider

        val deferredResolver = DeferredResolver(runtimeConfig, audienceOverridesProvider)

        this.remoteData = RemoteData(
            context = application,
            config = runtimeConfig,
            preferenceDataStore = preferenceDataStore,
            privacyManager = privacyManager,
            localeManager = localeManager,
            pushManager = pushManager,
            pushProviders = pushProviders,
            contact = contact
        )
        localComponents.add(remoteData)

        this.meteredUsageManager = AirshipMeteredUsage(
            context = application,
            dataStore = preferenceDataStore,
            config = runtimeConfig,
            privacyManager = privacyManager,
            contact = contact
        )
        localComponents.add(meteredUsageManager)

        this.remoteConfigManager = RemoteConfigManager(
            context = application,
            dataStore = preferenceDataStore,
            runtimeConfig = runtimeConfig,
            privacyManager = privacyManager,
            remoteData = remoteData
        )
        localComponents.add(remoteConfigManager)

        val cache = AirshipCache(application, runtimeConfig)
        val audienceEvaluator = AudienceEvaluator(cache)

        // Experiments
        this.experimentManager = ExperimentManager(
            context = application,
            dataStore = preferenceDataStore,
            remoteData = remoteData,
            audienceEvaluator = audienceEvaluator
        )
        localComponents.add(experimentManager)

        // Debug
        val debugModule = Modules.debug(
            context = application,
            dataStore = preferenceDataStore,
            remoteData = remoteData,
            pushManager = pushManager,
            analytics = analytics
        )
        processModule(debugModule)

        // Message Center
        val messageCenterModule = Modules.messageCenter(
            context = application,
            preferenceDataStore = preferenceDataStore,
            config = runtimeConfig,
            privacyManager = privacyManager,
            channel = channel,
            pushManager = pushManager
        )
        processModule(messageCenterModule)

        // Automation
        val automationModule = Modules.automation(
            context = application,
            dataStore = preferenceDataStore,
            runtimeConfig = runtimeConfig,
            privacyManager = privacyManager,
            airshipChannel = channel,
            pushManager = pushManager,
            analytics = analytics,
            remoteData = remoteData,
            experimentManager = experimentManager,
            meteredUsage = meteredUsageManager,
            deferredResolver = deferredResolver,
            eventFeed = eventFeed,
            metrics = applicationMetrics,
            cache = cache,
            audienceEvaluator = audienceEvaluator
        )
        processModule(automationModule)

        // Ad Id
        val adIdModule = Modules.adId(
            context = application,
            dataStore = preferenceDataStore,
            runtimeConfig = runtimeConfig,
            privacyManager = privacyManager,
            analytics = analytics
        )
        processModule(adIdModule)

        // Preference Center
        val preferenceCenter = Modules.preferenceCenter(
            context = application,
            dataStore = preferenceDataStore,
            privacyManager = privacyManager,
            remoteData = remoteData,
            validator = inputValidator
        )
        processModule(preferenceCenter)

        // Live Updates
        val liveUpdateManager = Modules.liveUpdateManager(
            context = application,
            dataStore = preferenceDataStore,
            config = runtimeConfig,
            privacyManager = privacyManager,
            airshipChannel = channel,
            pushManager = pushManager
        )
        processModule(liveUpdateManager)

        // Feature flags
        val featureFlags = Modules.featureFlags(
            context = application,
            dataStore = preferenceDataStore,
            remoteData = remoteData,
            analytics = analytics,
            cache = cache,
            resolver = deferredResolver,
            privacyManager = privacyManager
        )

        processModule(featureFlags)

        components.update { it + localComponents }

        components.value.forEach { it.init() }
    }

    private fun processModule(module: Module?) {
        if (module == null) { return }
        val application = application ?: return

        components.update { it + module.components }
        module.registerActions(application, this.actionRegistry)
    }

    /**
     * Tears down the Airship instance.
     */
    private fun tearDown() {
        components.value.forEach { it.tearDown() }

        // Teardown the preference data store last
        preferenceDataStore.tearDown()
    }

    /**
     * Returns the platform type.
     *
     * @return [Platform.AMAZON] for Amazon, [Platform.ANDROID] for Android (FCM/HMS),
     * or [Platform.UNKNOWN] if the platform has not been resolved in the past and all features
     * in the SDK are opted out.
     */
    public val platformType: Platform
        get() = runtimeConfig.platform

    /**
     * Gets an AirshipComponent by class.
     *
     * @param clazz The component class.
     * @return The component, or null if not found.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun <T : AirshipComponent?> getComponent(clazz: Class<T>): T? {
        val found = componentClassMap[clazz] ?: run {
            components.value
                .firstOrNull { it.javaClass == clazz }
                ?.also { componentClassMap[clazz] = it }
        }

        if (found != null) {
            return found as T
        }

        return null
    }

    internal fun getComponentsList(): List<AirshipComponent> = components.value

    /**
     * Gets an AirshipComponent by class or throws an exception if there is no AirshipComponent for the class.
     *
     * @param clazz The component class.
     * @return The component.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Throws(IllegalArgumentException::class)
    public fun <T : AirshipComponent?> requireComponent(clazz: Class<T>): T {
        return getComponent(clazz)
            ?: throw IllegalArgumentException("Unable to find component $clazz")
    }

    /**
     * Deep links. If the deep link is an `uairship://` it will be handled internally by the SDK.
     * All other deep links will be forwarded to the deep link listener.
     *
     * @param deepLink The deep link.
     * @return `true` if the deep link was handled, otherwise `false`.
     */
    @MainThread
    public fun deepLink(deepLink: String): Boolean {
        val uri = Uri.parse(deepLink)
        if (uri.scheme != EXTRA_AIRSHIP_DEEP_LINK_SCHEME) {
            return deepLinkListener?.onDeepLink(deepLink) == true
        }

        if (handleAirshipDeeplink(uri, applicationContext)) {
            return true
        }

        if (components.value.any { it.onAirshipDeepLink(uri) }) {
            return true
        }

        if (deepLinkListener?.onDeepLink(deepLink) == true) {
            return true
        }

        UALog.d("Airship deep link not handled: %s", deepLink)

        return true
    }

    /**
     * Handle the Airship deep links for app_settings and app_store.
     * @param uri The deep link Uri.
     * @param context The application context.
     * @return `true` if the deep link was handled, otherwise `false`.
     */
    private fun handleAirshipDeeplink(uri: Uri, context: Context): Boolean {
        when (uri.encodedAuthority) {
            APP_SETTINGS_DEEP_LINK_HOST -> {
                val appSettingsIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", applicationContext.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(appSettingsIntent)
                return true
            }

            APP_STORE_DEEP_LINK_HOST -> {
                val appStoreIntent = AppStoreUtils.getAppStoreIntent(
                    context, platformType, airshipConfigOptions
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(appStoreIntent)
                return true
            }

            else -> return false
        }
    }

    /**
     * Sets a locale to be stored in Airship.
     *
     * @param locale The new locale to use.
     */
    public fun setLocaleOverride(locale: Locale?) {
        localeManager.setLocaleOverride(locale)
    }

    /**
     * Get the locale stored in Airship.
     */
    public val locale: Locale
        get() = localeManager.locale

    /**
     * Callback interface used to notify app when Airship is ready.
     */
    public fun interface OnReadyCallback {

        /**
         * Called when Airship is ready.
         *
         * @param airship The Airship instance.
         */
        public fun onAirshipReady(airship: Airship)
    }

    public companion object {

        /**
         * Broadcast that is sent when Airship is finished taking off.
         */
        public const val ACTION_AIRSHIP_READY: String = "com.urbanairship.AIRSHIP_READY"

        public const val EXTRA_CHANNEL_ID_KEY: String = "channel_id"

        public const val EXTRA_PAYLOAD_VERSION_KEY: String = "payload_version"

        public const val EXTRA_APP_KEY_KEY: String = "app_key"

        public const val EXTRA_AIRSHIP_DEEP_LINK_SCHEME: String = "uairship"

        private const val APP_SETTINGS_DEEP_LINK_HOST = "app_settings"

        private const val APP_STORE_DEEP_LINK_HOST = "app_store"

        private val airshipLock = Any()

        /**
         * Tests if Airship has been initialized and is ready for use.
         */
        @JvmField
        @Volatile
        public var isFlying: Boolean = false

        /**
         * Tests if Airship is currently taking off.
         */
        @JvmField
        @Volatile
        public var isTakingOff: Boolean = false

        /**
         * Tests if the current process is the main process.
         */
        @Volatile
        public var isMainProcess: Boolean = false

        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public var application: Application? = null

        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public var sharedAirship: Airship? = null

        /**
         * Flag to enable printing take off's stacktrace. Useful when debugging exceptions related
         * to take off not being called first.
         */
        public var LOG_TAKE_OFF_STACKTRACE: Boolean = false

        private val pendingAirshipRequests = mutableListOf<CancelableOperation>()

        private var queuePendingAirshipRequests = true

        /**
         * Returns the shared Airship singleton instance. This method will block
         * until airship is ready.
         *
         * @return The Airship singleton.
         * @throws IllegalStateException if takeoff is not called prior to this method.
         */
        @JvmStatic
        @Throws(IllegalStateException::class)
        public fun shared(): Airship {
            synchronized(airshipLock) {
                check(!(!isTakingOff && !isFlying)) { "Take off must be called before shared()" }
                val result = waitForTakeOff(0) ?: throw IllegalStateException("Airship not ready")
                return result
            }
        }

        /**
         * Requests the airship instance asynchronously.
         *
         *
         * This method calls through to [shared]
         * with a null looper.
         *
         * @param callback An optional callback
         * @return A cancelable object that can be used to cancel the callback.
         */
        @JvmStatic
        public fun shared(callback: OnReadyCallback): Cancelable {
            return shared(null, callback)
        }

        /**
         * Waits for Airship to takeOff and be ready.
         *
         * @param millis Time to wait for Airship to be ready in milliseconds or `0` to wait
         * forever.
         * @return The ready Airship instance, or `null` if Airship
         * is not ready by the specified wait time.
         * @hide
         */
        @JvmStatic
        public fun waitForTakeOff(millis: Long): Airship? {
            synchronized(airshipLock) {
                if (isFlying) {
                    return sharedAirship
                }/*
                 From https://developer.android.com/reference/java/lang/Object.html#wait(long)

                 A thread can also wake up without being notified, interrupted, or timing out, a
                 so-called spurious wakeup. While this will rarely occur in practice, applications must
                 guard against it by testing for the condition that should have caused the thread to be
                 awakened, and continuing to wait if the condition is not satisfied.
             */
                try {
                    if (millis > 0) {
                        var remainingTime = millis
                        val startTime = SystemClock.elapsedRealtime()
                        while (!isFlying && remainingTime > 0) {
                            (airshipLock as Object).wait(remainingTime)
                            val elapsedTime = SystemClock.elapsedRealtime() - startTime
                            remainingTime = millis - elapsedTime
                        }
                    } else {
                        while (!isFlying) {
                            (airshipLock as Object).wait()
                        }
                    }

                    if (isFlying) {
                        return sharedAirship
                    }
                } catch (ignored: InterruptedException) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt()
                }
                return null
            }
        }

        /**
         * Requests the airship instance asynchronously.
         *
         *
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
        public fun shared(looper: Looper?, callback: OnReadyCallback?): Cancelable {
            val cancelableOperation: CancelableOperation = object : CancelableOperation(looper) {
                public override fun onRun() {
                    callback?.onAirshipReady(shared())
                }
            }

            synchronized(pendingAirshipRequests) {
                if (queuePendingAirshipRequests) {
                    pendingAirshipRequests.add(cancelableOperation)
                } else {
                    cancelableOperation.run()
                }
            }

            return cancelableOperation
        }

        /**
         * Take off with config loaded from the `airshipconfig.properties` file in the
         * assets directory. See [com.urbanairship.AirshipConfigOptions.Builder.applyDefaultProperties].
         *
         * @param application The application (required)
         */
        @JvmStatic
        @MainThread
        public fun takeOff(application: Application) {
            takeOff(application, null, null)
        }

        /**
         * Take off with a callback to perform airship configuration after
         * takeoff. The ready callback will be executed before the Airship instance is returned by any
         * of the shared methods. The config will be loaded from `airshipconfig.properties` file in the
         * assets directory. See [com.urbanairship.AirshipConfigOptions.Builder.applyDefaultProperties].
         *
         * @param application The application (required)
         * @param readyCallback Optional ready callback. The callback will be triggered on a background thread
         * that performs `takeOff`. If the callback takes longer than ~5 seconds it could cause ANRs within
         * the application.
         */
        @MainThread
        public fun takeOff(application: Application, readyCallback: OnReadyCallback?) {
            takeOff(application, null, readyCallback)
        }

        /**
         * Take off with defined AirshipConfigOptions.
         *
         * @param application The application (required)
         * @param options The launch options. If not null, the options passed in here
         * will override the options loaded from the `.properties` file. This parameter
         * is useful for specifying options at runtime.
         */
        @JvmStatic
        @MainThread
        public fun takeOff(application: Application, options: AirshipConfigOptions?) {
            takeOff(application, options, null)
        }

        /**
         * Take off with a callback to perform airship configuration after takeoff. The
         * ready callback will be executed before the Airship instance is returned by any of the shared
         * methods.
         *
         * @param application The application (required)
         * @param options The launch options. If not null, the options passed in here
         * will override the options loaded from the `.properties` file. This parameter
         * is useful for specifying options at runtime.
         * @param readyCallback Optional ready callback. The callback will be triggered on a background thread
         * that performs `takeOff`. If the callback takes longer than ~5 seconds it could cause ANRs within
         * the application.
         */
        @JvmStatic
        @MainThread
        public fun takeOff(
            application: Application,
            options: AirshipConfigOptions?,
            readyCallback: OnReadyCallback?
        ) {
            if (Looper.myLooper() == null || Looper.getMainLooper() != Looper.myLooper()) {
                UALog.e("takeOff() must be called on the main thread!")
            }

            isMainProcess = ProcessUtils.isMainProcess(application)
            AirshipAppBootstrap.init(application)

            if (LOG_TAKE_OFF_STACKTRACE) {
                val sb = StringBuilder()
                for (element in Exception().stackTrace) {
                    sb.append("\n\tat ")
                    sb.append(element.toString())
                }

                UALog.d("Takeoff stack trace: %s", sb.toString())
            }

            synchronized(airshipLock) {
                // airships only take off once
                if (isFlying || isTakingOff) {
                    UALog.e("You can only call takeOff() once.")
                    return
                }

                UALog.i("Airship taking off!")

                isTakingOff = true

                Companion.application = application
                AirshipExecutors.threadPoolExecutor().execute {
                    executeTakeOff(
                        application, options, readyCallback
                    )
                }
            }
        }

        /**
         * Actually performs takeOff. This is called from takeOff on a background thread.
         *
         * @param application The application (required)
         * @param options The launch options. If not null, the options passed in here will override the
         * options loaded from the `.properties` file. This parameter is useful for specifying options at runtime.
         * @param readyCallback Optional ready callback.
         */
        private fun executeTakeOff(
            application: Application,
            options: AirshipConfigOptions?,
            readyCallback: OnReadyCallback?
        ) {
            val resolved = options ?: AirshipConfigOptions.Builder()
                .applyDefaultProperties(application.applicationContext).build()

            resolved.validate()

            UALog.logLevel = resolved.logLevel.level
            UALog.logPrivacyLevel = resolved.logPrivacyLevel
            val appName = applicationContext.packageManager.getApplicationLabel(applicationContext.applicationInfo).toString()
            UALog.tag = appName + " - " + UALog.DEFAULT_TAG

            UALog.i("Airship taking off!")
            UALog.i("Airship log level: %s", resolved.logLevel)
            UALog.i(
                "UA Version: %s / App key = %s Production = %s",
                getVersion(),
                resolved.appKey,
                resolved.inProduction
            )
            UALog.v(BuildConfig.SDK_VERSION)

            val airshipApp = Airship(resolved)
            sharedAirship = airshipApp

            synchronized(airshipLock) {
                // IMPORTANT! Make sure we set isFlying before calling the readyCallback callback or
                // initializing any of the modules to prevent shared from deadlocking or adding
                // another pendingAirshipRequests.
                isFlying = true
                isTakingOff = false

                // Initialize the modules
                airshipApp.init()

                UALog.i("Airship ready!")

                // Ready callback for setup
                readyCallback?.onAirshipReady(airshipApp)

                // Notify each component that airship is ready
                airshipApp.components.value.forEach { it.onAirshipReady() }

                // Fire any pendingAirshipRequests
                synchronized(pendingAirshipRequests) {
                    queuePendingAirshipRequests = false
                    for (pendingRequest in pendingAirshipRequests) {
                        pendingRequest.run()
                    }
                    pendingAirshipRequests.clear()
                }

                // Send AirshipReady intent for other plugins that depend on Airship
                val readyIntent = Intent(ACTION_AIRSHIP_READY)
                    .setPackage(application.packageName)
                    .addCategory(application.packageName)

                if (airshipApp.runtimeConfig.configOptions.extendedBroadcastsEnabled) {
                    readyIntent.putExtra(EXTRA_CHANNEL_ID_KEY, airshipApp.channel.id)
                    readyIntent.putExtra(
                        EXTRA_APP_KEY_KEY, airshipApp.runtimeConfig.configOptions.appKey
                    )
                    readyIntent.putExtra(EXTRA_PAYLOAD_VERSION_KEY, 1)
                }

                application.sendBroadcast(readyIntent)

                // Notify any blocking shared
                (airshipLock as Object).notifyAll()
            }
        }

        /**
         * Cleans up and closes any connections or other resources.
         *
         * @hide
         */
        @JvmStatic
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        internal fun land() {
            synchronized(airshipLock) {
                if (!isTakingOff && !isFlying) {
                    return
                }
                // Block until takeoff is finished
                val airship = shared()

                airship.tearDown()

                isFlying = false
                isTakingOff = false
                sharedAirship = null
                application = null
                queuePendingAirshipRequests = true
            }
        }

        @JvmStatic
        public val applicationContext: Context
            /**
             * Returns the current Application's context.
             *
             * @return The current application Context.
             * @throws java.lang.IllegalStateException if takeOff has not been called.
             */
            get() {
                val application =
                    application ?: throw IllegalStateException("TakeOff must be called first.")
                return application.applicationContext
            }

        /**
         * Returns the current Airship version.
         *
         * @return The Airship version number.
         */
        @JvmStatic
        public fun getVersion(): String = BuildConfig.AIRSHIP_VERSION
    }
}
