/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.Checks;
import com.urbanairship.util.ConfigParser;
import com.urbanairship.util.PropertiesConfigParser;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.XmlConfigParser;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringDef;
import androidx.annotation.XmlRes;
import androidx.core.app.NotificationCompat;

/**
 * This class holds the set of options necessary to properly initialize
 * {@link com.urbanairship.UAirship}.
 */
public class AirshipConfigOptions {

    /**
     * Config exceptions when trying to load properties from a resource.
     */
    public static class ConfigException extends Exception {

        /**
         * Default constructor.
         *
         * @param message The message.
         * @param throwable The cause.
         */
        public ConfigException(@NonNull String message, @Nullable Throwable throwable) {
            super(message, throwable);
        }
    }

    /**
     * Maps to the feature {@link PrivacyManager.Feature#IN_APP_AUTOMATION} when used in the properties or xml config.
     */
    @NonNull
    public static final String FEATURE_IN_APP_AUTOMATION = "in_app_automation";

    /**
     * Maps to the feature {@link PrivacyManager.Feature#TAGS_AND_ATTRIBUTES} when used in the properties or xml config.
     */
    @NonNull
    public static final String FEATURE_TAGS_AND_ATTRIBUTES = "tags_and_attributes";

    /**
     * Maps to the feature {@link PrivacyManager.Feature#MESSAGE_CENTER} when used in the properties or xml config.
     */
    @NonNull
    public static final String FEATURE_MESSAGE_CENTER = "message_center";

    /**
     * Maps to the feature {@link PrivacyManager.Feature#ANALYTICS} when used in the properties or xml config.
     */
    @NonNull
    public static final String FEATURE_ANALYTICS = "analytics";

    /**
     * Maps to the feature {@link PrivacyManager.Feature#PUSH} when used in the properties or xml config.
     */
    @NonNull
    public static final String FEATURE_PUSH = "push";

    /**
     * Maps to the feature {@link PrivacyManager.Feature#CONTACTS} when used in the properties or xml config.
     */
    @NonNull
    public static final String FEATURE_CONTACTS = "contacts";

    /**
     * Maps to the feature {@link PrivacyManager.Feature#NONE} when used in the properties or xml config.
     */
    @NonNull
    public static final String FEATURE_NONE = "none";

    /**
     * Maps to the feature {@link PrivacyManager.Feature#ALL} when used in the properties or xml config.
     */
    @NonNull
    public static final String FEATURE_ALL = "all";

    // EU cloud site
    private static final String EU_DEVICE_URL = "https://device-api.asnapieu.com/";
    private static final String EU_ANALYTICS_URL = "https://combine.asnapieu.com/";
    private static final String EU_REMOTE_DATA_URL = "https://remote-data.asnapieu.com/";
    private static final String EU_WALLET_URL = "https://wallet-api.asnapieu.com";

    // US cloud site
    private static final String US_DEVICE_URL = "https://device-api.urbanairship.com/";
    private static final String US_ANALYTICS_URL = "https://combine.urbanairship.com/";
    private static final String US_REMOTE_DATA_URL = "https://remote-data.urbanairship.com/";
    private static final String US_WALLET_URL = "https://wallet-api.urbanairship.com";

    private final static long MIN_BG_REPORTING_INTERVAL_MS = 60 * 1000; // 1 minute
    private final static long MAX_BG_REPORTING_INTERVAL_MS = 24 * 60 * 60 * 1000; // 24 hours

    private static final int DEFAULT_PRODUCTION_LOG_LEVEL = Log.ERROR;
    private static final int DEFAULT_DEVELOPMENT_LOG_LEVEL = Log.DEBUG;
    private static final long DEFAULT_BG_REPORTING_INTERVAL_MS = 24 * 60 * 60 * 1000; // 24 hours

    private static final Pattern APP_CREDENTIAL_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{22}$");

    @StringDef({ SITE_US, SITE_EU })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Site {}

    /**
     * US data site. In order to use this site, your
     * project must be created at go.airship.com
     */
    @NonNull
    public static final String SITE_US = "US";

    /**
     * EU data site. In order to use this site, your
     * project must be created at go.airship.eu
     */
    @NonNull
    public static final String SITE_EU = "EU";

    /**
     * The ADM transport type for Push.
     */
    @NonNull
    public static final String ADM_TRANSPORT = "ADM";

    /**
     * The FCM transport type for Push.
     */
    @NonNull
    public static final String FCM_TRANSPORT = "FCM";

    /**
     * The HMS transport type for Push.
     */
    @NonNull
    public static final String HMS_TRANSPORT = "HMS";

    /**
     * Airship app key.
     * <p>
     * This string is generated automatically when you create an app in the Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    @NonNull
    public final String appKey;

    /**
     * Airship app secret.
     * <p>
     * This string is generated automatically when you create an app in the Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    @NonNull
    public final String appSecret;

    /**
     * The device URL.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public final String deviceUrl;

    /**
     * The analytics Url.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public final String analyticsUrl;

    /**
     * The remote data server URL.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public final String remoteDataUrl;

    /**
     * The wallet URL.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public final String walletUrl;

    /**
     * Optional app store link when using the rate app action. If not set,
     * the action will generate it using hte app's current package name.
     * <p>
     * Example: "market://details?id=com.example.android"
     */
    @Nullable
    public final Uri appStoreUri;

    /**
     * The transport types allowed for Push.
     * <p>
     * Defaults to ADM, FCM.
     */
    @NonNull
    public final List<String> allowedTransports;

    /**
     * Custom push provider.
     *
     * @hide
     */
    @Nullable
    public final PushProvider customPushProvider;

    /**
     * Additional URLs that will be added to the allow list for both {@link UrlAllowList#SCOPE_OPEN_URL} and
     * {@link UrlAllowList#SCOPE_JAVASCRIPT_INTERFACE} scopes.
     *
     * See {@link UrlAllowList#addEntry(String)} for valid URL patterns.
     */
    @NonNull
    public final List<String> urlAllowList;

    /**
     * Additional URLs that will be added to the allow list for scope {@link UrlAllowList#SCOPE_JAVASCRIPT_INTERFACE}.
     *
     * See {@link UrlAllowList#addEntry(String)} for valid URL patterns.
     */
    @NonNull
    public final List<String> urlAllowListScopeJavaScriptInterface;

    /**
     * Additional URLs that will be added to the allow list for scope {@link UrlAllowList#SCOPE_OPEN_URL}.
     * See {@link UrlAllowList#addEntry(String)} for valid URL patterns.
     */
    @NonNull
    public final List<String> urlAllowListScopeOpenUrl;

    final boolean isAllowListScopeOpenSet;
    final boolean isAllowListSet;

    /**
     * Flag indicating whether the application will use analytics.
     * <p>
     * The flag defaults to true.
     */
    public final boolean analyticsEnabled;

    /**
     * Minimum delta in milliseconds between analytics uploads when
     * adding location events while in the background.
     * <p>
     * Defaults to 15 minutes.
     */
    public final long backgroundReportingIntervalMS;

    /**
     * Logger level when the application is in debug mode. Possible values are:
     * <br><ul>
     * <li>ASSERT
     * <li>NONE
     * <li>DEBUG
     * <li>ERROR
     * <li>INFO
     * <li>VERBOSE
     * <li>WARN
     * </ul><br>
     * Defaults to <code>DEBUG</code>
     */
    public final int logLevel;

    /**
     * Flag indicating whether or not to launch the launcher activity when a push notification or push
     * notification button is opened and the application intent receiver did not launch an activity.
     * <p>
     * Defaults to true.
     */
    public final boolean autoLaunchApplication;

    /**
     * Flag indicating whether channel creation delay is enabled or not.
     * <p>
     * The flag defaults to false.
     */
    public final boolean channelCreationDelayEnabled;

    /**
     * Flag indicating whether channel capture feature is enabled or not.
     * <p>
     * The flag defaults to true.
     */
    public final boolean channelCaptureEnabled;

    /**
     * Flag indicating if the data collection opt-in is enabled.
     * <p>
     * The flag defaults to false
     *
     * @deprecated Use {@link #enabledFeatures} instead.
     */
    @Deprecated
    public final boolean dataCollectionOptInEnabled;

    /**
     * Default enabled Airship features for the app. For more details, see {@link PrivacyManager}.
     * Defaults to {@link PrivacyManager.Feature#ALL}.
     *
     * When specifying the features in either xml or a properties file, use one of the
     * names for convenience:
     * - {@link #FEATURE_ALL}
     * - {@link #FEATURE_NONE}
     * - {@link #FEATURE_MESSAGE_CENTER}
     * - {@link #FEATURE_TAGS_AND_ATTRIBUTES}
     * - {@link #FEATURE_IN_APP_AUTOMATION}
     * - {@link #FEATURE_CONTACTS}
     * - {@link #FEATURE_ANALYTICS}
     * - {@link #FEATURE_PUSH}
     */
    public final PrivacyManager.Feature enabledFeatures;

    /**
     * Allows resetting enabled features to match the runtime config defaults on each takeOff
     * Defaults to <code>false</code>.
     */
    public final boolean resetEnabledFeatures;

    /**
     * Flag indicating whether or not to perform extended broadcasts.
     *
     * When extended broadcasts are enabled, the channel identifier and app key are
     * added to the AIRSHIP_READY broadcast and the channel identifier is included in
     * a new CHANNEL_CREATED broadcast.
     * <p>
     * Defaults to <code>false</code>.
     */
    public final boolean extendedBroadcastsEnabled;

    /**
     * Notification icon.
     */
    @DrawableRes
    public final int notificationIcon;

    /**
     * Large notification icon.
     */
    @DrawableRes
    public final int notificationLargeIcon;

    /**
     * Notification accent color.
     */
    @ColorInt
    public final int notificationAccentColor;

    /**
     * The default notification channel.
     */
    @Nullable
    public final String notificationChannel;

    /**
     * Flag indicating whether the application is in production.
     * <p>
     * Defaults to <code>false</code>.
     */
    public final boolean inProduction;

    /**
     * Flag indicating whether the SDK will wait for an initial remote config instead of falling back on default API URLs.
     * <p>
     * Defaults to <code>true</code>.
     */
    public final boolean requireInitialRemoteConfigEnabled;

    /**
     * The Firebase app name to use for FCM instead of the default app.
     */
    @Nullable
    public final String fcmFirebaseAppName;

    /**
     * The initial config URL.
     */
    @Nullable
    public final String initialConfigUrl;

    /**
     * Flag indicating whether or not the SDK will automatically prompt for notification permission
     * when enabling notifications with {@link com.urbanairship.push.PushManager#setUserNotificationsEnabled(boolean)}.
     */
    public final boolean isPromptForPermissionOnUserNotificationsEnabled;

    /**
     * Flag indicating whether or not the SDK will automatically pause In-App Automation during app launch.
     * Defaults to false.
     */
    public final boolean autoPauseInAppAutomationOnLaunch;

    private AirshipConfigOptions(@NonNull Builder builder) {
        if (builder.inProduction) {
            this.appKey = firstOrEmpty(builder.productionAppKey, builder.appKey);
            this.appSecret = firstOrEmpty(builder.productionAppSecret, builder.appSecret);
            this.logLevel = first(builder.productionLogLevel, builder.logLevel, DEFAULT_PRODUCTION_LOG_LEVEL);
        } else {
            this.appKey = firstOrEmpty(builder.developmentAppKey, builder.appKey);
            this.appSecret = firstOrEmpty(builder.developmentAppSecret, builder.appSecret);
            this.logLevel = first(builder.developmentLogLevel, builder.logLevel, DEFAULT_DEVELOPMENT_LOG_LEVEL);
        }

        switch (builder.site) {
            case SITE_EU:
                this.deviceUrl = firstOrEmpty(builder.deviceUrl, EU_DEVICE_URL);
                this.analyticsUrl = firstOrEmpty(builder.analyticsUrl, EU_ANALYTICS_URL);
                this.remoteDataUrl = firstOrEmpty(builder.remoteDataUrl, EU_REMOTE_DATA_URL);
                this.walletUrl = firstOrEmpty(builder.walletUrl, EU_WALLET_URL);
                break;

            case SITE_US:
            default:
                this.deviceUrl = firstOrEmpty(builder.deviceUrl, US_DEVICE_URL);
                this.analyticsUrl = firstOrEmpty(builder.analyticsUrl, US_ANALYTICS_URL);
                this.remoteDataUrl = firstOrEmpty(builder.remoteDataUrl, US_REMOTE_DATA_URL);
                this.walletUrl = firstOrEmpty(builder.walletUrl, US_WALLET_URL);
                break;
        }

        this.allowedTransports = Collections.unmodifiableList(new ArrayList<>(builder.allowedTransports));
        this.urlAllowList = copyOrEmpty(builder.urlAllowList);
        this.urlAllowListScopeJavaScriptInterface = copyOrEmpty(builder.urlAllowListScopeJavaScriptInterface);
        this.urlAllowListScopeOpenUrl = copyOrEmpty(builder.urlAllowListScopeOpenUrl);
        this.isAllowListScopeOpenSet = builder.isAllowListScopeOpenSet;
        this.isAllowListSet = builder.isAllowListSet;
        this.inProduction = builder.inProduction;
        this.analyticsEnabled = builder.analyticsEnabled;
        this.backgroundReportingIntervalMS = builder.backgroundReportingIntervalMS;
        this.autoLaunchApplication = builder.autoLaunchApplication;
        this.channelCreationDelayEnabled = builder.channelCreationDelayEnabled;
        this.channelCaptureEnabled = builder.channelCaptureEnabled;
        this.notificationIcon = builder.notificationIcon;
        this.notificationLargeIcon = builder.notificationLargeIcon;
        this.notificationAccentColor = builder.notificationAccentColor;
        this.notificationChannel = builder.notificationChannel;
        this.customPushProvider = builder.customPushProvider;
        this.appStoreUri = builder.appStoreUri;
        this.dataCollectionOptInEnabled = builder.dataCollectionOptInEnabled;
        this.enabledFeatures = builder.enabledFeatures;
        this.resetEnabledFeatures = builder.resetEnabledFeatures;
        this.extendedBroadcastsEnabled = builder.extendedBroadcastsEnabled;
        this.requireInitialRemoteConfigEnabled = builder.requireInitialRemoteConfigEnabled;
        this.fcmFirebaseAppName = builder.fcmFirebaseAppName;
        this.initialConfigUrl = builder.initialConfigUrl;
        this.isPromptForPermissionOnUserNotificationsEnabled = builder.isPromptForPermissionOnUserNotificationsEnabled;
        this.autoPauseInAppAutomationOnLaunch = builder.autoPauseInAppAutomationOnLaunch;
    }

    private static <T> List<T> copyOrEmpty(@Nullable List<T> list) {
        if (list == null) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(list);
        }
    }

    /**
     * Validates the config.
     *
     * @throws IllegalArgumentException if the app key or secret are invalid.
     */
    public void validate() {
        String modeString = inProduction ? "production" : "development";

        if (!APP_CREDENTIAL_PATTERN.matcher(appKey).matches()) {
            throw new IllegalArgumentException("AirshipConfigOptions: " + appKey + " is not a valid " + modeString + " app key");
        }

        if (!APP_CREDENTIAL_PATTERN.matcher(appSecret).matches()) {
            throw new IllegalArgumentException("AirshipConfigOptions: " + appSecret + " is not a valid " + modeString + " app secret");
        }

        if (backgroundReportingIntervalMS < MIN_BG_REPORTING_INTERVAL_MS) {
            UALog.w("AirshipConfigOptions - The backgroundReportingIntervalMS %s may decrease battery life.", backgroundReportingIntervalMS);
        } else if (backgroundReportingIntervalMS > MAX_BG_REPORTING_INTERVAL_MS) {
            UALog.w("AirshipConfigOptions - The backgroundReportingIntervalMS %s may provide less detailed analytic reports.", backgroundReportingIntervalMS);
        }
    }

    /**
     * Factory method to create an AirshipConfig builder.
     *
     * @return A new builder.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Parses {@link Site} from a String.
     *
     * @param value The value to parse.
     * @return The parsed site value.
     * @throws IllegalArgumentException If the value is invalid.
     */
    @NonNull
    @Site
    private static String parseSite(String value) {
        if (SITE_EU.equalsIgnoreCase(value)) {
            return SITE_EU;
        }

        if (SITE_US.equalsIgnoreCase(value)) {
            return SITE_US;
        }

        throw new IllegalArgumentException("Invalid site: " + value);
    }

    /**
     * Returns the first String that is not empty or null.
     *
     * @param args The Strings.
     * @return The first String that is not empty or null.
     */
    @Nullable
    private static String firstOrNull(@NonNull String... args) {
        for (String arg : args) {
            if (!UAStringUtil.isEmpty(arg)) {
                return arg;
            }
        }
        return null;
    }

    /**
     * Returns the first String that is not empty or an empty String.
     *
     * @param args The Strings.
     * @return The first String that is not empty or an empty String.
     */
    @NonNull
    private static String firstOrEmpty(@NonNull String... args) {
        for (String arg : args) {
            if (!UAStringUtil.isEmpty(arg)) {
                return arg;
            }
        }
        return "";
    }

    /**
     * Returns the first nonnull Integer or 0.
     *
     * @param args The Integers.
     * @return The first nonnull Integer or 0.
     */
    private static int first(Integer... args) {
        for (Integer arg : args) {
            if (arg != null) {
                return arg;
            }
        }
        return 0;
    }

    /**
     * Airship config builder.
     */
    public static final class Builder {

        // Default airship config properties filename
        private final static String DEFAULT_PROPERTIES_FILENAME = "airshipconfig.properties";
        private static final String CONFIG_ELEMENT = "AirshipConfigOptions";

        /*
         * Common config fields
         */
        private static final String FIELD_APP_KEY = "appKey";
        private static final String FIELD_APP_SECRET = "appSecret";
        private static final String FIELD_PRODUCTION_APP_KEY = "productionAppKey";
        private static final String FIELD_PRODUCTION_APP_SECRET = "productionAppSecret";
        private static final String FIELD_DEVELOPMENT_APP_KEY = "developmentAppKey";
        private static final String FIELD_DEVELOPMENT_APP_SECRET = "developmentAppSecret";
        private static final String FIELD_LEGACY_DEVICE_URL = "hostURL";
        private static final String FIELD_DEVICE_URL = "deviceUrl";
        private static final String FIELD_LEGACY_ANALYTICS_SERVER = "analyticsServer";
        private static final String FIELD_ANALYTICS_URL = "analyticsUrl";
        private static final String FIELD_LEGACY_REMOTE_DATA_URL = "remoteDataURL";
        private static final String FIELD_REMOTE_DATA_URL = "remoteDataUrl";
        private static final String FIELD_GCM_SENDER = "gcmSender";
        private static final String FIELD_ALLOWED_TRANSPORTS = "allowedTransports";
        private static final String FIELD_URL_ALLOW_LIST = "urlAllowList";
        private static final String FIELD_URL_ALLOW_LIST_SCOPE_JAVASCRIPT_INTERFACE = "urlAllowListScopeJavaScriptInterface";
        private static final String FIELD_URL_ALLOW_LIST_SCOPE_OPEN_URL = "urlAllowListScopeOpenUrl";
        private static final String FIELD_IN_PRODUCTION = "inProduction";
        private static final String FIELD_ANALYTICS_ENABLED = "analyticsEnabled";
        private static final String FIELD_BACKGROUND_REPORTING_INTERVAL_MS = "backgroundReportingIntervalMS";
        private static final String FIELD_DEVELOPMENT_LOG_LEVEL = "developmentLogLevel";
        private static final String FIELD_PRODUCTION_LOG_LEVEL = "productionLogLevel";
        private static final String FIELD_LOG_LEVEL = "logLevel";
        private static final String FIELD_AUTO_LAUNCH_APPLICATION = "autoLaunchApplication";
        private static final String FIELD_CHANNEL_CREATION_DELAY_ENABLED = "channelCreationDelayEnabled";
        private static final String FIELD_CHANNEL_CAPTURE_ENABLED = "channelCaptureEnabled";
        private static final String FIELD_NOTIFICATION_ICON = "notificationIcon";
        private static final String FIELD_NOTIFICATION_LARGE_ICON = "notificationLargeIcon";
        private static final String FIELD_NOTIFICATION_ACCENT_COLOR = "notificationAccentColor";
        private static final String FIELD_WALLET_URL = "walletUrl";
        private static final String FIELD_NOTIFICATION_CHANNEL = "notificationChannel";
        private static final String FIELD_FCM_FIREBASE_APP_NAME = "fcmFirebaseAppName";
        private static final String FIELD_FCM_SENDER_ID = "fcmSenderId";
        private static final String FIELD_PRODUCTION_FCM_SENDER_ID = "productionFcmSenderId";
        private static final String FIELD_DEVELOPMENT_FCM_SENDER_ID = "developmentFcmSenderId";
        private static final String FIELD_CUSTOM_PUSH_PROVIDER = "customPushProvider";
        private static final String FIELD_APP_STORE_URI = "appStoreUri";
        private static final String FIELD_SITE = "site";
        private static final String FIELD_DATA_COLLECTION_OPT_IN_ENABLED = "dataCollectionOptInEnabled";
        private static final String FIELD_EXTENDED_BROADCASTS_ENABLED = "extendedBroadcastsEnabled";
        private static final String FIELD_REQUIRE_INITIAL_REMOTE_CONFIG_ENABLED = "requireInitialRemoteConfigEnabled";
        private static final String FIELD_ENABLED_FEATURES = "enabledFeatures";
        private static final String FIELD_RESET_ENABLED_FEATURES = "resetEnabledFeatures";
        private static final String FIELD_INITIAL_CONFIG_URL = "initialConfigUrl";
        private static final String FIELD_IS_PROMPT_FOR_PERMISSION_ON_USER_NOTIFICATIONS_ENABLED = "isPromptForPermissionOnUserNotificationsEnabled";
        private static final String FIELD_AUTO_PAUSE_IN_APP_AUTOMATION_ON_LAUNCH = "autoPauseInAppAutomationOnLaunch";

        private String appKey;
        private String appSecret;
        private String productionAppKey;
        private String productionAppSecret;
        private String developmentAppKey;
        private String developmentAppSecret;
        private String deviceUrl;
        private String analyticsUrl;
        private String remoteDataUrl;
        private List<String> allowedTransports = new ArrayList<>(Arrays.asList(ADM_TRANSPORT, FCM_TRANSPORT, HMS_TRANSPORT));
        private List<String> urlAllowList = null;
        private List<String> urlAllowListScopeJavaScriptInterface = null;
        private List<String> urlAllowListScopeOpenUrl = null;
        private boolean isAllowListScopeOpenSet = false;
        private boolean isAllowListSet = false;
        private Boolean inProduction = null;
        private boolean analyticsEnabled = true;
        private long backgroundReportingIntervalMS = DEFAULT_BG_REPORTING_INTERVAL_MS;
        private Integer developmentLogLevel;
        private Integer productionLogLevel;
        private Integer logLevel;
        private boolean autoLaunchApplication = true;
        private boolean channelCreationDelayEnabled = false;
        private boolean channelCaptureEnabled = true;
        private int notificationIcon;
        private int notificationLargeIcon;
        private int notificationAccentColor = NotificationCompat.COLOR_DEFAULT;
        private String walletUrl;
        private String notificationChannel;
        private PushProvider customPushProvider;
        private Uri appStoreUri;
        private boolean dataCollectionOptInEnabled;
        private boolean extendedBroadcastsEnabled;
        private @Site
        String site = SITE_US;

        public PrivacyManager.Feature enabledFeatures = PrivacyManager.Feature.ALL;
        public boolean resetEnabledFeatures = false;

        private boolean requireInitialRemoteConfigEnabled = true;
        private String fcmFirebaseAppName;

        private String initialConfigUrl;

        private boolean isPromptForPermissionOnUserNotificationsEnabled = true;

        private boolean autoPauseInAppAutomationOnLaunch = false;

        /**
         * Apply the options from the default properties file {@code airshipconfig.properties}.
         * <p>
         * See {@link #applyProperties(Context, String)}.
         *
         * @param context The application context
         * @return The config option builder.
         */
        @NonNull
        public Builder applyDefaultProperties(@NonNull Context context) {
            return applyProperties(context, DEFAULT_PROPERTIES_FILENAME);
        }

        /**
         * Same as {@link #applyDefaultProperties(Context)}, but throws an exception instead of
         * logging an error.
         *
         * @param context The application context
         * @return The config option builder.
         * @throws ConfigException
         */
        @NonNull
        public Builder tryApplyDefaultProperties(@NonNull Context context) throws ConfigException {
            return tryApplyProperties(context, DEFAULT_PROPERTIES_FILENAME);
        }

        /**
         * Apply the options from a given properties file. The properties file should
         * be available in the assets directory. The properties file can define any of the
         * public {@link AirshipConfigOptions} fields. Example:
         * <pre>
         * {@code
         * # App Credentials
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
         * }
         * </pre>
         *
         * @param context The application context.
         * @param propertiesFile The name of the properties file in the assets directory.
         * @return The config option builder.
         */
        @NonNull
        public Builder applyProperties(@NonNull Context context, @NonNull String propertiesFile) {
            try {
                tryApplyProperties(context, propertiesFile);
            } catch (Exception e) {
                UALog.e(e);
            }

            return this;
        }

        /**
         * Same as {@link #applyProperties(Context, String)}, but throws an exception instead of
         * logging an error.
         *
         * @param context The application context.
         * @param propertiesFile The name of the properties file in the assets directory.
         * @return The config option builder.
         * @throws ConfigException
         */
        @NonNull
        public Builder tryApplyProperties(@NonNull Context context, @NonNull String propertiesFile) throws ConfigException {
            try {
                ConfigParser configParser = PropertiesConfigParser.fromAssets(context, propertiesFile);
                applyConfigParser(context, configParser);
            } catch (Exception e) {
                throw new ConfigException("Unable to apply config from file " + propertiesFile, e);
            }

            return this;
        }

        /**
         * Applies properties from a given Properties object.
         *
         * @param context The application context.
         * @param properties The properties
         * @return The config option builder.
         */
        @NonNull
        public Builder applyProperties(@NonNull Context context, @NonNull Properties properties) {
            try {
                ConfigParser configParser = PropertiesConfigParser.fromProperties(context, properties);
                applyConfigParser(context, configParser);
            } catch (Exception e) {
                UALog.e(e);
            }

            return this;
        }

        /**
         * The same as {@link #applyProperties(Context, Properties)}, but throws an exception
         * instead of logging an error.
         *
         * @param context The application context.
         * @param properties The properties
         * @return The config option builder.
         * @throws ConfigException
         */
        @NonNull
        public Builder tryApplyProperties(@NonNull Context context, @NonNull Properties properties) throws ConfigException {
            try {
                ConfigParser configParser = PropertiesConfigParser.fromProperties(context, properties);
                applyConfigParser(context, configParser);
            } catch (Exception e) {
                throw new ConfigException("Unable to apply config.", e);
            }
            return this;
        }

        /**
         * Apply options from a xml resource file. The XML file must contain the element {@code AirshipConfigOptions}
         * and any public {@link AirshipConfigOptions} fields should be set as attributes on the element.
         * Example:
         * <pre>
         * {@code
         * <AirshipConfigOptions
         *    notificationIcon = "@drawable/ic_notification"
         *    notificationAccentColor = "@color/color_accent"
         *    inProduction = "false"
         *    productionAppKey = "Your Production App Key"
         *    productionAppSecret = "Your Production App Secret"
         *    productionLogLevel = "NONE"
         *    developmentAppKey = "Your Development App Key"
         *    developmentAppSecret = "Your Development App Secret"
         *    developmentLogLevel = "VERBOSE"
         * }
         * </pre>
         *
         * @param context The application context.
         * @param xmlResourceId The xml resource ID.
         * @return The config option builder.
         */
        @NonNull
        public Builder applyConfig(@NonNull Context context, @XmlRes int xmlResourceId) {
            try {
                tryApplyConfig(context, xmlResourceId);
            } catch (Exception e) {
                UALog.e(e);
            }

            return this;
        }

        /**
         * The same as {@link #applyConfig(Context, int)}, but throws an exception instead of
         * logging an error.
         *
         * @param context The application context.
         * @param xmlResourceId The xml resource ID.
         * @return The config option builder.
         */
        @NonNull
        public Builder tryApplyConfig(@NonNull Context context, @XmlRes int xmlResourceId) throws Exception {
            XmlConfigParser configParser = null;
            try {
                configParser = XmlConfigParser.parseElement(context, xmlResourceId, CONFIG_ELEMENT);
                applyConfigParser(context, configParser);
            } catch (Exception e) {
                throw new ConfigException("Unable to apply config from xml.", e);
            } finally {
                if (configParser != null) {
                    configParser.close();
                }
            }

            return this;
        }

        /**
         * Applies a value to the builder.
         *
         * @param configParser The config parser.
         */
        private void applyConfigParser(Context context, ConfigParser configParser) {
            for (int i = 0; i < configParser.getCount(); i++) {
                try {
                    String name = configParser.getName(i);
                    if (name == null) {
                        continue;
                    }
                    switch (name) {
                        case FIELD_APP_KEY:
                            this.setAppKey(configParser.getString(name));
                            break;

                        case FIELD_APP_SECRET:
                            this.setAppSecret(configParser.getString(name));
                            break;

                        case FIELD_PRODUCTION_APP_KEY:
                            this.setProductionAppKey(configParser.getString(name));
                            break;

                        case FIELD_PRODUCTION_APP_SECRET:
                            this.setProductionAppSecret(configParser.getString(name));
                            break;

                        case FIELD_DEVELOPMENT_APP_KEY:
                            this.setDevelopmentAppKey(configParser.getString(name));
                            break;

                        case FIELD_DEVELOPMENT_APP_SECRET:
                            this.setDevelopmentAppSecret(configParser.getString(name));
                            break;

                        case FIELD_LEGACY_DEVICE_URL:
                        case FIELD_DEVICE_URL:
                            this.setDeviceUrl(configParser.getString(name, deviceUrl));
                            break;

                        case FIELD_LEGACY_ANALYTICS_SERVER:
                        case FIELD_ANALYTICS_URL:
                            this.setAnalyticsUrl(configParser.getString(name, analyticsUrl));
                            break;

                        case FIELD_LEGACY_REMOTE_DATA_URL:
                        case FIELD_REMOTE_DATA_URL:
                            this.setRemoteDataUrl(configParser.getString(name, remoteDataUrl));
                            break;

                        case FIELD_INITIAL_CONFIG_URL:
                            this.setInitialConfigUrl(configParser.getString(name, null));
                            break;

                        case FIELD_GCM_SENDER:
                            throw new IllegalArgumentException("gcmSender no longer supported. Please use " +
                                    "fcmSender or remove it to allow the Airship SDK to pull from the google-services.json.");

                        case FIELD_ALLOWED_TRANSPORTS:
                            this.setAllowedTransports(configParser.getStringArray(name));
                            break;

                        /* Deprecated. To be removed in a future version of the SDK. */
                        case "whitelist":
                            UALog.e("Parameter whitelist is deprecated and will be removed in a future version of the SDK. Use urlAllowList instead.");
                            this.setUrlAllowList(configParser.getStringArray(name));
                            break;

                        case FIELD_URL_ALLOW_LIST:
                            this.setUrlAllowList(configParser.getStringArray(name));
                            break;

                        case FIELD_URL_ALLOW_LIST_SCOPE_JAVASCRIPT_INTERFACE:
                            this.setUrlAllowListScopeJavaScriptInterface(configParser.getStringArray(name));
                            break;

                        case FIELD_URL_ALLOW_LIST_SCOPE_OPEN_URL:
                            this.setUrlAllowListScopeOpenUrl(configParser.getStringArray(name));
                            break;

                        case FIELD_IN_PRODUCTION:
                            this.setInProduction(configParser.getBoolean(name, inProduction != null && inProduction));
                            break;

                        case FIELD_ANALYTICS_ENABLED:
                            this.setAnalyticsEnabled(configParser.getBoolean(name, analyticsEnabled));
                            break;

                        case FIELD_BACKGROUND_REPORTING_INTERVAL_MS:
                            this.setBackgroundReportingIntervalMS(configParser.getLong(name, backgroundReportingIntervalMS));
                            break;

                        case FIELD_DEVELOPMENT_LOG_LEVEL:
                            this.setDevelopmentLogLevel(UALog.parseLogLevel(configParser.getString(name), DEFAULT_DEVELOPMENT_LOG_LEVEL));
                            break;

                        case FIELD_PRODUCTION_LOG_LEVEL:
                            this.setProductionLogLevel(UALog.parseLogLevel(configParser.getString(name), DEFAULT_PRODUCTION_LOG_LEVEL));
                            break;

                        case FIELD_LOG_LEVEL:
                            this.setLogLevel(UALog.parseLogLevel(configParser.getString(name), DEFAULT_PRODUCTION_LOG_LEVEL));
                            break;

                        case FIELD_AUTO_LAUNCH_APPLICATION:
                            this.setAutoLaunchApplication(configParser.getBoolean(name, autoLaunchApplication));
                            break;

                        case FIELD_CHANNEL_CREATION_DELAY_ENABLED:
                            this.setChannelCreationDelayEnabled(configParser.getBoolean(name, channelCreationDelayEnabled));
                            break;

                        case FIELD_CHANNEL_CAPTURE_ENABLED:
                            this.setChannelCaptureEnabled(configParser.getBoolean(name, channelCaptureEnabled));
                            break;

                        case FIELD_NOTIFICATION_ICON:
                            this.setNotificationIcon(configParser.getDrawableResourceId(name));
                            break;

                        case FIELD_NOTIFICATION_LARGE_ICON:
                            this.setNotificationLargeIcon(configParser.getDrawableResourceId(name));
                            break;

                        case FIELD_NOTIFICATION_ACCENT_COLOR:
                            this.setNotificationAccentColor(configParser.getColor(name, notificationAccentColor));
                            break;

                        case FIELD_WALLET_URL:
                            this.setWalletUrl(configParser.getString(name, walletUrl));
                            break;

                        case FIELD_NOTIFICATION_CHANNEL:
                            this.setNotificationChannel(configParser.getString(name));
                            break;

                        case FIELD_FCM_SENDER_ID:
                        case FIELD_DEVELOPMENT_FCM_SENDER_ID:
                        case FIELD_PRODUCTION_FCM_SENDER_ID:
                            UALog.e("Support for Sender ID override has been removed. Configure a FirebaseApp and use fcmFirebaseAppName instead.");
                            break;

                        case FIELD_FCM_FIREBASE_APP_NAME:
                            this.setFcmFirebaseAppName(configParser.getString(name));
                            break;

                        case "enableUrlWhitelisting":
                            UALog.e("Parameter enableUrlWhitelisting has been removed. See urlAllowListScopeJavaScriptBridge and urlAllowListScopeOpen instead.");
                            break;

                        case FIELD_CUSTOM_PUSH_PROVIDER:
                            String className = configParser.getString(name);
                            Checks.checkNotNull(className, "Missing custom push provider class name");
                            Class<? extends PushProvider> providerClass = Class.forName(className).asSubclass(PushProvider.class);
                            this.setCustomPushProvider(providerClass.newInstance());
                            break;

                        case FIELD_APP_STORE_URI:
                            this.setAppStoreUri(Uri.parse(configParser.getString(name)));
                            break;

                        case FIELD_SITE:
                            this.setSite(parseSite(configParser.getString(name)));
                            break;

                        case FIELD_DATA_COLLECTION_OPT_IN_ENABLED:
                            this.setDataCollectionOptInEnabled(configParser.getBoolean(name, false));
                            break;

                        case FIELD_EXTENDED_BROADCASTS_ENABLED:
                            this.setExtendedBroadcastsEnabled(configParser.getBoolean(name, false));
                            break;

                        case FIELD_REQUIRE_INITIAL_REMOTE_CONFIG_ENABLED:
                            this.setRequireInitialRemoteConfigEnabled(configParser.getBoolean(name, false));
                            break;

                        case FIELD_IS_PROMPT_FOR_PERMISSION_ON_USER_NOTIFICATIONS_ENABLED:
                            this.setIsPromptForPermissionOnUserNotificationsEnabled(configParser.getBoolean(name, true));
                            break;

                        case FIELD_AUTO_PAUSE_IN_APP_AUTOMATION_ON_LAUNCH:
                            this.setAutoPauseInAppAutomationOnLaunch(configParser.getBoolean(name, false));
                            break;

                        case FIELD_ENABLED_FEATURES:
                            int value = -1;
                            try {
                                value = configParser.getInt(name, -1);
                            } catch (Exception e) {
                                // ignored
                            }

                            if (value != -1) {
                                this.setEnabledFeatures(new PrivacyManager.Feature(value));
                            } else {
                                String[] features = configParser.getStringArray(name);
                                if (features == null) {
                                    throw new IllegalArgumentException("Unable to parse enableFeatures: " + configParser.getString(name));
                                }

                                this.setEnabledFeatures(convertFeatureNames(features));
                            }

                            break;
                        case FIELD_RESET_ENABLED_FEATURES:
                            this.setResetEnabledFeatures(configParser.getBoolean(name, false));
                            break;

                    }
                } catch (Exception e) {
                    UALog.e(e, "Unable to set config field '%s' due to invalid configuration value.", configParser.getName(i));
                }
            }

            // Determine build mode if not specified in config file.
            if (inProduction == null) {
                detectProvisioningMode(context);
            }
        }

        private PrivacyManager.Feature convertFeatureNames(@NonNull String[] features) {
            try {
                return PrivacyManager.Feature.fromJson(JsonValue.wrap(features));
            } catch (Exception ex) {
                UALog.e(ex, "Failed to parse features array " + String.join(",", features));
                return PrivacyManager.Feature.NONE;
            }
        }

        /**
         * Sets the default notification channel.
         * <p>
         * See {@link com.urbanairship.push.notifications.NotificationProvider#onCreateNotificationArguments(Context, PushMessage)}
         *
         * @param channel The notification channel.
         * @return The config options builder.
         */
        @NonNull
        public Builder setNotificationChannel(@Nullable String channel) {
            this.notificationChannel = channel;
            return this;
        }

        /**
         * Sets the default notification Icon.
         * <p>
         * See {@link com.urbanairship.push.notifications.AirshipNotificationProvider#setSmallIcon(int)}.
         *
         * @param notificationIcon The notification icon.
         * @return The config options builder.
         */
        @NonNull
        public Builder setNotificationIcon(@DrawableRes int notificationIcon) {
            this.notificationIcon = notificationIcon;
            return this;
        }

        /**
         * Sets the large notification Icon.
         * <p>
         * See {@link com.urbanairship.push.notifications.AirshipNotificationProvider#setLargeIcon(int)}.
         *
         * @param notificationLargeIcon The large notification icon.
         * @return The config options builder.
         */
        @NonNull
        public Builder setNotificationLargeIcon(@DrawableRes int notificationLargeIcon) {
            this.notificationLargeIcon = notificationLargeIcon;
            return this;
        }

        /**
         * Sets the default notification accent color.
         * <p>
         * See {@link com.urbanairship.push.notifications.AirshipNotificationProvider#setDefaultAccentColor(int)}.
         *
         * @param notificationAccentColor The notification accent color.
         * @return The config options builder.
         */
        @NonNull
        public Builder setNotificationAccentColor(@ColorInt int notificationAccentColor) {
            this.notificationAccentColor = notificationAccentColor;
            return this;
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
        @NonNull
        public Builder setAppKey(@Nullable String appKey) {
            this.appKey = appKey;
            return this;
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
        @NonNull
        public Builder setAppSecret(@Nullable String appSecret) {
            this.appSecret = appSecret;
            return this;
        }

        /**
         * Set the application's production app key.
         *
         * @param productionAppKey The application's production app key.
         * @return The config options builder.
         */
        @NonNull
        public Builder setProductionAppKey(@Nullable String productionAppKey) {
            this.productionAppKey = productionAppKey;
            return this;
        }

        /**
         * Set the application's production app secret.
         *
         * @param productionAppSecret The application's production app secret.
         * @return The config options builder.
         */
        @NonNull
        public Builder setProductionAppSecret(@Nullable String productionAppSecret) {
            this.productionAppSecret = productionAppSecret;
            return this;
        }

        /**
         * Set the application's development app key.
         *
         * @param developmentAppKey The application's development app key.
         * @return The config options builder.
         */
        @NonNull
        public Builder setDevelopmentAppKey(@Nullable String developmentAppKey) {
            this.developmentAppKey = developmentAppKey;
            return this;
        }

        /**
         * Set the application's development app secret.
         *
         * @param developmentAppSecret The application's development app secret.
         * @return The config options builder.
         */
        @NonNull
        public Builder setDevelopmentAppSecret(@Nullable String developmentAppSecret) {
            this.developmentAppSecret = developmentAppSecret;
            return this;
        }

        /**
         * Set the device URL.
         *
         * @param deviceUrl The device URL.
         * @return The config options builder.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder setDeviceUrl(@NonNull String deviceUrl) {
            this.deviceUrl = deviceUrl;
            return this;
        }

        /**
         * Set the analytics server URL.
         *
         * @param analyticsUrl The analytics server URL.
         * @return The config options builder.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder setAnalyticsUrl(@NonNull String analyticsUrl) {
            this.analyticsUrl = analyticsUrl;
            return this;
        }

        /**
         * Set the remote data URL.
         *
         * @param remoteDataUrl The remote data URL.
         * @return The config options builder.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public Builder setRemoteDataUrl(@Nullable String remoteDataUrl) {
            this.remoteDataUrl = remoteDataUrl;
            return this;
        }

        /**
         * The Airship URL used to pull the initial config. This should only be set
         * if you are using custom domains that forward to Airship.
         *
         * @param initialConfigUrl The initial config URL.
         * @return The config options builder.
         */
        @NonNull
        public Builder setInitialConfigUrl(@Nullable String initialConfigUrl) {
            this.initialConfigUrl = initialConfigUrl;
            return this;
        }

        /**
         * Set the transport types allowed for Push.
         *
         * @param allowedTransports The transport types allowed for Push.
         * @return The config options builder.
         */
        @NonNull
        public Builder setAllowedTransports(@Nullable String[] allowedTransports) {
            this.allowedTransports.clear();
            if (allowedTransports != null) {
                this.allowedTransports.addAll(Arrays.asList(allowedTransports));
            }
            return this;
        }

        /**
         * Sets the additional URLs that will be added to the allow list for both {@link UrlAllowList#SCOPE_OPEN_URL} and
         * {@link UrlAllowList#SCOPE_JAVASCRIPT_INTERFACE} scopes.
         * See {@link UrlAllowList#addEntry(String)} for valid URL patterns.
         *
         * @param urlAllowList An array of URL patterns.
         * @return The config options builder.
         */
        @NonNull
        public Builder setUrlAllowList(@Nullable String[] urlAllowList) {
            if (urlAllowList != null) {
                this.urlAllowList = Arrays.asList(urlAllowList);
            } else {
                this.urlAllowList = null;
            }

            this.isAllowListSet = true;
            return this;
        }

        /**
         * Sets the additional URLs that will be added to the allow list for scope {@link UrlAllowList#SCOPE_JAVASCRIPT_INTERFACE}.
         * See {@link UrlAllowList#addEntry(String)} for valid URL patterns.
         *
         * @param urlAllowListScopeJavaScriptInterface An array of URL patterns.
         * @return The config options builder.
         */
        @NonNull
        public Builder setUrlAllowListScopeJavaScriptInterface(@Nullable String[] urlAllowListScopeJavaScriptInterface) {
            if (urlAllowListScopeJavaScriptInterface != null) {
                this.urlAllowListScopeJavaScriptInterface = Arrays.asList(urlAllowListScopeJavaScriptInterface);
            } else {
                this.urlAllowListScopeJavaScriptInterface = null;
            }
            return this;
        }

        /**
         * Sets the additional URLs that will be added to the allow list for scope {@link UrlAllowList#SCOPE_OPEN_URL}.
         * See {@link UrlAllowList#addEntry(String)} for valid URL patterns.
         *
         * @param urlAllowListScopeOpenUrl An array of URL patterns.
         * @return The config options builder.
         */
        @NonNull
        public Builder setUrlAllowListScopeOpenUrl(@Nullable String[] urlAllowListScopeOpenUrl) {
            if (urlAllowListScopeOpenUrl != null) {
                this.urlAllowListScopeOpenUrl = Arrays.asList(urlAllowListScopeOpenUrl);
            } else {
                this.urlAllowListScopeOpenUrl = null;
            }

            this.isAllowListScopeOpenSet = true;
            return this;
        }

        /**
         * Set the flag indicating whether the application is in production or development.
         *
         * @param inProduction The flag indicating whether the application is in production or development.
         * @return The config options builder.
         */
        @NonNull
        public Builder setInProduction(boolean inProduction) {
            this.inProduction = inProduction;
            return this;
        }

        /**
         * Automatically determine the provisioning mode of the application.
         *
         * @param context The application context.
         * @return The config options builder.
         */
        @NonNull
        public Builder detectProvisioningMode(@NonNull Context context) {
            try {
                Class<?> clazz = Class.forName(context.getPackageName() + ".BuildConfig");
                Field field = clazz.getField("DEBUG");
                inProduction = !(boolean) field.get(null);
            } catch (Exception e) {
                UALog.w("AirshipConfigOptions - Unable to determine the build mode. Defaulting to debug.");
                inProduction = false;
            }
            return this;
        }

        /**
         * Set the flag indicating whether the application will use analytics.
         *
         * @param analyticsEnabled The flag indicating whether the application will use analytics.
         * @return The config options builder.
         */
        @NonNull
        public Builder setAnalyticsEnabled(boolean analyticsEnabled) {
            this.analyticsEnabled = analyticsEnabled;
            return this;
        }

        /**
         * Set the background reporting interval.
         *
         * @param backgroundReportingIntervalMS The background reporting interval.
         * @return The config options builder.
         */
        @NonNull
        public Builder setBackgroundReportingIntervalMS(long backgroundReportingIntervalMS) {
            this.backgroundReportingIntervalMS = backgroundReportingIntervalMS;
            return this;
        }

        /**
         * Set the logger level when the application is in debug mode.
         *
         * @param developmentLogLevel The logger level.
         * @return The config options builder.
         */
        @NonNull
        public Builder setDevelopmentLogLevel(int developmentLogLevel) {
            this.developmentLogLevel = developmentLogLevel;
            return this;
        }

        /**
         * Set the logger level when the application is in production mode.
         *
         * @param productionLogLevel The logger level.
         * @return The config options builder.
         */
        @NonNull
        public Builder setProductionLogLevel(int productionLogLevel) {
            this.productionLogLevel = productionLogLevel;
            return this;
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
        @NonNull
        public Builder setLogLevel(int logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        /**
         * Set the flag indicating whether or not to launch the launcher activity when a push notification or push
         * notification button is opened and the application intent receiver did not launch an activity.
         *
         * @param autoLaunchApplication The auto launch flag.
         * @return The config options builder.
         */
        @NonNull
        public Builder setAutoLaunchApplication(boolean autoLaunchApplication) {
            this.autoLaunchApplication = autoLaunchApplication;
            return this;
        }

        /**
         * Set the flag indicating whether channel creation delay is enabled or not.
         *
         * @param channelCreationDelayEnabled The flag indicating whether channel creation delay is enabled or not.
         * @return The config option builder.
         */
        @NonNull
        public Builder setChannelCreationDelayEnabled(boolean channelCreationDelayEnabled) {
            this.channelCreationDelayEnabled = channelCreationDelayEnabled;
            return this;
        }

        /**
         * Set the flag indicating whether channel capture feature is enabled or not.
         *
         * @param channelCaptureEnabled The flag indicating whether channel capture feature is enabled or not.
         * @return The config option builder.
         */
        @NonNull
        public Builder setChannelCaptureEnabled(boolean channelCaptureEnabled) {
            this.channelCaptureEnabled = channelCaptureEnabled;
            return this;
        }

        /**
         * Set the Wallet URL.
         *
         * @param walletUrl The Wallet URL.
         * @return The config options builder.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setWalletUrl(@NonNull String walletUrl) {
            this.walletUrl = walletUrl;
            return this;
        }

        /**
         * Used to set a custom push provider for push registration.
         *
         * @param customPushProvider Push provider.
         * @return The config options builder.
         * @hide
         */
        @NonNull
        public Builder setCustomPushProvider(@Nullable PushProvider customPushProvider) {
            this.customPushProvider = customPushProvider;
            return this;
        }

        /**
         * Sets the app store URI for the rate-app action. If not set,
         * the action will generate it using the app's current package name.
         *
         * <p>
         * Example: "market://details?id=com.example.android"
         *
         * @param appStoreUri The app store URI.
         * @return The config options builder.
         */
        @NonNull
        public Builder setAppStoreUri(@Nullable Uri appStoreUri) {
            this.appStoreUri = appStoreUri;
            return this;
        }

        /**
         * Sets the Airship cloud site for data locality.
         *
         * @param site The airship cloud site.
         * @return The config options builder.
         */
        @NonNull
        public Builder setSite(@NonNull @Site String site) {
            this.site = site;
            return this;
        }

        /**
         * Set the flag indicating whether data collection needs to be opted in with
         * {@link UAirship#setDataCollectionEnabled(boolean)}.
         *
         * This flag will only take affect on first run. If previously not enabled, the device
         * will still have data collection enabled until disabled with {@link UAirship#setDataCollectionEnabled(boolean)}.
         *
         * @param dataCollectionOptInEnabled The flag indicating whether data collection needs to be opted in.
         * @return The config options builder.
         * @deprecated Use {@link #enabledFeatures} instead.
         */
        @NonNull
        @Deprecated
        public Builder setDataCollectionOptInEnabled(boolean dataCollectionOptInEnabled) {
            this.dataCollectionOptInEnabled = dataCollectionOptInEnabled;
            return this;
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
        @NonNull
        public Builder setExtendedBroadcastsEnabled(boolean extendedBroadcastsEnabled) {
            this.extendedBroadcastsEnabled = extendedBroadcastsEnabled;
            return this;
        }

        /**
         * Sets the default enabled SDK features. See {@link PrivacyManager} for more info.
         *
         * @param enabledFeatures The enabled features.
         * @return The config options builder.
         */
        @NonNull
        public Builder setEnabledFeatures(PrivacyManager.Feature... enabledFeatures) {
            this.enabledFeatures = PrivacyManager.Feature.combined(enabledFeatures);
            return this;
        }

        /**
         * Sets the Firebase app name that is used for FCM. If set, the app name must exist in order
         * for Airship to get registration token. The app should be initialized with Firebase before takeOff, or during
         * onAirshipReady callback.
         *
         * @param fcmFirebaseAppName The firebase app name.
         * @return The config options builder.
         */
        @NonNull
        public Builder setFcmFirebaseAppName(@Nullable String fcmFirebaseAppName) {
            this.fcmFirebaseAppName = fcmFirebaseAppName;
            return this;
        }

        /**
         * Sets the flag to require initial remote-config for device URLs.
         *
         * @param requireInitialRemoteConfigEnabled {@code true} to require initial remote-config, otherwise {@code false}.
         * @return The config options builder.
         */
        @NonNull
        public Builder setRequireInitialRemoteConfigEnabled(boolean requireInitialRemoteConfigEnabled) {
            this.requireInitialRemoteConfigEnabled = requireInitialRemoteConfigEnabled;
            return this;
        }

        /**
         * Sets if when enabling {@link com.urbanairship.push.PushManager#setUserNotificationsEnabled(boolean)}
         * if the SDK should prompt for permission on Android 13+ devices. Enabled by default.
         *
         * @param enabled {@code true} to prompt for notifications when user notifications are enabled, otherwise {@code false}.
         * @return The config options builder.
         */
        @NonNull
        public Builder setIsPromptForPermissionOnUserNotificationsEnabled(boolean enabled) {
            this.isPromptForPermissionOnUserNotificationsEnabled = enabled;
            return this;
        }


        /**
         * Set the auto pause In-App Automation on launch.
         *
         * @param autoPauseInAppAutomationOnLaunch {@code true} to auto pause In-App Automation, otherwise {@code false}.
         * @return The config options builder.
         */
        @NonNull
        public Builder setAutoPauseInAppAutomationOnLaunch(boolean autoPauseInAppAutomationOnLaunch) {
            this.autoPauseInAppAutomationOnLaunch = autoPauseInAppAutomationOnLaunch;
            return this;
        }

        /**
         * Set the reset enabled features option.
         *
         * @param resetEnabledFeatures {@code true} to reset enabled features on launch, otherwise {@code false}.
         * @return The config options builder.
         */
        @NonNull
        public Builder setResetEnabledFeatures(boolean resetEnabledFeatures) {
            this.resetEnabledFeatures = resetEnabledFeatures;
            return this;
        }

        /**
         * Builds the config options.
         *
         * @return The built config options.
         */
        @NonNull
        public AirshipConfigOptions build() {
            if (inProduction == null) {
                inProduction = false;
            }

            if (productionAppKey != null && productionAppKey.equals(developmentAppKey)) {
                UALog.w("Production App Key matches Development App Key");
            }

            if (productionAppSecret != null && productionAppSecret.equals(developmentAppSecret)) {
                UALog.w("Production App Secret matches Development App Secret");
            }

            if (dataCollectionOptInEnabled) {
                UALog.w("dataCollectionOptInEnabled is deprecated. Use enabledFeatures instead.");
                if (enabledFeatures == PrivacyManager.Feature.ALL) {
                    enabledFeatures = PrivacyManager.Feature.NONE;
                }
            }

            return new AirshipConfigOptions(this);
        }

    }

}
