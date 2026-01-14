/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.actions.DeepLinkListener
import com.urbanairship.actions.DefaultActionsManifest
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class AirshipInstance(
    val application: Application
) {
    var deepLinkListener: DeepLinkListener? = null

    val imageLoader: ImageLoader = AirshipGlideImageLoader

    lateinit var actionRegistry: ActionRegistry
    lateinit var preferenceDataStore: PreferenceDataStore
    lateinit var urlAllowList: UrlAllowList
    lateinit var remoteData: RemoteData
    lateinit var inputValidator: AirshipInputValidation.Validator
    lateinit var runtimeConfig: AirshipRuntimeConfig
    lateinit var localeManager: LocaleManager
    lateinit var privacyManager: PrivacyManager
    lateinit var permissionsManager: PermissionsManager
    lateinit var airshipConfigOptions: AirshipConfigOptions

    private val componentClassMap = mutableMapOf<Class<*>, AirshipComponent>()
    val components = MutableStateFlow<List<AirshipComponent>>(emptyList())

    internal fun takeOff(
        configOptions: AirshipConfigOptions?,
        logTakeOffStackTrace: Boolean,
        onReady: ((AirshipConfigOptions) -> Unit)
    ) {
        if (Looper.myLooper() == null || Looper.getMainLooper() != Looper.myLooper()) {
            UALog.e("takeOff() must be called on the main thread!")
        }

        AirshipAppBootstrap.init(application)

        if (logTakeOffStackTrace) {
            val sb = StringBuilder()
            for (element in Exception().stackTrace) {
                sb.append("\n\tat ")
                sb.append(element.toString())
            }

            UALog.d("Takeoff stack trace: %s", sb.toString())
        }

        UALog.i("Airship taking off!")

        AirshipExecutors.threadPoolExecutor().execute {
            executeTakeOff(configOptions, onReady)
        }
    }

    private fun executeTakeOff(
        options: AirshipConfigOptions?,
        onReady: ((AirshipConfigOptions) -> Unit)
    ) {
        val resolved = options ?: AirshipConfigOptions.Builder()
            .applyDefaultProperties(application.applicationContext).build()

        resolved.validate()

        UALog.logLevel = resolved.logLevel.level
        UALog.logPrivacyLevel = resolved.logPrivacyLevel
        val appName = application.packageManager.getApplicationLabel(application.applicationInfo).toString()
        UALog.tag = appName + " - " + UALog.DEFAULT_TAG

        UALog.i("Airship taking off!")
        UALog.i("Airship log level: %s", resolved.logLevel)
        UALog.i(
            "UA Version: %s / App key = %s Production = %s", BuildConfig.AIRSHIP_VERSION,
            resolved.appKey,
            resolved.inProduction
        )
        UALog.v(BuildConfig.SDK_VERSION)

        this.airshipConfigOptions = resolved
        this.preferenceDataStore = PreferenceDataStore.loadDataStore(
            context = application,
            configOptions = airshipConfigOptions
        )

        initModules()
        onReady(resolved)

        // Notify each component that airship is ready
        components.value.forEach { it.onAirshipReady() }
    }

    internal fun tearDown() {
        components.value.forEach { it.tearDown() }
        preferenceDataStore.tearDown()
    }

    private fun initModules() {

        val remoteConfigObserver = RemoteConfigObserver(preferenceDataStore)

        this.privacyManager = PrivacyManager(
            dataStore = preferenceDataStore,
            defaultEnabledFeatures = airshipConfigOptions.enabledFeatures,
            configObserver = remoteConfigObserver,
            resetEnabledFeatures = airshipConfigOptions.resetEnabledFeatures
        )

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
            context = application,
            dataStore = preferenceDataStore,
            runtimeConfig = runtimeConfig,
            privacyManager = privacyManager
        )

        val channel = AirshipChannel(
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
        this.actionRegistry = ActionRegistry().also { it.registerActions(DefaultActionsManifest()) }

        val eventFeed = AirshipEventFeed(
            privacyManager = privacyManager,
            isAnalyticsEnabled = airshipConfigOptions.analyticsEnabled
        )

        // Airship components
        val analytics = Analytics(
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

        val pushManager = PushManager(
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

        val channelCapture = ChannelCapture(
            context = application,
            configOptions = airshipConfigOptions,
            airshipChannel = channel,
            preferenceDataStore = preferenceDataStore,
            activityMonitor = GlobalActivityMonitor.shared(application)
        )
        localComponents.add(channelCapture)

        val contact = Contact(
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

        val meteredUsageManager = AirshipMeteredUsage(
            context = application,
            dataStore = preferenceDataStore,
            config = runtimeConfig,
            privacyManager = privacyManager,
            contact = contact,
            channel = channel
        )
        localComponents.add(meteredUsageManager)

        val remoteConfigManager = RemoteConfigManager(
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
        val experimentManager = ExperimentManager(
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
            pushManager = pushManager,
            analytics = analytics,
            meteredUsage = meteredUsageManager
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
            metrics = ApplicationMetrics(
                context = application,
                dataStore = preferenceDataStore,
                privacyManager = privacyManager
            ),
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
        components.update { it + module.components }
        module.actionsManifest?.let {
            actionRegistry.registerActions(it)
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    inline fun <reified T: AirshipComponent> getComponent(): T? =  getComponent(T::class.java)

    @Throws(IllegalArgumentException::class)
    inline fun <reified T: AirshipComponent> requireComponent(): T =  requireComponent(T::class.java)

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun <T : AirshipComponent?> getComponent(clazz: Class<T>): T? {
        val found = componentClassMap[clazz] ?:
        components.value.firstOrNull { it.javaClass == clazz }
            ?.also { componentClassMap[clazz] = it }

        @Suppress("UNCHECKED_CAST")
        return found as? T
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Throws(IllegalArgumentException::class)
    fun <T : AirshipComponent?> requireComponent(clazz: Class<T>): T {
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
    fun deepLink(deepLink: String): Boolean {
        val uri = Uri.parse(deepLink)
        if (uri.scheme != Airship.EXTRA_AIRSHIP_DEEP_LINK_SCHEME) {
            return deepLinkListener?.onDeepLink(deepLink) == true
        }

        if (handleAirshipDeeplink(uri, application)) {
            return true
        }

        if (components.value.any { it.onAirshipDeepLink(uri) }) {
            return true
        }

        if (deepLinkListener?.onDeepLink(deepLink) == true) {
            return true
        }

        UALog.w("Airship deep link not handled: %s", deepLink)

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
                    Uri.fromParts("package", application.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(appSettingsIntent)
                return true
            }

            APP_STORE_DEEP_LINK_HOST -> {
                val appStoreIntent = AppStoreUtils.getAppStoreIntent(
                    context, runtimeConfig.platform, airshipConfigOptions
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(appStoreIntent)
                return true
            }

            else -> return false
        }
    }

    companion object {
        private const val APP_SETTINGS_DEEP_LINK_HOST = "app_settings"

        private const val APP_STORE_DEEP_LINK_HOST = "app_store"
    }
}
