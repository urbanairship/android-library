/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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
import android.util.Log;

import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;


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
    @PropertyName(name = "productionAppKey")
    public String productionAppKey;

    /**
     * The application's production app secret.
     * <p/>
     * This string is generated automatically when you create an app in the Urban Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    @PropertyName(name = "productionAppSecret")
    public String productionAppSecret;

    /**
     * The application's development app key.
     * <p/>
     * This string is generated automatically when you create an app in the Urban Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    @PropertyName(name = "developmentAppKey")
    public String developmentAppKey;

    /**
     * The application's development app secret.
     * <p/>
     * This string is generated automatically when you create an app in the Urban Airship
     * dashboard, which you can manually copy into your app configuration.
     */
    @PropertyName(name = "developmentAppSecret")
    public String developmentAppSecret;

    /**
     * The Urban Airship URL. This will always be set to http://device-api.urbanairship.com/
     */
    @PropertyName(name = "hostURL")
    public String hostURL = "https://device-api.urbanairship.com/";

    /**
     * The Analytics Server. This will always be set to https://combine.urbanairship.com/
     */
    @PropertyName(name = "analyticsServer")
    public String analyticsServer = "https://combine.urbanairship.com/";

    /**
     * The landing page content URL. This will always be set to https://dl.urbanairship.com/aaa/
     */
    @PropertyName(name = "landingPageContentURL")
    public String landingPageContentURL = "https://dl.urbanairship.com/aaa/";

    /**
     * The sender ID used to send GCM pushes. This is your Google API project number.
     */
    @PropertyName(name = "gcmSender")
    public String gcmSender;

    /**
     * Additional sender IDs to register with GCM. Only messages sent from the sender {@link #gcmSender} will
     * be handled by Urban Airship.
     */
    @PropertyName(name = "additionalGCMSenderIds")
    public String[] additionalGCMSenderIds;

    /**
     * The transport types allowed for Push.
     * <p/>
     * Defaults to ADM, GCM.
     */
    @PropertyName(name = "allowedTransports")
    public String[] allowedTransports = new String[] { ADM_TRANSPORT, GCM_TRANSPORT };


    /**
     * List of additional url patterns that will be allowed access to the Urban Airship Javascript
     * Interface (Urban Airship https URLs are included by default). See {@link com.urbanairship.js.Whitelist#addEntry(String)}
     * for valid url patterns.
     * <p/>
     * Defaults null.
     */
    @PropertyName(name = "whitelist")
    public String[] whitelist = null;

    /**
     * Flag indicating whether the application is in production or development.
     * <p/>
     * Defaults to <code>false</code>.
     */
    @PropertyName(name = "inProduction")
    public boolean inProduction = false;

    /**
     * Flag indicating whether the application will use analytics.
     * <p/>
     * The flag defaults to true.
     */
    @PropertyName(name = "analyticsEnabled")
    public boolean analyticsEnabled = true;

    /**
     * Minimum delta in milliseconds between analytics uploads when
     * adding location events while in the background.
     * <p/>
     * Defaults to 15 minutes.
     */
    @PropertyName(name = "backgroundReportingIntervalMS")
    public long backgroundReportingIntervalMS = 15 * 60 * 1000;

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
    @PropertyName(name = "developmentLogLevel")
    @ConstantClass(name = "android.util.Log")
    public int developmentLogLevel = DEFAULT_DEVELOPMENT_LOG_LEVEL;

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
    @PropertyName(name = "productionLogLevel")
    @ConstantClass(name = "android.util.Log")
    public int productionLogLevel = DEFAULT_PRODUCTION_LOG_LEVEL;

    /**
     * The minSdkVersion is the minimum Android API Level required for the application to run.
     * Defaults to <code>4 (DONUT)</code>
     */
    @PropertyName(name = "minSdkVersion")
    @ConstantClass(name = "android.os.Build.VERSION_CODES")
    public int minSdkVersion = 4;

    /**
     * Flag indicating whether or not to launch the launcher activity when a push notification or push
     * notification button is opened and the application intent receiver did not launch an activity.
     * <p/>
     * Defaults to true.
     */
    @PropertyName(name = "autoLaunchApplication")
    public boolean autoLaunchApplication = true;

    /**
     * Convenience method for loading the default options from a properties
     * file
     *
     * @param ctx The application context
     * @return Options populated with the contents of getDefaultPropertiesFilename()
     */
    public static AirshipConfigOptions loadDefaultOptions(Context ctx) {

        // Use a static initialization method
        // Loading a properties file using reflection in the constructor
        // is not reliable

        AirshipConfigOptions options = new AirshipConfigOptions();
        options.loadFromProperties(ctx);

        return options;
    }

    /**
     * Load the options from the default properties file
     *
     * @param ctx The application context
     */
    public void loadFromProperties(Context ctx) {
        this.loadFromProperties(ctx, DEFAULT_PROPERTIES_FILENAME);
    }

    /**
     * Load the options from a given properties file
     *
     * @param ctx The application context
     * @param propertiesFile The properties file
     */
    public void loadFromProperties(Context ctx, String propertiesFile) {
        Resources resources = ctx.getResources();
        AssetManager assetManager = resources.getAssets();

        //bail if the properties file can't be found
        try {
            if (!Arrays.asList(assetManager.list("")).contains(propertiesFile)) {
                Logger.verbose("AirshipConfigOptions - Couldn't find " + propertiesFile);
                return;
            }
        } catch (IOException e) {
            Logger.error(e);
            return;
        }

        Properties properties = new Properties();

        try {
            InputStream inStream = assetManager.open(propertiesFile);
            properties.load(inStream);

            Class<?> theClass = this.getClass();

            for (Field field : theClass.getDeclaredFields()) {
                // If it's a nested Options class, skip it
                if (AirshipConfigOptions.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                String propertyValue = getPropertyValue(field, properties);

                if (propertyValue != null) {
                    setPropertyValue(field, propertyValue);
                }
            }

        } catch (IOException ioe) {
            Logger.error("AirshipConfigOptions - Unable to load properties file " + propertiesFile, ioe);
        }
    }

    /**
     * Gets the string value of the field
     *
     * @param field field
     * @param properties properties
     * @return the current value of the property
     */
    private String getPropertyValue(Field field, Properties properties) {
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
    private void setPropertyValue(Field field, String propertyValue) {
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
    int parseOptionValues(Field field, String value) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException {
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

    /* (non-Javadoc)
     * @see com.urbanairship.Options#isValid()
     */
    public boolean isValid() {
        boolean valid = true;
        String modeString = inProduction ? "production" : "development";

        if (getAppKey() == null || getAppKey().length() == 0 || getAppKey().indexOf(' ') > 0) {
            Logger.error("AirshipConfigOptions: " + getAppKey() + " is not a valid " + modeString + " app key");
            valid = false;
        }

        if (getAppSecret() == null || getAppSecret().length() == 0 || getAppSecret().indexOf(' ') > 0) {
            Logger.error("AirshipConfigOptions: " + getAppSecret() + " is not a valid " + modeString + " app secret");
            valid = false;
        }

        // If invalid, test for obfuscation and alert the developer
        if (!valid) {
            try {
                // Test for any proguard obfuscated by checking any mutable public field.
                Field mutableField = null;
                for (Field field : getClass().getFields()) {
                    if ((field.getModifiers() & Modifier.FINAL) == 0) {
                        mutableField = field;
                        break;
                    }
                }

                if (mutableField != null && !mutableField.isAnnotationPresent(PropertyName.class)) {
                    Logger.error("AirshipConfigOptions - The public field '" + mutableField.getName() + "' is missing an annotation");
                    // This class has been modified, so alert the developer and provide proguard guidance
                    Logger.error("AirshipConfigOptions appears to be obfuscated. If using Proguard, add the following to your proguard.cfg: \n" +
                            "\t-keepattributes *Annotation*");
                }

            } catch (SecurityException ignored) {
                //do nothing
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

        if (backgroundReportingIntervalMS < MIN_BG_REPORTING_INTERVAL_MS) {
            Logger.warn("AirshipConfigOptions - The backgroundReportingIntervalMS " + backgroundReportingIntervalMS + " may decrease battery life.");
        } else if (backgroundReportingIntervalMS > MAX_BG_REPORTING_INTERVAL_MS) {
            Logger.warn("AirshipConfigOptions - The backgroundReportingIntervalMS " + backgroundReportingIntervalMS + " may provide less detailed analytic reports.");
        }

        return valid;
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

    /**
     * Helper method to get the set of GCM sender Ids.
     *
     * @return The set of sender ids.
     */
    public Set<String> getGCMSenderIds() {
        Set<String> senderIds = new HashSet<>();

        if (!UAStringUtil.isEmpty(gcmSender)) {
            senderIds.add(gcmSender);
        }

        if (additionalGCMSenderIds != null) {
            senderIds.addAll(Arrays.asList(additionalGCMSenderIds));
        }

        return senderIds;
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
}
