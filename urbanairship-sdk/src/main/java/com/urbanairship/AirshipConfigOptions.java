/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.util.Log;

import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Properties;


/**
 * This class holds the set of options necessary to properly initialize
 * {@link com.urbanairship.UAirship}.
 *
 * @author Urban Airship
 */
public class AirshipConfigOptions {

    private static final int DEFAULT_PRODUCTION_LOG_LEVEL = Log.ERROR;
    private static final int DEFAULT_DEVELOPMENT_LOG_LEVEL = Log.DEBUG;

    /**
     * The ADM transport type for Push.
     */
    public static final String ADM_TRANSPORT = "ADM";

    /**
     * The GCM transport type for Push.
     */
    public static final String GCM_TRANSPORT = "GCM";

    // Logs a warning message if the backgroundReportingIntervalSeconds is below this minimum value
    private final static int MIN_BG_REPORTING_INTERVAL_MS = 60 * 1000; // 1 minute

    // Logs a warning message if the backgroundReportingIntervalSeconds is above this maximum value
    private final static int MAX_BG_REPORTING_INTERVAL_MS = 24 * 60 * 60 * 1000; // 24 hours

    // Default airship config properties filename
    private final static String DEFAULT_PROPERTIES_FILENAME = "airshipconfig.properties";

    /**
     * The application's production app key.
     * <p/>
     * This string is generated automatically when you create an app in the Urban Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    public final String productionAppKey;

    /**
     * The application's production app secret.
     * <p/>
     * This string is generated automatically when you create an app in the Urban Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    public final String productionAppSecret;

    /**
     * The application's development app key.
     * <p/>
     * This string is generated automatically when you create an app in the Urban Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    public final String developmentAppKey;

    /**
     * The application's development app secret.
     * <p/>
     * This string is generated automatically when you create an app in the Urban Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    public final String developmentAppSecret;

    /**
     * The Urban Airship URL. This will always be set to http://device-api.urbanairship.com/
     */
    public final String hostURL;

    /**
     * The Analytics Server. This will always be set to https://combine.urbanairship.com/
     */
    public final String analyticsServer;

    /**
     * The landing page content URL. This will always be set to https://dl.urbanairship.com/aaa/
     */
    public final String landingPageContentURL;

    /**
     * The sender ID used to send GCM pushes. This is your Google API project number.
     */
    public final String gcmSender;

    /**
     * The transport types allowed for Push.
     * <p/>
     * Defaults to ADM, GCM.
     */
    public final String[] allowedTransports;


    /**
     * List of additional url patterns that will be allowed access to the Urban Airship Javascript
     * Interface (Urban Airship https URLs are included by default). See {@link com.urbanairship.js.Whitelist#addEntry(String)}
     * for valid url patterns.
     * <p/>
     * Defaults null.
     */
    public final String[] whitelist;

    /**
     * Flag indicating whether the application is in production or development.
     * <p/>
     * Defaults to <code>false</code>.
     */
    public final boolean inProduction;

    /**
     * Flag indicating whether the application will use analytics.
     * <p/>
     * The flag defaults to true.
     */
    public final boolean analyticsEnabled;

    /**
     * Minimum delta in milliseconds between analytics uploads when
     * adding location events while in the background.
     * <p/>
     * Defaults to 15 minutes.
     */
    public final long backgroundReportingIntervalMS;

    /**
     * Flag indicating whether to clear an existing named user during a re-install.
     * <p/>
     * Defaults to <code>false</code>.
     */
    public final boolean clearNamedUser;

    /**
     * Logger level when the application is in debug mode. Possible values are:
     * <br><ul>
     * <li>ASSERT
     * <li>DEBUG
     * <li>ERROR
     * <li>INFO
     * <li>VERBOSE
     * <li>WARN
     * </ul><br>
     * Defaults to <code>DEBUG</code>
     */
    public final int developmentLogLevel;

    /**
     * Logger level when the application is in production mode. Possible values are:
     * <br><ul>
     * <li>ASSERT
     * <li>DEBUG
     * <li>ERROR
     * <li>INFO
     * <li>VERBOSE
     * <li>WARN
     * </ul><br>
     * Defaults to <code>ERROR</code>
     */
    public final int productionLogLevel;

    /**
     * Flag indicating whether or not to launch the launcher activity when a push notification or push
     * notification button is opened and the application intent receiver did not launch an activity.
     * <p/>
     * Defaults to true.
     */
    public final boolean autoLaunchApplication;

    /**
     * Flag indicating whether channel creation delay is enabled or not.
     * <p/>
     * The flag defaults to false.
     */
    public final boolean channelCreationDelayEnabled;

    /**
     * Flag indicating whether channel capture feature is enabled or not.
     * <p/>
     * The flag defaults to true.
     */
    public final boolean channelCaptureEnabled;

    private AirshipConfigOptions(Builder builder) {
        this.productionAppKey = builder.productionAppKey;
        this.productionAppSecret = builder.productionAppSecret;
        this.developmentAppKey = builder.developmentAppKey;
        this.developmentAppSecret = builder.developmentAppSecret;
        this.hostURL = builder.hostURL;
        this.analyticsServer = builder.analyticsServer;
        this.landingPageContentURL = builder.landingPageContentURL;
        this.gcmSender = builder.gcmSender;
        this.allowedTransports = builder.allowedTransports;
        this.whitelist = builder.whitelist;
        this.inProduction = builder.inProduction;
        this.analyticsEnabled = builder.analyticsEnabled;
        this.backgroundReportingIntervalMS = builder.backgroundReportingIntervalMS;
        this.clearNamedUser = builder.clearNamedUser;
        this.developmentLogLevel = builder.developmentLogLevel;
        this.productionLogLevel = builder.productionLogLevel;
        this.autoLaunchApplication = builder.autoLaunchApplication;
        this.channelCreationDelayEnabled = builder.channelCreationDelayEnabled;
        this.channelCaptureEnabled = builder.channelCaptureEnabled;
    }

    /**
     * Returns the appropriate development or production app key
     *
     * @return The application key
     */
    public String getAppKey() {
        return inProduction ? productionAppKey : developmentAppKey;
    }

    /**
     * Returns the appropriate development or production app secret
     *
     * @return The application secret
     */
    public String getAppSecret() {
        return inProduction ? productionAppSecret : developmentAppSecret;
    }

    /**
     * Returns the appropriate development or production log level.
     *
     * @return The log level
     */
    public int getLoggerLevel() {
        return inProduction ? productionLogLevel : developmentLogLevel;
    }


    /**
     * Check to see if the specified transport type is allowed.
     *
     * @param transport The transport type.
     * @return <code>true</code> if the transport type is allowed, otherwise <code>false</code>.
     */
    public boolean isTransportAllowed(String transport) {
        if (allowedTransports == null || transport == null) {
            return false;
        }

        for (String allowedTransport : allowedTransports) {
            if (transport.equalsIgnoreCase(allowedTransport)) {
                return true;
            }
        }

        return false;
    }

    public static final class Builder {

        @PropertyName(name = "productionAppKey")
        private String productionAppKey;

        @PropertyName(name = "productionAppSecret")
        private String productionAppSecret;

        @PropertyName(name = "developmentAppKey")
        private String developmentAppKey;

        @PropertyName(name = "developmentAppSecret")
        private String developmentAppSecret;

        @Size(min=1)
        @PropertyName(name = "hostURL")
        private String hostURL = "https://device-api.urbanairship.com/";

        @PropertyName(name = "analyticsServer")
        private String analyticsServer = "https://combine.urbanairship.com/";

        @PropertyName(name = "landingPageContentURL")
        private String landingPageContentURL = "https://dl.urbanairship.com/aaa/";

        @PropertyName(name = "gcmSender")
        private String gcmSender;

        @PropertyName(name = "allowedTransports")
        private String[] allowedTransports = new String[] { ADM_TRANSPORT, GCM_TRANSPORT };

        @PropertyName(name = "whitelist")
        private String[] whitelist = null;

        @PropertyName(name = "inProduction")
        private boolean inProduction = false;

        @PropertyName(name = "analyticsEnabled")
        private boolean analyticsEnabled = true;

        @PropertyName(name = "backgroundReportingIntervalMS")
        private long backgroundReportingIntervalMS = 15 * 60 * 1000;

        @PropertyName(name = "clearNamedUser")
        private boolean clearNamedUser = false;

        @PropertyName(name = "developmentLogLevel")
        @ConstantClass(name = "android.util.Log")
        private int developmentLogLevel = DEFAULT_DEVELOPMENT_LOG_LEVEL;

        @PropertyName(name = "productionLogLevel")
        @ConstantClass(name = "android.util.Log")
        private int productionLogLevel = DEFAULT_PRODUCTION_LOG_LEVEL;

        @PropertyName(name = "autoLaunchApplication")
        private boolean autoLaunchApplication = true;

        @PropertyName(name = "channelCreationDelayEnabled")
        private boolean channelCreationDelayEnabled = false;

        @PropertyName(name = "channelCaptureEnabled")
        private boolean channelCaptureEnabled = true;

        /**
         * Apply the options from the default properties file
         *
         * @param ctx The application context
         * @return The config option builder.
         */
        public Builder applyDefaultProperties(@NonNull Context ctx) {
            this.applyProperties(ctx, DEFAULT_PROPERTIES_FILENAME);
            return this;
        }


        /**
         * Apply the options from a given properties file
         *
         * @param ctx The application context
         * @param propertiesFile The properties file
         * @return The config option builder.
         */
        public Builder applyProperties(@NonNull Context ctx, @NonNull String propertiesFile) {
            Resources resources = ctx.getResources();
            AssetManager assetManager = resources.getAssets();

            //bail if the properties file can't be found
            try {
                if (!Arrays.asList(assetManager.list("")).contains(propertiesFile)) {
                    Logger.verbose("AirshipConfigOptions - Couldn't find " + propertiesFile);
                    return this;
                }
            } catch (IOException e) {
                Logger.error(e);
                return this;
            }

            Properties properties = new Properties();
            InputStream inStream = null;

            try {
                inStream = assetManager.open(propertiesFile);
                properties.load(inStream);

                Class<?> theClass = this.getClass();

                for (Field field : theClass.getDeclaredFields()) {
                    // If it's a nested Options class, skip it
                    if (AirshipConfigOptions.Builder.class.isAssignableFrom(field.getType())) {
                        continue;
                    }

                    String propertyValue = getPropertyValue(field, properties);

                    if (propertyValue != null) {
                        setPropertyValue(field, propertyValue);
                    }
                }

            } catch (IOException ioe) {
                Logger.error("AirshipConfigOptions - Unable to load properties file " + propertiesFile, ioe);
            } finally {
                if (inStream != null) {
                    try {
                        inStream.close();
                    } catch (IOException e) {
                        Logger.error("AirshipConfigOptions - Failed to close input stream.", e);
                    }
                }
            }

            if (inProduction) {
                if (!isLogLevelValid(productionLogLevel)) {
                    Logger.error(productionLogLevel + " is not a valid log level. Falling back to " + DEFAULT_PRODUCTION_LOG_LEVEL + " ERROR.");
                    productionLogLevel = DEFAULT_PRODUCTION_LOG_LEVEL;
                }
            } else {
                if (!isLogLevelValid(developmentLogLevel)) {
                    Logger.error(developmentLogLevel + " is not a valid log level. Falling back to " + DEFAULT_DEVELOPMENT_LOG_LEVEL + " DEBUG.");
                    developmentLogLevel = DEFAULT_DEVELOPMENT_LOG_LEVEL;
                }
            }
            return this;
        }

        private boolean isLogLevelValid(int logType) {
            switch (logType) {
                case Log.ASSERT:
                case Log.DEBUG:
                case Log.ERROR:
                case Log.INFO:
                case Log.VERBOSE:
                case Log.WARN:
                    return true;
                default:
                    return false;
            }
        }

        /**
         * Gets the string value of the field
         *
         * @param field field
         * @param properties properties
         * @return the current value of the property
         */
        private String getPropertyValue(@NonNull Field field, @NonNull Properties properties) {
            String propertyValue = null;
            PropertyName propertyAnnotation = field.getAnnotation(PropertyName.class);

            if (propertyAnnotation != null) {
                propertyValue = properties.getProperty(propertyAnnotation.name());
                Logger.verbose("AirshipConfigOptions - Found PropertyAnnotation for " + propertyAnnotation.name() + " matching " + field.getName());
            }

            return propertyValue;
        }

        /**
         * Sets the field value with the parsed version of propertyValue
         *
         * @param field field
         * @param propertyValue propertyValue
         */
        private void setPropertyValue(@NonNull Field field, @NonNull String propertyValue) {
            try {
                // Parse as boolean if expected
                if (field.getType() == Boolean.TYPE || field.getType() == Boolean.class) {
                    field.set(this, Boolean.valueOf(propertyValue));//set will auto-unbox and the value will not be null
                } else if (field.getType() == Integer.TYPE || field.getType() == Integer.class) {
                    int refValue = parseOptionValues(field, propertyValue);
                    field.set(this, refValue);
                } else if (field.getType() == Long.TYPE || field.getType() == Long.class) {
                    field.set(this, Long.valueOf(propertyValue));
                } else if (field.getType().isArray()) {
                    field.set(this, propertyValue.split("[, ]+"));
                } else {
                    field.set(this, propertyValue.trim());
                }
            } catch (IllegalAccessException e) {
                Logger.error("AirshipConfigOptions - Unable to set field '" + field.getName() + "' because the field is not visible.", e);
            } catch (IllegalArgumentException | ClassNotFoundException e) {
                Logger.error("AirshipConfigOptions - Unable to set field '" + field.getName() + "' due to invalid configuration value.", e);
            }
        }

        /**
         * Parses values provided by a .properties file
         *
         * @param field field
         * @param value value
         * @return the field value to be set
         * @throws ClassNotFoundException
         * @throws IllegalAccessException
         * @throws IllegalArgumentException
         */
        private int parseOptionValues(@NonNull Field field, @NonNull String value) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException {
            // Accept the integer value in the properties file for backwards compatibility
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException nfe) {
                ConstantClass classAnnotation = field.getAnnotation(ConstantClass.class);

                if (classAnnotation == null) {
                    throw new IllegalArgumentException("The field '" + field.getName() + "' has a type mismatch or missing annotation.");
                }

                Class<?> constantClass = Class.forName(classAnnotation.name());

                for (Field referenceField : constantClass.getDeclaredFields()) {
                    if (referenceField.getName().equalsIgnoreCase(value)) {
                        try {
                            return referenceField.getInt(this);
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("The field '" + field.getName() + "' is incompatible with specified class", e);
                        }
                    }
                }

                throw new IllegalArgumentException("Unable to match class for field '" + field.getName() + "'");
            }
        }

        /**
         * Set the application's production app key.
         *
         * @param productionAppKey The application's production app key.
         * @return The config options builder.
         */
        public Builder setProductionAppKey(String productionAppKey) {
            this.productionAppKey = productionAppKey;
            return this;
        }

        /**
         * Set the application's production app secret.
         *
         * @param productionAppSecret The application's production app secret.
         * @return The config options builder.
         */
        public Builder setProductionAppSecret(String productionAppSecret) {
            this.productionAppSecret = productionAppSecret;
            return this;
        }

        /**
         * Set the application's development app key.
         *
         * @param developmentAppKey The application's development app key.
         * @return The config options builder.
         */
        public Builder setDevelopmentAppKey(String developmentAppKey) {
            this.developmentAppKey = developmentAppKey;
            return this;
        }

        /**
         * Set the application's development app secret.
         *
         * @param developmentAppSecret The application's development app secret.
         * @return The config options builder.
         */
        public Builder setDevelopmentAppSecret(String developmentAppSecret) {
            this.developmentAppSecret = developmentAppSecret;
            return this;
        }

        /**
         * Set the Urban Airship URL.
         *
         * @param hostURL The Urban Airship URL.
         * @return The config options builder.
         */
        public Builder setHostURL(String hostURL) {
            this.hostURL = hostURL;
            return this;
        }

        /**
         * Set the analytics server URL.
         *
         * @param analyticsServer The analytics server URL.
         * @return The config options builder.
         */
        public Builder setAnalyticsServer(String analyticsServer) {
            this.analyticsServer = analyticsServer;
            return this;
        }

        /**
         * Set the landing page content URL.
         *
         * @param landingPageContentURL The landing page content URL.
         * @return The config options builder.
         */
        public Builder setLandingPageContentURL(String landingPageContentURL) {
            this.landingPageContentURL = landingPageContentURL;
            return this;
        }

        /**
         * Set the sender ID used to send GCM pushes.
         *
         * @param gcmSender The sender ID used to send GCM pushes.
         * @return The config options builder.
         */
        public Builder setGcmSender(String gcmSender) {
            this.gcmSender = gcmSender;
            return this;
        }

        /**
         * Set the transport types allowed for Push.
         *
         * @param allowedTransports The transport types allowed for Push.
         * @return The config options builder.
         */
        public Builder setAllowedTransports(String[] allowedTransports) {
            this.allowedTransports = allowedTransports;
            return this;
        }

        /**
         * Set the list of additional url patterns that will be allowed access to the Urban Airship Javascript
         * Interface (Urban Airship https URLs are included by default). See {@link com.urbanairship.js.Whitelist#addEntry(String)}
         * for valid url patterns.
         *
         * @param whitelist The whitelist.
         * @return The config options builder.
         */
        public Builder setWhitelist(String[] whitelist) {
            this.whitelist = whitelist;
            return this;
        }

        /**
         * Set the flag indicating whether the application is in production or development.
         *
         * @param inProduction The flag indicating whether the application is in production or development.
         * @return The config options builder.
         */
        public Builder setInProduction(boolean inProduction) {
            this.inProduction = inProduction;
            return this;
        }

        /**
         * Set the flag indicating whether the application will use analytics.
         *
         * @param analyticsEnabled The flag indicating whether the application will use analytics.
         * @return The config options builder.
         */
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
        public Builder setBackgroundReportingIntervalMS(long backgroundReportingIntervalMS) {
            this.backgroundReportingIntervalMS = backgroundReportingIntervalMS;
            return this;
        }

        /**
         * Set the flag whether to clear an existing named user during a re-install.
         *
         * @param clearNamedUser The flag whether to clear an existing named user during a re-install.
         * @return The config options builder.
         */
        public Builder setClearNamedUser(boolean clearNamedUser) {
            this.clearNamedUser = clearNamedUser;
            return this;
        }

        /**
         * Set the logger level when the application is in debug mode.
         *
         * @param developmentLogLevel The logger level.
         * @return The config options builder.
         */
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
        public Builder setProductionLogLevel(int productionLogLevel) {
            this.productionLogLevel = productionLogLevel;
            return this;
        }

        /**
         * Set the flag indicating whether or not to launch the launcher activity when a push notification or push
         * notification button is opened and the application intent receiver did not launch an activity.
         *
         * @param autoLaunchApplication The auto launch flag.
         * @return The config options builder.
         */
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
        public Builder setChannelCaptureEnabled(boolean channelCaptureEnabled) {
            this.channelCaptureEnabled = channelCaptureEnabled;
            return this;
        }

        /**
         * Builds the config options. Will fail if any of the following preconditions are not met.
         *
         * <pre>
         * 1. If inProduction is <code>false</code>, development app key and secret must be set.
         * 2. If inProduction is <code>true</code>, production app key and secret must be set.
         * 3. The analytics URI must not be empty if analytics are enabled.
         * 4. The host URL must not be empty.
         * </pre>
         *
         *
         * @return The built config options.
         */
        public AirshipConfigOptions build() {
            String modeString = inProduction ? "production" : "development";

            String appKey = inProduction ? productionAppKey : developmentAppKey;
            if (appKey == null || appKey.length() == 0 || appKey.indexOf(' ') > 0) {
                throw new IllegalArgumentException("AirshipConfigOptions: " + appKey + " is not a valid " + modeString + " app key");
            }

            String appSecret = inProduction ? productionAppSecret : developmentAppSecret;
            if (appSecret == null || appSecret.length() == 0 || appSecret.indexOf(' ') > 0) {
                throw new IllegalArgumentException("AirshipConfigOptions: " + appSecret + " is not a valid " + modeString + " app secret");
            }

            if (analyticsEnabled && UAStringUtil.isEmpty(analyticsServer)) {
                throw new IllegalArgumentException("Invalid config - analyticsServer is empty or null.");
            }

            if (UAStringUtil.isEmpty(hostURL)) {
                throw new IllegalArgumentException("Invalid config - hostURL is empty or null.");
            }

            if (backgroundReportingIntervalMS < MIN_BG_REPORTING_INTERVAL_MS) {
                Logger.warn("AirshipConfigOptions - The backgroundReportingIntervalMS " + backgroundReportingIntervalMS + " may decrease battery life.");
            } else if (backgroundReportingIntervalMS > MAX_BG_REPORTING_INTERVAL_MS) {
                Logger.warn("AirshipConfigOptions - The backgroundReportingIntervalMS " + backgroundReportingIntervalMS + " may provide less detailed analytic reports.");
            }

            if (productionAppKey != null && productionAppKey.equals(developmentAppKey)) {
                Logger.warn("Production App Key matches Development App Key");
            }

            if (productionAppSecret != null && productionAppSecret.equals(developmentAppSecret)) {
                Logger.warn("Production App Secret matches Development App Secret");
            }

            return new AirshipConfigOptions(this);
        }
    }

}
