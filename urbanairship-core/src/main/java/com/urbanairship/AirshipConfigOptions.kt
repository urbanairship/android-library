/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.XmlRes
import androidx.core.app.NotificationCompat
import com.urbanairship.AirshipConfigOptions.PrivacyLevel.PUBLIC
import com.urbanairship.PrivacyManager.Feature
import com.urbanairship.channel.AirshipChannelCreateOption
import com.urbanairship.inputvalidation.AirshipValidationOverride
import com.urbanairship.json.JsonValue
import com.urbanairship.push.PushProvider
import com.urbanairship.util.ConfigParser
import com.urbanairship.util.PropertiesConfigParser
import com.urbanairship.util.XmlConfigParser
import java.util.Properties
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * This class holds the set of options necessary to properly initialize
 * [com.urbanairship.Airship].
 */
public class AirshipConfigOptions private constructor(builder: Builder) {

    /**
     * Config exceptions when trying to load properties from a resource.
     */
    public class ConfigException public constructor(
        message: String,
        throwable: Throwable?
    ) : Exception(message, throwable)

    /**
     * Log privacy levels.
     *
     * When the privacy level is set to [PUBLIC],
     * logs with level [Log.VERBOSE] and [Log.DEBUG] will be logged at
     * [Log.INFO]. This affords more visible logging for debugging purposes.
     */
    public enum class PrivacyLevel {
        PRIVATE, PUBLIC
    }

    public enum class Site(internal val site: String) {
        /**
         * US data site. In order to use this site, your project must be created at go.airship.com
         */
        SITE_US("US"),

        /**
         * EU data site. In order to use this site, your project must be created at go.airship.eu
         */
        SITE_EU("EU")
    }

    public enum class LogLevel(public val level: Int) {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR),
        ASSERT(Log.ASSERT)
    }

    /**
     * Airship app key.
     *
     *
     * This string is generated automatically when you create an app in the Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    @JvmField
    public val appKey: String

    /**
     * Airship app secret.
     *
     *
     * This string is generated automatically when you create an app in the Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    @JvmField
    public val appSecret: String

    /**
     * The device URL.
     *
     * @hide
     */
    internal val deviceUrl: String

    /**
     * The analytics Url.
     *
     * @hide
     */
    internal val analyticsUrl: String

    /**
     * The remote data server URL.
     *
     * @hide
     */
    internal val remoteDataUrl: String

    /**
     * The wallet URL.
     *
     * @hide
     */
    internal val walletUrl: String

    /**
     * Optional app store link when using the rate app action. If not set,
     * the action will generate it using hte app's current package name.
     *
     *
     * Example: "market://details?id=com.example.android"
     */
    @JvmField
    public val appStoreUri: Uri?

    /**
     * The transport types allowed for Push.
     *
     *
     * Defaults to ADM, FCM.
     */
    @JvmField
    public val allowedTransports: List<String>

    /**
     * Custom push provider.
     *
     * @hide
     */
    @JvmField
    public val customPushProvider: PushProvider?

    /**
     * Additional URLs that will be added to the allow list for both [UrlAllowList.Scope.OPEN_URL] and
     * [UrlAllowList.Scope.JAVASCRIPT_INTERFACE] scopes.
     *
     * See [UrlAllowList.addEntry] for valid URL patterns.
     */
    @JvmField
    public val urlAllowList: List<String>

    /**
     * Additional URLs that will be added to the allow list for scope [UrlAllowList.Scope.JAVASCRIPT_INTERFACE].
     *
     * See [UrlAllowList.addEntry] for valid URL patterns.
     */
    @JvmField
    public val urlAllowListScopeJavaScriptInterface: List<String>

    /**
     * Additional URLs that will be added to the allow list for scope [UrlAllowList.Scope.OPEN_URL].
     * See [UrlAllowList.addEntry] for valid URL patterns.
     */
    @JvmField
    public val urlAllowListScopeOpenUrl: List<String>

    @JvmField
    public val isAllowListScopeOpenSet: Boolean
    @JvmField
    public val isAllowListSet: Boolean

    /**
     * Flag indicating whether the application will use analytics.
     *
     *
     * The flag defaults to true.
     */
    @JvmField
    public val analyticsEnabled: Boolean

    /**
     * Minimum delta in milliseconds between analytics uploads when
     * adding location events while in the background.
     *
     *
     * Defaults to 15 minutes.
     */
    @JvmField
    public val backgroundReportingIntervalMS: Long

    /**
     * Logger level when the application is in debug mode.
     * Defaults to `DEBUG`
     */
    @JvmField
    public val logLevel: LogLevel

    @JvmField
    public val logPrivacyLevel: PrivacyLevel

    /**
     * Flag indicating whether or not to launch the launcher activity when a push notification or push
     * notification button is opened and the application intent receiver did not launch an activity.
     *
     *
     * Defaults to true.
     */
    @JvmField
    public val autoLaunchApplication: Boolean

    /**
     * Flag indicating whether channel creation delay is enabled or not.
     *
     *
     * The flag defaults to false.
     */
    @JvmField
    public val channelCreationDelayEnabled: Boolean

    /**
     * Flag indicating whether channel capture feature is enabled or not.
     *
     *
     * The flag defaults to true.
     */
    @JvmField
    public val channelCaptureEnabled: Boolean

    /**
     * Defines the procedure for creating a new channel.
     * Implement the interface carefully to avoid potential issues such as having multiple devices
     * with the same channel id or unpredicted Airship setup time..
     * When the method is set to [com.urbanairship.channel.ChannelGenerationMethod.Restore],
     * the user must provide a unique channel ID.
     */
    public val channelCreateOption: AirshipChannelCreateOption?

    /** Overrides the input validation used by Preference Center and Scenes.  */
    public val validationOverride: AirshipValidationOverride?

    /**
     * Flag indicating if the data collection opt-in is enabled.
     *
     *
     * The flag defaults to false
     *
     */
    @JvmField
    @Deprecated("Use {@link #enabledFeatures} instead.")
    public val dataCollectionOptInEnabled: Boolean

    /**
     * Default enabled Airship features for the app. For more details, see [PrivacyManager].
     * Defaults to [PrivacyManager.Feature.ALL].
     *
     * When specifying the features in either xml or a properties file, use one of the
     * names for convenience:
     * - [FEATURE_ALL]
     * - [FEATURE_NONE]
     * - [FEATURE_MESSAGE_CENTER]
     * - [FEATURE_TAGS_AND_ATTRIBUTES]
     * - [FEATURE_IN_APP_AUTOMATION]
     * - [FEATURE_CONTACTS]
     * - [FEATURE_FEATURE_FLAGS]
     * - [FEATURE_ANALYTICS]
     * - [FEATURE_PUSH]
     */
    @JvmField
    public val enabledFeatures: Feature

    /**
     * Allows resetting enabled features to match the runtime config defaults on each takeOff
     * Defaults to `false`.
     */
    @JvmField
    public val resetEnabledFeatures: Boolean

    /**
     * Flag indicating whether or not to perform extended broadcasts.
     *
     * When extended broadcasts are enabled, the channel identifier and app key are
     * added to the AIRSHIP_READY broadcast and the channel identifier is included in
     * a new CHANNEL_CREATED broadcast.
     *
     *
     * Defaults to `false`.
     */
    @JvmField
    public val extendedBroadcastsEnabled: Boolean

    /**
     * Notification icon.
     */
    @JvmField
    @DrawableRes
    public val notificationIcon: Int

    /**
     * Large notification icon.
     */
    @JvmField
    @DrawableRes
    public val notificationLargeIcon: Int

    /**
     * Notification accent color.
     */
    @JvmField
    @ColorInt
    public val notificationAccentColor: Int

    /**
     * The default notification channel.
     */
    @JvmField
    public val notificationChannel: String?

    /**
     * Flag indicating whether the application is in production.
     *
     *
     * Defaults to `false`.
     */
    @JvmField
    public val inProduction: Boolean

    /**
     * Flag indicating whether the SDK will wait for an initial remote config instead of falling back on default API URLs.
     *
     *
     * Defaults to `true`.
     */
    @JvmField
    public val requireInitialRemoteConfigEnabled: Boolean

    /**
     * The Firebase app name to use for FCM instead of the default app.
     */
    @JvmField
    public val fcmFirebaseAppName: String?

    /**
     * The initial config URL.
     */
    @JvmField
    public val initialConfigUrl: String?

    /**
     * Flag indicating whether or not the SDK will automatically prompt for notification permission
     * when enabling notifications with [com.urbanairship.push.PushManager.userNotificationsEnabled].
     */
    public val isPromptForPermissionOnUserNotificationsEnabled: Boolean

    /**
     * Flag indicating whether or not the SDK will automatically pause In-App Automation during app launch.
     * Defaults to false.
     */
    public val autoPauseInAppAutomationOnLaunch: Boolean

    init {
        val inProduction = builder.inProduction
        requireNotNull(inProduction)
        if (inProduction) {
            this.appKey = builder.productionAppKey ?: builder.appKey ?: ""
            this.appSecret = builder.productionAppSecret ?: builder.appSecret ?: ""
            this.logLevel = builder.productionLogLevel
                ?: builder.logLevel
                ?: DEFAULT_PRODUCTION_LOG_LEVEL
            this.logPrivacyLevel = builder.productionLogPrivacyLevel
        } else {
            this.appKey = builder.developmentAppKey ?: builder.appKey ?: ""
            this.appSecret = builder.developmentAppSecret ?: builder.appSecret ?: ""
            this.logLevel = builder.developmentLogLevel ?: builder.logLevel ?: DEFAULT_DEVELOPMENT_LOG_LEVEL
            this.logPrivacyLevel = builder.developmentLogPrivacyLevel
        }

        when (builder.site) {
            Site.SITE_EU -> {
                this.deviceUrl = builder.deviceUrl ?: EU_DEVICE_URL
                this.analyticsUrl = builder.analyticsUrl ?: EU_ANALYTICS_URL
                this.remoteDataUrl = builder.remoteDataUrl ?: EU_REMOTE_DATA_URL
                this.walletUrl = builder.walletUrl ?: EU_WALLET_URL
            }

            Site.SITE_US -> {
                this.deviceUrl = builder.deviceUrl ?: US_DEVICE_URL
                this.analyticsUrl = builder.analyticsUrl ?: US_ANALYTICS_URL
                this.remoteDataUrl = builder.remoteDataUrl ?: US_REMOTE_DATA_URL
                this.walletUrl = builder.walletUrl ?: US_WALLET_URL
            }
        }

        this.allowedTransports = builder.allowedTransports.toList()
        this.urlAllowList = builder.urlAllowList?.toList() ?: emptyList()
        this.urlAllowListScopeJavaScriptInterface = builder.urlAllowListScopeJavaScriptInterface?.toList() ?: emptyList()
        this.urlAllowListScopeOpenUrl = builder.urlAllowListScopeOpenUrl?.toList() ?: emptyList()
        this.isAllowListScopeOpenSet = builder.isAllowListScopeOpenSet
        this.isAllowListSet = builder.isAllowListSet
        this.inProduction = inProduction
        this.analyticsEnabled = builder.analyticsEnabled
        this.backgroundReportingIntervalMS = builder.backgroundReportingIntervalMS
        this.autoLaunchApplication = builder.autoLaunchApplication
        this.channelCreationDelayEnabled = builder.channelCreationDelayEnabled
        this.channelCaptureEnabled = builder.channelCaptureEnabled
        this.notificationIcon = builder.notificationIcon
        this.notificationLargeIcon = builder.notificationLargeIcon
        this.notificationAccentColor = builder.notificationAccentColor
        this.notificationChannel = builder.notificationChannel
        this.customPushProvider = builder.customPushProvider
        this.appStoreUri = builder.appStoreUri
        @Suppress("DEPRECATION")
        this.dataCollectionOptInEnabled = builder.dataCollectionOptInEnabled
        this.enabledFeatures = builder.enabledFeatures
        this.resetEnabledFeatures = builder.resetEnabledFeatures
        this.extendedBroadcastsEnabled = builder.extendedBroadcastsEnabled
        this.requireInitialRemoteConfigEnabled = builder.requireInitialRemoteConfigEnabled
        this.fcmFirebaseAppName = builder.fcmFirebaseAppName
        this.initialConfigUrl = builder.initialConfigUrl
        this.isPromptForPermissionOnUserNotificationsEnabled = builder.isPromptForPermissionOnUserNotificationsEnabled
        this.autoPauseInAppAutomationOnLaunch = builder.autoPauseInAppAutomationOnLaunch
        this.channelCreateOption = builder.channelCreateOption
        this.validationOverride = builder.validationOverride
    }

    /**
     * Validates the config.
     *
     * @throws IllegalArgumentException if the app key or secret are invalid.
     */
    @kotlin.jvm.Throws(IllegalArgumentException::class)
    public fun validate() {
        val modeString = if (inProduction) "production" else "development"

        require(APP_CREDENTIAL_PATTERN.matcher(appKey).matches()) {
            "AirshipConfigOptions: $appKey is not a valid $modeString app key"
        }

        require(APP_CREDENTIAL_PATTERN.matcher(appSecret).matches()) {
            "AirshipConfigOptions: $appSecret is not a valid $modeString app secret"
        }

        if (backgroundReportingIntervalMS < MIN_BG_REPORTING_INTERVAL) {
            UALog.w(
                "AirshipConfigOptions - The backgroundReportingIntervalMS %s may decrease battery life.",
                backgroundReportingIntervalMS
            )
        } else if (backgroundReportingIntervalMS > MAX_BG_REPORTING_INTERVAL) {
            UALog.w(
                "AirshipConfigOptions - The backgroundReportingIntervalMS %s may provide less detailed analytic reports.",
                backgroundReportingIntervalMS
            )
        }
    }

    /**
     * Airship config builder.
     */
    public class Builder public constructor() {

        /**
         * The default app key.
         *
         * The development and production app keys will take precedence if defined depending
         * on how the inProduction flag is set.
         */
        public var appKey: String? = null
            private set

        /**
         * The default app secret.
         *
         * The development and production app secret will take precedence if defined depending
         * on how the inProduction flag is set.
         */
        public var appSecret: String? = null
            private set

        /** The application's production app key. */
        public var productionAppKey: String? = null
            private set

        /** The application's production app secret. */
        public var productionAppSecret: String? = null
            private set

        /** The application's development app key. */
        public var developmentAppKey: String? = null
            private set

        /** The application's development app secret. */
        public var developmentAppSecret: String? = null
            private set

        /** The device URL. */
        internal var deviceUrl: String? = null
            private set

        /** The analytics server URL. */
        internal var analyticsUrl: String? = null
            private set

        /** The remote data URL. */
        internal var remoteDataUrl: String? = null
            private set

        /** The transport types allowed for Push. */
        public var allowedTransports: List<String> = listOf(ADM_TRANSPORT, FCM_TRANSPORT, HMS_TRANSPORT)
            private set

        /**
         * The additional URLs that will be added to the allow list for both [UrlAllowList.Scope.OPEN_URL] and
         * [UrlAllowList.Scope.JAVASCRIPT_INTERFACE] scopes.
         * See [UrlAllowList.addEntry] for valid URL patterns.
         */
        public var urlAllowList: List<String>? = null
            private set

        /**
         * The additional URLs that will be added to the allow list for scope [UrlAllowList.Scope.JAVASCRIPT_INTERFACE].
         * See [UrlAllowList.addEntry] for valid URL patterns.
         */
        public var urlAllowListScopeJavaScriptInterface: List<String>? = null
            private set

        /**
         * The additional URLs that will be added to the allow list for scope [UrlAllowList.Scope.OPEN_URL].
         * See [UrlAllowList.addEntry] for valid URL patterns.
         */
        public var urlAllowListScopeOpenUrl: List<String>? = null
            private set

        internal var isAllowListScopeOpenSet: Boolean = false
            private set
        internal var isAllowListSet: Boolean = false
            private set

        /** The flag indicating whether the application is in production or development. */
        public var inProduction: Boolean? = null
            private set

        /** The flag indicating whether the application will use analytics. */
        public var analyticsEnabled: Boolean = true
            private set

        /** The background reporting interval in milliseconds. */
        public var backgroundReportingIntervalMS: Long = DEFAULT_BG_REPORTING_INTERVAL
            private set

        /** The logger level when the application is in debug mode. */
        public var developmentLogLevel: LogLevel? = null
            private set

        /** The logger level when the application is in production mode. */
        public var productionLogLevel: LogLevel? = null
            private set

        /**
         * The default logger level.
         *
         * The development and production log level will take precedence if defined depending
         * on how the inProduction flag is set.
         */
        public var logLevel: LogLevel? = null
            private set

        /** The development log privacy level. */
        public var developmentLogPrivacyLevel: PrivacyLevel = PrivacyLevel.PRIVATE
            private set

        /** The production log privacy level. */
        public var productionLogPrivacyLevel: PrivacyLevel = PrivacyLevel.PRIVATE
            private set

        /**
         * The flag indicating whether or not to launch the launcher activity when a push notification or push
         * notification button is opened and the application intent receiver did not launch an activity.
         */
        public var autoLaunchApplication: Boolean = true
            private set

        /** The flag indicating whether channel creation delay is enabled or not. */
        public var channelCreationDelayEnabled: Boolean = false
            private set

        /** The flag indicating whether channel capture feature is enabled or not. */
        public var channelCaptureEnabled: Boolean = true
            private set

        /**
         * The default notification Icon.
         * See [com.urbanairship.push.notifications.AirshipNotificationProvider.smallIcon].
         */
        public var notificationIcon: Int = 0
            private set

        /**
         * The large notification Icon.
         * See [com.urbanairship.push.notifications.AirshipNotificationProvider.largeIcon].
         */
        public var notificationLargeIcon: Int = 0
            private set

        /**
         * The default notification accent color.
         * See [com.urbanairship.push.notifications.AirshipNotificationProvider.defaultAccentColor].
         */
        public var notificationAccentColor: Int = NotificationCompat.COLOR_DEFAULT
            private set

        /** The Wallet URL. */
        internal var walletUrl: String? = null
            private set

        /**
         * The default notification channel.
         * See [com.urbanairship.push.notifications.NotificationProvider.onCreateNotificationArguments]
         */
        public var notificationChannel: String? = null
            private set

        /** Used to set a custom push provider for push registration. */
        public var customPushProvider: PushProvider? = null
            private set

        /**
         * The app store URI for the rate-app action. If not set,
         * the action will generate it using the app's current package name.
         *
         * Example: "market://details?id=com.example.android"
         */
        public var appStoreUri: Uri? = null
            private set

        /**
         * The flag indicating whether data collection needs to be opted in.
         *
         * This method is deprecated. Use [PrivacyManager] to manage privacy features.
         */
        @Deprecated(message = "Use {@link #enabledFeatures} instead.")
        public var dataCollectionOptInEnabled: Boolean = false
            private set

        /**
         * The flag indicating whether extended broadcasts are enabled or disabled.
         *
         * When extended broadcasts are enabled, the channel identifier and app key are
         * added to the AIRSHIP_READY broadcast and the channel identifier is included in
         * a new CHANNEL_CREATED broadcast.
         */
        public var extendedBroadcastsEnabled: Boolean = false
            private set

        /** The Airship cloud site for data locality. */
        public var site: Site = Site.SITE_US

        /** Overrides the input validation used by Preference Center and Scenes. */
        public var validationOverride: AirshipValidationOverride? = null

        /** The default enabled SDK features. See [PrivacyManager] for more info. */
        public var enabledFeatures: Feature = Feature.ALL

        /** The reset enabled features option. */
        public var resetEnabledFeatures: Boolean = false

        /** The flag to require initial remote-config for device URLs. */
        public var requireInitialRemoteConfigEnabled: Boolean = true

        /**
         * The Firebase app name that is used for FCM. If set, the app name must exist in order
         * for Airship to get registration token. The app should be initialized with Firebase before takeOff, or during
         * onAirshipReady callback.
         */
        public var fcmFirebaseAppName: String? = null

        /**
         * The Airship URL used to pull the initial config. This should only be set
         * if you are using custom domains that forward to Airship.
         */
        public var initialConfigUrl: String? = null

        /**
         * When enabled sets [com.urbanairship.push.PushManager.userNotificationsEnabled]
         * if the SDK should prompt for permission on Android 13+ devices. Enabled by default.
         */
        public var isPromptForPermissionOnUserNotificationsEnabled: Boolean = true

        /** The auto pause In-App Automation on launch. */
        public var autoPauseInAppAutomationOnLaunch: Boolean = false

        /** The Airship channel create option */
        public var channelCreateOption: AirshipChannelCreateOption? = null

        /**
         * Apply the options from the default properties file `airshipconfig.properties`.
         *
         * @see applyProperties
         *
         * @param context The application context
         * @return The config option builder.
         */
        public fun applyDefaultProperties(context: Context): Builder {
            return applyProperties(context, DEFAULT_PROPERTIES_FILENAME)
        }

        /**
         * Same as [applyDefaultProperties], but throws an exception instead of
         * logging an error.
         *
         * @param context The application context
         * @return The config option builder.
         * @throws ConfigException
         */
        @Throws(ConfigException::class)
        public fun tryApplyDefaultProperties(context: Context): Builder {
            return tryApplyProperties(context, DEFAULT_PROPERTIES_FILENAME)
        }

        /**
         * Apply the options from a given properties file. The properties file should
         * be available in the assets directory. The properties file can define any of the
         * public [AirshipConfigOptions] fields. Example:
         * <pre>
         * `# App Credentials
         * developmentAppKey = Your Development App Key
         * developmentAppSecret = Your Development App Secret
         * productionAppKey = Your Production App Key
         * productionAppSecret = Your Production Secret
         *
         * # Flag to indicate what credentials to use
         * inProduction = false
         *
         * # Log levels
         * developmentLogLevel = DEBUG
         * productionLogLevel = ERROR
         *
         * # Notification settings
         * notificationIcon = ic_notification
         * notificationAccentColor = #ff0000
         *
        ` *
        </pre> *
         *
         * @param context The application context.
         * @param propertiesFile The name of the properties file in the assets directory.
         * @return The config option builder.
         */
        public fun applyProperties(context: Context, propertiesFile: String): Builder {
            try {
                tryApplyProperties(context, propertiesFile)
            } catch (e: Exception) {
                UALog.e(e)
            }

            return this
        }

        /**
         * Same as [applyProperties], but throws an exception instead of
         * logging an error.
         *
         * @param context The application context.
         * @param propertiesFile The name of the properties file in the assets directory.
         * @return The config option builder.
         * @throws ConfigException
         */
        @Throws(ConfigException::class)
        public fun tryApplyProperties(context: Context, propertiesFile: String): Builder {
            try {
                val configParser: ConfigParser =
                    PropertiesConfigParser.fromAssets(context, propertiesFile)
                applyConfigParser(context, configParser)
            } catch (e: Exception) {
                throw ConfigException("Unable to apply config from file $propertiesFile", e)
            }

            return this
        }

        /**
         * Applies properties from a given Properties object.
         *
         * @param context The application context.
         * @param properties The properties
         * @return The config option builder.
         */
        public fun applyProperties(context: Context, properties: Properties): Builder {
            try {
                val configParser: ConfigParser =
                    PropertiesConfigParser.fromProperties(context, properties)
                applyConfigParser(context, configParser)
            } catch (e: Exception) {
                UALog.e(e)
            }

            return this
        }

        /**
         * The same as [applyProperties], but throws an exception
         * instead of logging an error.
         *
         * @param context The application context.
         * @param properties The properties
         * @return The config option builder.
         * @throws ConfigException
         */
        @Throws(ConfigException::class)
        public fun tryApplyProperties(context: Context, properties: Properties): Builder {
            try {
                val configParser: ConfigParser =
                    PropertiesConfigParser.fromProperties(context, properties)
                applyConfigParser(context, configParser)
            } catch (e: Exception) {
                throw ConfigException("Unable to apply config.", e)
            }
            return this
        }

        /**
         * Apply options from a xml resource file. The XML file must contain the element `AirshipConfigOptions`
         * and any public [AirshipConfigOptions] fields should be set as attributes on the element.
         * Example:
         * <pre>
         * `<AirshipConfigOptions
         * notificationIcon = "@drawable/ic_notification"
         * notificationAccentColor = "@color/color_accent"
         * inProduction = "false"
         * productionAppKey = "Your Production App Key"
         * productionAppSecret = "Your Production App Secret"
         * productionLogLevel = "NONE"
         * developmentAppKey = "Your Development App Key"
         * developmentAppSecret = "Your Development App Secret"
         * developmentLogLevel = "VERBOSE"
        ` *
        </pre> *
         *
         * @param context The application context.
         * @param xmlResourceId The xml resource ID.
         * @return The config option builder.
         */
        public fun applyConfig(context: Context, @XmlRes xmlResourceId: Int): Builder {
            try {
                tryApplyConfig(context, xmlResourceId)
            } catch (e: Exception) {
                UALog.e(e)
            }

            return this
        }

        /**
         * The same as [.applyConfig], but throws an exception instead of
         * logging an error.
         *
         * @param context The application context.
         * @param xmlResourceId The xml resource ID.
         * @return The config option builder.
         */
        @Throws(Exception::class)
        public fun tryApplyConfig(context: Context, @XmlRes xmlResourceId: Int): Builder {
            var configParser: XmlConfigParser? = null
            try {
                configParser = XmlConfigParser.parseElement(context, xmlResourceId, CONFIG_ELEMENT)
                applyConfigParser(context, configParser)
            } catch (e: Exception) {
                throw ConfigException("Unable to apply config from xml.", e)
            } finally {
                configParser?.close()
            }

            return this
        }

        /**
         * Applies a value to the builder.
         *
         * @param configParser The config parser.
         */
        private fun applyConfigParser(context: Context, configParser: ConfigParser) {
            for (i in 0..<configParser.count) {
                try {
                    val name = configParser.getName(i) ?: continue
                    when (name) {
                        FIELD_APP_KEY -> this.setAppKey(configParser.getString(name))
                        FIELD_APP_SECRET -> this.setAppSecret(configParser.getString(name))
                        FIELD_PRODUCTION_APP_KEY -> this.setProductionAppKey(
                            configParser.getString(name)
                        )
                        FIELD_PRODUCTION_APP_SECRET -> this.setProductionAppSecret(
                            configParser.getString(name)
                        )
                        FIELD_DEVELOPMENT_APP_KEY -> this.setDevelopmentAppKey(
                            configParser.getString(name)
                        )
                        FIELD_DEVELOPMENT_APP_SECRET -> this.setDevelopmentAppSecret(
                            configParser.getString(name)
                        )

                        FIELD_LEGACY_DEVICE_URL, FIELD_DEVICE_URL ->
                            configParser.getString(name)?.let { this.setDeviceUrl(it) }

                        FIELD_LEGACY_ANALYTICS_SERVER, FIELD_ANALYTICS_URL ->
                            configParser.getString(name)?.let { this.setAnalyticsUrl(it) }

                        FIELD_LEGACY_REMOTE_DATA_URL, FIELD_REMOTE_DATA_URL ->
                            configParser.getString(name)?.let { this.setRemoteDataUrl(it) }

                        FIELD_INITIAL_CONFIG_URL ->
                            configParser.getString(name)?.let { this.setInitialConfigUrl(it) }

                        FIELD_GCM_SENDER -> throw IllegalArgumentException(
                            "gcmSender no longer supported. Please use " +
                            "fcmSender or remove it to allow the Airship SDK to pull from the google-services.json."
                        )

                        FIELD_ALLOWED_TRANSPORTS -> this.setAllowedTransports(
                            configParser.getStringArray(name)
                        )

                        "whitelist" -> {
                            UALog.e("Parameter whitelist is deprecated and will be removed in a future version of the SDK. Use urlAllowList instead.")
                            this.setUrlAllowList(configParser.getStringArray(name))
                        }

                        FIELD_URL_ALLOW_LIST -> this.setUrlAllowList(
                            configParser.getStringArray(name)
                        )

                        FIELD_URL_ALLOW_LIST_SCOPE_JAVASCRIPT_INTERFACE -> this.setUrlAllowListScopeJavaScriptInterface(
                            configParser.getStringArray(name)
                        )

                        FIELD_URL_ALLOW_LIST_SCOPE_OPEN_URL -> this.setUrlAllowListScopeOpenUrl(
                            configParser.getStringArray(name)
                        )

                        FIELD_IN_PRODUCTION ->
                            configParser.getString(name)?.let { this.setInProduction(it.toBoolean()) }

                        FIELD_ANALYTICS_ENABLED -> this.setAnalyticsEnabled(
                            configParser.getBoolean(name, analyticsEnabled)
                        )

                        FIELD_BACKGROUND_REPORTING_INTERVAL_MS -> this.setBackgroundReportingIntervalMS(
                            configParser.getLong(name, backgroundReportingIntervalMS)
                        )

                        FIELD_DEVELOPMENT_LOG_LEVEL -> this.setDevelopmentLogLevel(
                            UALog.parseLogLevel(configParser.getString(name), DEFAULT_DEVELOPMENT_LOG_LEVEL)
                        )

                        FIELD_PRODUCTION_LOG_LEVEL -> this.setProductionLogLevel(
                            UALog.parseLogLevel(configParser.getString(name), DEFAULT_PRODUCTION_LOG_LEVEL)
                        )

                        FIELD_LOG_LEVEL -> this.setLogLevel(
                            UALog.parseLogLevel(configParser.getString(name), DEFAULT_PRODUCTION_LOG_LEVEL)
                        )

                        FIELD_DEVELOPMENT_LOG_PRIVACY_LEVEL -> this.setDevelopmentLogPrivacyLevel(
                            parseLogPrivacyLevel(configParser.getString(name))
                        )

                        FIELD_PRODUCTION_LOG_PRIVACY_LEVEL -> this.setProductionLogPrivacyLevel(
                            parseLogPrivacyLevel(configParser.getString(name))
                        )

                        FIELD_AUTO_LAUNCH_APPLICATION -> this.setAutoLaunchApplication(
                            configParser.getBoolean(name, autoLaunchApplication)
                        )

                        FIELD_CHANNEL_CREATION_DELAY_ENABLED -> this.setChannelCreationDelayEnabled(
                            configParser.getBoolean(name, channelCreationDelayEnabled)
                        )

                        FIELD_CHANNEL_CAPTURE_ENABLED -> this.setChannelCaptureEnabled(
                            configParser.getBoolean(name, channelCaptureEnabled)
                        )

                        FIELD_NOTIFICATION_ICON -> this.setNotificationIcon(
                            configParser.getDrawableResourceId(name)
                        )

                        FIELD_NOTIFICATION_LARGE_ICON -> this.setNotificationLargeIcon(
                            configParser.getDrawableResourceId(name)
                        )

                        FIELD_NOTIFICATION_ACCENT_COLOR -> this.setNotificationAccentColor(
                            configParser.getColor(name, notificationAccentColor)
                        )

                        FIELD_WALLET_URL ->
                            configParser.getString(name)?.let { this.setWalletUrl(it) }

                        FIELD_NOTIFICATION_CHANNEL -> this.setNotificationChannel(
                            configParser.getString(name)
                        )

                        FIELD_FCM_SENDER_ID,
                        FIELD_DEVELOPMENT_FCM_SENDER_ID,
                        FIELD_PRODUCTION_FCM_SENDER_ID -> UALog.e(
                            "Support for Sender ID override has been removed. Configure a FirebaseApp and use fcmFirebaseAppName instead."
                        )

                        FIELD_FCM_FIREBASE_APP_NAME -> this.setFcmFirebaseAppName(
                            configParser.getString(name)
                        )

                        "enableUrlWhitelisting" ->
                            UALog.e("Parameter enableUrlWhitelisting has been removed. " +
                                    "See urlAllowListScopeJavaScriptBridge and urlAllowListScopeOpen instead.")

                        FIELD_CUSTOM_PUSH_PROVIDER -> {
                            val className = configParser.getString(name)
                                ?: throw IllegalArgumentException("Missing custom push provider class name")
                            val providerClass = Class.forName(className).asSubclass(PushProvider::class.java)
                            this.setCustomPushProvider(providerClass.getDeclaredConstructor().newInstance())
                        }

                        FIELD_APP_STORE_URI -> this.setAppStoreUri(
                            Uri.parse(configParser.getString(name))
                        )

                        FIELD_SITE -> this.setSite(parseSite(configParser.getString(name)))
                        FIELD_DATA_COLLECTION_OPT_IN_ENABLED -> {
                            @Suppress("DEPRECATION")
                            this.setDataCollectionOptInEnabled(configParser.getBoolean(name, false))
                        }

                        FIELD_EXTENDED_BROADCASTS_ENABLED -> this.setExtendedBroadcastsEnabled(
                            configParser.getBoolean(name, false)
                        )

                        FIELD_REQUIRE_INITIAL_REMOTE_CONFIG_ENABLED -> this.setRequireInitialRemoteConfigEnabled(
                            configParser.getBoolean(name, false)
                        )

                        FIELD_IS_PROMPT_FOR_PERMISSION_ON_USER_NOTIFICATIONS_ENABLED -> this.setIsPromptForPermissionOnUserNotificationsEnabled(
                            configParser.getBoolean(name, true)
                        )

                        FIELD_AUTO_PAUSE_IN_APP_AUTOMATION_ON_LAUNCH -> this.setAutoPauseInAppAutomationOnLaunch(
                            configParser.getBoolean(name, false)
                        )

                        FIELD_ENABLED_FEATURES -> {
                            val value = try {
                                configParser.getInt(name, -1)
                            } catch (e: Exception) {
                                -1
                            }

                            if (value != -1) {
                                this.setEnabledFeatures(Feature(value))
                            } else {
                                val features = configParser.getStringArray(name)
                                    ?: throw IllegalArgumentException("Unable to parse enableFeatures: " + configParser.getString(name))

                                convertFeatureNames(features)?.let { this.setEnabledFeatures(it) }
                            }
                        }

                        FIELD_RESET_ENABLED_FEATURES -> this.setResetEnabledFeatures(
                            configParser.getBoolean(name, false)
                        )
                    }
                } catch (e: Exception) {
                    UALog.e(
                        e,
                        "Unable to set config field '%s' due to invalid configuration value.",
                        configParser.getName(i)
                    )
                }
            }

            // Determine build mode if not specified in config file.
            if (inProduction == null) {
                detectProvisioningMode(context)
            }
        }

        private fun convertFeatureNames(features: Array<String>): Feature? {
            try {
                return Feature.fromJson(JsonValue.wrap(features))
            } catch (ex: Exception) {
                UALog.e(ex, "Failed to parse features array " + java.lang.String.join(",", *features))
                return Feature.NONE
            }
        }

        /**
         * Sets the default notification channel.
         *
         *
         * See [com.urbanairship.push.notifications.NotificationProvider.onCreateNotificationArguments]
         *
         * @param channel The notification channel.
         * @return The config options builder.
         */
        public fun setNotificationChannel(channel: String?): Builder {
            this.notificationChannel = channel
            return this
        }

        /**
         * Sets the default notification Icon.
         *
         *
         * See [com.urbanairship.push.notifications.AirshipNotificationProvider.smallIcon].
         *
         * @param notificationIcon The notification icon.
         * @return The config options builder.
         */
        public fun setNotificationIcon(@DrawableRes notificationIcon: Int): Builder {
            this.notificationIcon = notificationIcon
            return this
        }

        /**
         * Sets the large notification Icon.
         *
         *
         * See [com.urbanairship.push.notifications.AirshipNotificationProvider.largeIcon].
         *
         * @param notificationLargeIcon The large notification icon.
         * @return The config options builder.
         */
        public fun setNotificationLargeIcon(@DrawableRes notificationLargeIcon: Int): Builder {
            this.notificationLargeIcon = notificationLargeIcon
            return this
        }

        /**
         * Sets the default notification accent color.
         *
         *
         * See [com.urbanairship.push.notifications.AirshipNotificationProvider.defaultAccentColor].
         *
         * @param notificationAccentColor The notification accent color.
         * @return The config options builder.
         */
        public fun setNotificationAccentColor(@ColorInt notificationAccentColor: Int): Builder {
            this.notificationAccentColor = notificationAccentColor
            return this
        }

        /**
         * Set the default app key.
         *
         * The development and production app keys will take precedence if defined depending
         * on how the inProduction flag is set.
         *
         * @param appKey The application's app key.
         * @return The config options builder.
         */
        public fun setAppKey(appKey: String?): Builder {
            this.appKey = appKey
            return this
        }

        /**
         * Set the default app secret.
         *
         * The development and production app secret will take precedence if defined depending
         * on how the inProduction flag is set.
         *
         * @param appSecret The application's production app secret.
         * @return The config options builder.
         */
        public fun setAppSecret(appSecret: String?): Builder {
            this.appSecret = appSecret
            return this
        }

        /**
         * Set the application's production app key.
         *
         * @param productionAppKey The application's production app key.
         * @return The config options builder.
         */
        public fun setProductionAppKey(productionAppKey: String?): Builder {
            this.productionAppKey = productionAppKey
            return this
        }

        /**
         * Set the application's production app secret.
         *
         * @param productionAppSecret The application's production app secret.
         * @return The config options builder.
         */
        public fun setProductionAppSecret(productionAppSecret: String?): Builder {
            this.productionAppSecret = productionAppSecret
            return this
        }

        /**
         * Set the application's development app key.
         *
         * @param developmentAppKey The application's development app key.
         * @return The config options builder.
         */
        public fun setDevelopmentAppKey(developmentAppKey: String?): Builder {
            this.developmentAppKey = developmentAppKey
            return this
        }

        /**
         * Set the application's development app secret.
         *
         * @param developmentAppSecret The application's development app secret.
         * @return The config options builder.
         */
        public fun setDevelopmentAppSecret(developmentAppSecret: String?): Builder {
            this.developmentAppSecret = developmentAppSecret
            return this
        }

        /**
         * Set the device URL.
         *
         * @param deviceUrl The device URL.
         * @return The config options builder.
         * @hide
         */
        internal fun setDeviceUrl(deviceUrl: String): Builder {
            this.deviceUrl = deviceUrl
            return this
        }

        /**
         * Set the analytics server URL.
         *
         * @param analyticsUrl The analytics server URL.
         * @return The config options builder.
         * @hide
         */
        internal fun setAnalyticsUrl(analyticsUrl: String): Builder {
            this.analyticsUrl = analyticsUrl
            return this
        }

        /**
         * Set the remote data URL.
         *
         * @param remoteDataUrl The remote data URL.
         * @return The config options builder.
         * @hide
         */
        internal fun setRemoteDataUrl(remoteDataUrl: String?): Builder {
            this.remoteDataUrl = remoteDataUrl
            return this
        }

        /**
         * The Airship URL used to pull the initial config. This should only be set
         * if you are using custom domains that forward to Airship.
         *
         * @param initialConfigUrl The initial config URL.
         * @return The config options builder.
         */
        public fun setInitialConfigUrl(initialConfigUrl: String?): Builder {
            this.initialConfigUrl = initialConfigUrl
            return this
        }

        /**
         * Set the transport types allowed for Push.
         *
         * @param allowedTransports The transport types allowed for Push.
         * @return The config options builder.
         */
        public fun setAllowedTransports(allowedTransports: Array<String>?): Builder {
            this.allowedTransports = allowedTransports?.toList() ?: emptyList()
            return this
        }

        /**
         * Sets the additional URLs that will be added to the allow list for both [UrlAllowList.Scope.OPEN_URL] and
         * [UrlAllowList.Scope.JAVASCRIPT_INTERFACE] scopes.
         *
         * See [UrlAllowList.addEntry] for valid URL patterns.
         *
         * @param urlAllowList An array of URL patterns.
         * @return The config options builder.
         */
        public fun setUrlAllowList(urlAllowList: Array<String>?): Builder {
            this.urlAllowList = urlAllowList?.toList()
            this.isAllowListSet = true
            return this
        }

        /**
         * Sets the additional URLs that will be added to the allow list for scope [UrlAllowList.Scope.JAVASCRIPT_INTERFACE].
         * See [UrlAllowList.addEntry] for valid URL patterns.
         *
         * @param urlAllowListScopeJavaScriptInterface An array of URL patterns.
         * @return The config options builder.
         */
        public fun setUrlAllowListScopeJavaScriptInterface(urlAllowListScopeJavaScriptInterface: Array<String>?): Builder {
            this.urlAllowListScopeJavaScriptInterface = urlAllowListScopeJavaScriptInterface?.toList()
            return this
        }

        /**
         * Sets the additional URLs that will be added to the allow list for scope [UrlAllowList.Scope.OPEN_URL].
         * See [UrlAllowList.addEntry] for valid URL patterns.
         *
         * @param urlAllowListScopeOpenUrl An array of URL patterns.
         * @return The config options builder.
         */
        public fun setUrlAllowListScopeOpenUrl(urlAllowListScopeOpenUrl: Array<String>?): Builder {
            this.urlAllowListScopeOpenUrl = urlAllowListScopeOpenUrl?.toList()
            this.isAllowListScopeOpenSet = true
            return this
        }

        /**
         * Set the flag indicating whether the application is in production or development.
         *
         * @param inProduction The flag indicating whether the application is in production or development.
         * @return The config options builder.
         */
        public fun setInProduction(inProduction: Boolean): Builder {
            this.inProduction = inProduction
            return this
        }

        /**
         * Automatically determine the provisioning mode of the application.
         *
         * @param context The application context.
         * @return The config options builder.
         */
        public fun detectProvisioningMode(context: Context): Builder {
            try {
                val clazz = Class.forName(context.packageName + ".BuildConfig")
                val field = clazz.getField("DEBUG")
                inProduction = field.get(null) == false
            } catch (e: Exception) {
                UALog.w(e, "AirshipConfigOptions - Unable to determine the build mode. Defaulting to debug.")
                inProduction = false
            }
            return this
        }

        /**
         * Set the flag indicating whether the application will use analytics.
         *
         * @param analyticsEnabled The flag indicating whether the application will use analytics.
         * @return The config options builder.
         */
        public fun setAnalyticsEnabled(analyticsEnabled: Boolean): Builder {
            this.analyticsEnabled = analyticsEnabled
            return this
        }

        /**
         * Set the background reporting interval.
         *
         * @param backgroundReportingIntervalMS The background reporting interval.
         * @return The config options builder.
         */
        public fun setBackgroundReportingIntervalMS(backgroundReportingIntervalMS: Long): Builder {
            this.backgroundReportingIntervalMS = backgroundReportingIntervalMS
            return this
        }

        /**
         * Set the logger level when the application is in debug mode.
         *
         * @param developmentLogLevel The logger level.
         * @return The config options builder.
         */
        public fun setDevelopmentLogLevel(developmentLogLevel: LogLevel): Builder {
            this.developmentLogLevel = developmentLogLevel
            return this
        }

        /**
         * Set the logger level when the application is in production mode.
         *
         * @param productionLogLevel The logger level.
         * @return The config options builder.
         */
        public fun setProductionLogLevel(productionLogLevel: LogLevel): Builder {
            this.productionLogLevel = productionLogLevel
            return this
        }

        /**
         * Set the default logger level.
         *
         * The development and production log level will take precedence if defined depending
         * on how the inProduction flag is set.
         *
         * @param logLevel The logger level.
         * @return The config options builder.
         */
        public fun setLogLevel(logLevel: LogLevel): Builder {
            this.logLevel = logLevel
            return this
        }

        public fun setDevelopmentLogPrivacyLevel(privacyLevel: PrivacyLevel): Builder {
            this.developmentLogPrivacyLevel = privacyLevel
            return this
        }

        public fun setProductionLogPrivacyLevel(privacyLevel: PrivacyLevel): Builder {
            this.productionLogPrivacyLevel = privacyLevel
            return this
        }

        /**
         * Set the flag indicating whether or not to launch the launcher activity when a push notification or push
         * notification button is opened and the application intent receiver did not launch an activity.
         *
         * @param autoLaunchApplication The auto launch flag.
         * @return The config options builder.
         */
        public fun setAutoLaunchApplication(autoLaunchApplication: Boolean): Builder {
            this.autoLaunchApplication = autoLaunchApplication
            return this
        }

        /**
         * Set the flag indicating whether channel creation delay is enabled or not.
         *
         * @param channelCreationDelayEnabled The flag indicating whether channel creation delay is enabled or not.
         * @return The config option builder.
         */
        public fun setChannelCreationDelayEnabled(channelCreationDelayEnabled: Boolean): Builder {
            this.channelCreationDelayEnabled = channelCreationDelayEnabled
            return this
        }

        /**
         * Set the flag indicating whether channel capture feature is enabled or not.
         *
         * @param channelCaptureEnabled The flag indicating whether channel capture feature is enabled or not.
         * @return The config option builder.
         */
        public fun setChannelCaptureEnabled(channelCaptureEnabled: Boolean): Builder {
            this.channelCaptureEnabled = channelCaptureEnabled
            return this
        }

        /**
         * Set the Wallet URL.
         *
         * @param walletUrl The Wallet URL.
         * @return The config options builder.
         * @hide
         */
        internal fun setWalletUrl(walletUrl: String): Builder {
            this.walletUrl = walletUrl
            return this
        }

        /**
         * Used to set a custom push provider for push registration.
         *
         * @param customPushProvider Push provider.
         * @return The config options builder.
         * @hide
         */
        public fun setCustomPushProvider(customPushProvider: PushProvider?): Builder {
            this.customPushProvider = customPushProvider
            return this
        }

        /**
         * Sets the app store URI for the rate-app action. If not set,
         * the action will generate it using the app's current package name.
         *
         *
         *
         * Example: "market://details?id=com.example.android"
         *
         * @param appStoreUri The app store URI.
         * @return The config options builder.
         */
        public fun setAppStoreUri(appStoreUri: Uri?): Builder {
            this.appStoreUri = appStoreUri
            return this
        }

        /**
         * Sets the Airship cloud site for data locality.
         *
         * @param site The airship cloud site.
         * @return The config options builder.
         */
        public fun setSite(site: Site): Builder {
            this.site = site
            return this
        }

        /**
         * Set the flag indicating whether data collection needs to be opted in.
         *
         * This method is deprecated. Use [PrivacyManager] to manage SDK features instead.
         *
         * @param dataCollectionOptInEnabled The flag indicating whether data collection needs to be opted in.
         * @return The config options builder.
         */
        @Deprecated("Use {@link #enabledFeatures} instead.")
        public fun setDataCollectionOptInEnabled(dataCollectionOptInEnabled: Boolean): Builder {
            @Suppress("DEPRECATION")
            this.dataCollectionOptInEnabled = dataCollectionOptInEnabled
            return this
        }

        /**
         * Set the flag indicating whether extended broadcasts are enabled or disabled.
         *
         * When extended broadcasts are enabled, the channel identifier and app key are
         * added to the AIRSHIP_READY broadcast and the channel identifier is included in
         * a new CHANNEL_CREATED broadcast.
         *
         * @param extendedBroadcastsEnabled The flag indicating whether extended broadcasts are enabled or disabled.
         * @return The config options builder.
         */
        public fun setExtendedBroadcastsEnabled(extendedBroadcastsEnabled: Boolean): Builder {
            this.extendedBroadcastsEnabled = extendedBroadcastsEnabled
            return this
        }

        /**
         * Sets the default enabled SDK features. See [PrivacyManager] for more info.
         *
         * @param enabledFeatures The enabled features.
         * @return The config options builder.
         */
        public fun setEnabledFeatures(vararg enabledFeatures: Feature): Builder {
            this.enabledFeatures = Feature.combined(*enabledFeatures)
            return this
        }

        /**
         * Sets the Firebase app name that is used for FCM. If set, the app name must exist in order
         * for Airship to get registration token. The app should be initialized with Firebase before takeOff, or during
         * onAirshipReady callback.
         *
         * @param fcmFirebaseAppName The firebase app name.
         * @return The config options builder.
         */
        public fun setFcmFirebaseAppName(fcmFirebaseAppName: String?): Builder {
            this.fcmFirebaseAppName = fcmFirebaseAppName
            return this
        }

        /**
         * Sets the flag to require initial remote-config for device URLs.
         *
         * @param requireInitialRemoteConfigEnabled `true` to require initial remote-config, otherwise `false`.
         * @return The config options builder.
         */
        public fun setRequireInitialRemoteConfigEnabled(requireInitialRemoteConfigEnabled: Boolean): Builder {
            this.requireInitialRemoteConfigEnabled = requireInitialRemoteConfigEnabled
            return this
        }

        /**
         * Sets if when enabling [com.urbanairship.push.PushManager.userNotificationsEnabled]
         * if the SDK should prompt for permission on Android 13+ devices. Enabled by default.
         *
         * @param enabled `true` to prompt for notifications when user notifications are enabled, otherwise `false`.
         * @return The config options builder.
         */
        public fun setIsPromptForPermissionOnUserNotificationsEnabled(enabled: Boolean): Builder {
            this.isPromptForPermissionOnUserNotificationsEnabled = enabled
            return this
        }

        /**
         * Set the auto pause In-App Automation on launch.
         *
         * @param autoPauseInAppAutomationOnLaunch `true` to auto pause In-App Automation, otherwise `false`.
         * @return The config options builder.
         */
        public fun setAutoPauseInAppAutomationOnLaunch(autoPauseInAppAutomationOnLaunch: Boolean): Builder {
            this.autoPauseInAppAutomationOnLaunch = autoPauseInAppAutomationOnLaunch
            return this
        }

        /**
         * Overrides the input validation used by Preference Center and Scenes.
         *
         * @param overrides provides overrides pending result .
         * @return The config options builder.
         */
        public fun setInputValidationOverrides(overrides: AirshipValidationOverride?): Builder {
            this.validationOverride = overrides
            return this
        }

        /**
         * Set the reset enabled features option.
         *
         * @param resetEnabledFeatures `true` to reset enabled features on launch, otherwise `false`.
         * @return The config options builder.
         */
        public fun setResetEnabledFeatures(resetEnabledFeatures: Boolean): Builder {
            this.resetEnabledFeatures = resetEnabledFeatures
            return this
        }

        /**
         * Set the Airship channel create option
         *
         * @param option [com.urbanairship.channel.AirshipChannelCreateOption] define channel creation type.
         * @return The config options builder.
         */
        public fun setAirshipChannelCreateOption(option: AirshipChannelCreateOption?): Builder {
            this.channelCreateOption = option
            return this
        }

        /**
         * Builds the config options.
         *
         * @return The built config options.
         */
        public fun build(): AirshipConfigOptions {
            if (inProduction == null) {
                inProduction = false
            }

            if (productionAppKey != null && productionAppKey == developmentAppKey) {
                UALog.w("Production App Key matches Development App Key")
            }

            if (productionAppSecret != null && productionAppSecret == developmentAppSecret) {
                UALog.w("Production App Secret matches Development App Secret")
            }

            @Suppress("DEPRECATION")
            if (dataCollectionOptInEnabled) {
                UALog.w("dataCollectionOptInEnabled is deprecated. Use enabledFeatures instead.")
                if (enabledFeatures === Feature.ALL) {
                    enabledFeatures = Feature.NONE
                }
            }

            return AirshipConfigOptions(this)
        }

        public companion object {

            // Default airship config properties filename
            private const val DEFAULT_PROPERTIES_FILENAME = "airshipconfig.properties"
            private const val CONFIG_ELEMENT = "AirshipConfigOptions"

            /*
         * Common config fields
         */
            private const val FIELD_APP_KEY = "appKey"
            private const val FIELD_APP_SECRET = "appSecret"
            private const val FIELD_PRODUCTION_APP_KEY = "productionAppKey"
            private const val FIELD_PRODUCTION_APP_SECRET = "productionAppSecret"
            private const val FIELD_DEVELOPMENT_APP_KEY = "developmentAppKey"
            private const val FIELD_DEVELOPMENT_APP_SECRET = "developmentAppSecret"
            private const val FIELD_LEGACY_DEVICE_URL = "hostURL"
            private const val FIELD_DEVICE_URL = "deviceUrl"
            private const val FIELD_LEGACY_ANALYTICS_SERVER = "analyticsServer"
            private const val FIELD_ANALYTICS_URL = "analyticsUrl"
            private const val FIELD_LEGACY_REMOTE_DATA_URL = "remoteDataURL"
            private const val FIELD_REMOTE_DATA_URL = "remoteDataUrl"
            private const val FIELD_GCM_SENDER = "gcmSender"
            private const val FIELD_ALLOWED_TRANSPORTS = "allowedTransports"
            private const val FIELD_URL_ALLOW_LIST = "urlAllowList"
            private const val FIELD_URL_ALLOW_LIST_SCOPE_JAVASCRIPT_INTERFACE =
                "urlAllowListScopeJavaScriptInterface"
            private const val FIELD_URL_ALLOW_LIST_SCOPE_OPEN_URL = "urlAllowListScopeOpenUrl"
            private const val FIELD_IN_PRODUCTION = "inProduction"
            private const val FIELD_ANALYTICS_ENABLED = "analyticsEnabled"
            private const val FIELD_BACKGROUND_REPORTING_INTERVAL_MS =
                "backgroundReportingIntervalMS"
            private const val FIELD_DEVELOPMENT_LOG_LEVEL = "developmentLogLevel"
            private const val FIELD_PRODUCTION_LOG_LEVEL = "productionLogLevel"
            private const val FIELD_LOG_LEVEL = "logLevel"
            private const val FIELD_DEVELOPMENT_LOG_PRIVACY_LEVEL = "developmentLogPrivacyLevel"
            private const val FIELD_PRODUCTION_LOG_PRIVACY_LEVEL = "productionLogPrivacyLevel"
            private const val FIELD_AUTO_LAUNCH_APPLICATION = "autoLaunchApplication"
            private const val FIELD_CHANNEL_CREATION_DELAY_ENABLED = "channelCreationDelayEnabled"
            private const val FIELD_CHANNEL_CAPTURE_ENABLED = "channelCaptureEnabled"
            private const val FIELD_NOTIFICATION_ICON = "notificationIcon"
            private const val FIELD_NOTIFICATION_LARGE_ICON = "notificationLargeIcon"
            private const val FIELD_NOTIFICATION_ACCENT_COLOR = "notificationAccentColor"
            private const val FIELD_WALLET_URL = "walletUrl"
            private const val FIELD_NOTIFICATION_CHANNEL = "notificationChannel"
            private const val FIELD_FCM_FIREBASE_APP_NAME = "fcmFirebaseAppName"
            private const val FIELD_FCM_SENDER_ID = "fcmSenderId"
            private const val FIELD_PRODUCTION_FCM_SENDER_ID = "productionFcmSenderId"
            private const val FIELD_DEVELOPMENT_FCM_SENDER_ID = "developmentFcmSenderId"
            private const val FIELD_CUSTOM_PUSH_PROVIDER = "customPushProvider"
            private const val FIELD_APP_STORE_URI = "appStoreUri"
            private const val FIELD_SITE = "site"
            private const val FIELD_DATA_COLLECTION_OPT_IN_ENABLED = "dataCollectionOptInEnabled"
            private const val FIELD_EXTENDED_BROADCASTS_ENABLED = "extendedBroadcastsEnabled"
            private const val FIELD_REQUIRE_INITIAL_REMOTE_CONFIG_ENABLED =
                "requireInitialRemoteConfigEnabled"
            private const val FIELD_ENABLED_FEATURES = "enabledFeatures"
            private const val FIELD_RESET_ENABLED_FEATURES = "resetEnabledFeatures"
            private const val FIELD_INITIAL_CONFIG_URL = "initialConfigUrl"
            private const val FIELD_IS_PROMPT_FOR_PERMISSION_ON_USER_NOTIFICATIONS_ENABLED =
                "isPromptForPermissionOnUserNotificationsEnabled"
            private const val FIELD_AUTO_PAUSE_IN_APP_AUTOMATION_ON_LAUNCH =
                "autoPauseInAppAutomationOnLaunch"
        }
    }

    public companion object {

        /**
         * Maps to the feature [PrivacyManager.Feature.IN_APP_AUTOMATION] when used in the properties or xml config.
         */
        public const val FEATURE_IN_APP_AUTOMATION: String = "in_app_automation"

        /**
         * Maps to the feature [PrivacyManager.Feature.TAGS_AND_ATTRIBUTES] when used in the properties or xml config.
         */
        public const val FEATURE_TAGS_AND_ATTRIBUTES: String = "tags_and_attributes"

        /**
         * Maps to the feature [PrivacyManager.Feature.MESSAGE_CENTER] when used in the properties or xml config.
         */
        public const val FEATURE_MESSAGE_CENTER: String = "message_center"

        /**
         * Maps to the feature [PrivacyManager.Feature.ANALYTICS] when used in the properties or xml config.
         */
        public const val FEATURE_ANALYTICS: String = "analytics"

        /**
         * Maps to the feature [PrivacyManager.Feature.PUSH] when used in the properties or xml config.
         */
        public const val FEATURE_PUSH: String = "push"

        /**
         * Maps to the feature [PrivacyManager.Feature.CONTACTS] when used in the properties or xml config.
         */
        public const val FEATURE_CONTACTS: String = "contacts"

        /**
         * Maps to the feature [PrivacyManager.Feature.NONE] when used in the properties or xml config.
         */
        public const val FEATURE_NONE: String = "none"

        /**
         * Maps to the feature [PrivacyManager.Feature.FEATURE_FLAGS] when used in the properties or xml config.
         */
        public const val FEATURE_FEATURE_FLAGS: String = "feature_flags"

        /**
         * Maps to the feature [PrivacyManager.Feature.ALL] when used in the properties or xml config.
         */
        public const val FEATURE_ALL: String = "all"

        // EU cloud site
        private const val EU_DEVICE_URL = "https://device-api.asnapieu.com/"
        private const val EU_ANALYTICS_URL = "https://combine.asnapieu.com/"
        private const val EU_REMOTE_DATA_URL = "https://remote-data.asnapieu.com/"
        private const val EU_WALLET_URL = "https://wallet-api.asnapieu.com"

        // US cloud site
        private const val US_DEVICE_URL = "https://device-api.urbanairship.com/"
        private const val US_ANALYTICS_URL = "https://combine.urbanairship.com/"
        private const val US_REMOTE_DATA_URL = "https://remote-data.urbanairship.com/"
        private const val US_WALLET_URL = "https://wallet-api.urbanairship.com"

        private val MIN_BG_REPORTING_INTERVAL = 1.minutes.inWholeMilliseconds
        private val MAX_BG_REPORTING_INTERVAL = 24.hours.inWholeMilliseconds

        private val DEFAULT_PRODUCTION_LOG_LEVEL = LogLevel.ERROR
        private val DEFAULT_DEVELOPMENT_LOG_LEVEL = LogLevel.DEBUG
        private val DEFAULT_BG_REPORTING_INTERVAL = 24.hours.inWholeMilliseconds

        private val APP_CREDENTIAL_PATTERN: Pattern = Pattern.compile("^[a-zA-Z0-9\\-_]{22}$")

        /**
         * The ADM transport type for Push.
         */
        public const val ADM_TRANSPORT: String = "ADM"

        /**
         * The FCM transport type for Push.
         */
        public const val FCM_TRANSPORT: String = "FCM"

        /**
         * The HMS transport type for Push.
         */
        public const val HMS_TRANSPORT: String = "HMS"

        /**
         * Factory method to create an AirshipConfig builder.
         *
         * @return A new builder.
         */
        @JvmStatic
        public fun newBuilder(): Builder {
            return Builder()
        }

        /**
         * Parses [Site] from a String.
         *
         * @param value The value to parse.
         * @return The parsed site value.
         * @throws IllegalArgumentException If the value is invalid.
         */
        @kotlin.jvm.Throws(IllegalArgumentException::class)
        private fun parseSite(value: String?): Site {
            val content = value?.uppercase()
                ?: throw IllegalArgumentException("Site cannot be null")

            return Site.entries.firstOrNull { it.site == value }
                ?: throw IllegalArgumentException("Invalid site: $content")
        }

        private fun parseLogPrivacyLevel(privacyLevel: String?): PrivacyLevel {
            return when(privacyLevel?.uppercase()) {
                PrivacyLevel.PUBLIC.name -> PrivacyLevel.PUBLIC
                PrivacyLevel.PRIVATE.name -> PrivacyLevel.PRIVATE
                else -> throw IllegalArgumentException("Invalid log privacy level: $privacyLevel")
            }
        }
    }
}

/**
 * Creates a new [AirshipConfigOptions] with the given options.
 *
 * @param block A lambda function that configures the `Builder`.
 * @return A new `AirshipConfigOptions` instance.
 */
@JvmSynthetic
public fun airshipConfigOptions(block: AirshipConfigOptions.Builder.() -> Unit): AirshipConfigOptions {
    val builder = AirshipConfigOptions.newBuilder()
    builder.block()
    return builder.build()
}
