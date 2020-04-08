/* Copyright Airship and Contributors */

package com.urbanairship.modules;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.modules.accengage.AccengageModuleLoader;
import com.urbanairship.modules.accengage.AccengageModuleLoaderFactory;
import com.urbanairship.modules.automation.AutomationModuleLoaderFactory;
import com.urbanairship.modules.location.LocationModuleLoader;
import com.urbanairship.modules.location.LocationModuleLoaderFactory;
import com.urbanairship.modules.messagecenter.MessageCenterModuleLoaderFactory;
import com.urbanairship.push.PushManager;
import com.urbanairship.remotedata.RemoteData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Creates module loaders used by {@link com.urbanairship.UAirship}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ModuleLoaders {

    private static final String ACCENGAGE_MODULE_LOADER_FACTORY = "com.urbanairship.accengage.AccengageModuleLoaderFactoryImpl";
    private static final String MESSAGE_CENTER_MODULE_LOADER_FACTORY = "com.urbanairship.messagecenter.MessageCenterModuleLoaderFactoryImpl";
    private static final String LOCATION_MODULE_LOADER_FACTORY = "com.urbanairship.location.LocationModuleLoaderFactoryImpl";
    private static final String AUTOMATION_MODULE_LOADER_FACTORY = "com.urbanairship.automation.AutomationModuleLoaderFactoryImpl";

    @Nullable
    public static AccengageModuleLoader accengageLoader(@NonNull Context context,
                                                        @NonNull PreferenceDataStore preferenceDataStore,
                                                        @NonNull AirshipChannel channel,
                                                        @NonNull PushManager pushManager,
                                                        @NonNull Analytics analytics) {
        try {
            Class clazz = Class.forName(ACCENGAGE_MODULE_LOADER_FACTORY);
            Object object = clazz.newInstance();

            if (object instanceof AccengageModuleLoaderFactory) {
                AccengageModuleLoaderFactory factory = (AccengageModuleLoaderFactory) object;
                return factory.build(context, preferenceDataStore, channel, pushManager, analytics);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            Logger.error(e, "Unable to create loader %s", ACCENGAGE_MODULE_LOADER_FACTORY);
        }

        return null;
    }

    @Nullable
    public static ModuleLoader messageCenterLoader(@NonNull Context context,
                                                   @NonNull PreferenceDataStore preferenceDataStore,
                                                   @NonNull AirshipChannel channel,
                                                   @NonNull PushManager pushManager) {
        try {
            Class clazz = Class.forName(MESSAGE_CENTER_MODULE_LOADER_FACTORY);
            Object object = clazz.newInstance();

            if (object instanceof MessageCenterModuleLoaderFactory) {
                MessageCenterModuleLoaderFactory factory = (MessageCenterModuleLoaderFactory) object;
                return factory.build(context, preferenceDataStore, channel, pushManager);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            Logger.error(e, "Unable to create loader %s", MESSAGE_CENTER_MODULE_LOADER_FACTORY);
        }

        return null;
    }

    @Nullable
    public static LocationModuleLoader locationLoader(@NonNull Context context,
                                                      @NonNull PreferenceDataStore preferenceDataStore,
                                                      @NonNull AirshipChannel channel,
                                                      @NonNull Analytics analytics) {
        try {
            Class clazz = Class.forName(LOCATION_MODULE_LOADER_FACTORY);
            Object object = clazz.newInstance();

            if (object instanceof LocationModuleLoaderFactory) {
                LocationModuleLoaderFactory factory = (LocationModuleLoaderFactory) object;
                return factory.build(context, preferenceDataStore, channel, analytics);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            Logger.error(e, "Unable to create loader %s", LOCATION_MODULE_LOADER_FACTORY);
        }

        return null;
    }

    @Nullable
    public static ModuleLoader automationLoader(@NonNull Context context,
                                                @NonNull PreferenceDataStore dataStore,
                                                @NonNull AirshipRuntimeConfig runtimeConfig,
                                                @NonNull AirshipChannel airshipChannel,
                                                @NonNull PushManager pushManager,
                                                @NonNull Analytics analytics,
                                                @NonNull RemoteData remoteData,
                                                @NonNull TagGroupRegistrar tagGroupRegistrar) {
        try {
            Class clazz = Class.forName(AUTOMATION_MODULE_LOADER_FACTORY);
            Object object = clazz.newInstance();

            if (object instanceof AutomationModuleLoaderFactory) {
                AutomationModuleLoaderFactory factory = (AutomationModuleLoaderFactory) object;
                return factory.build(context, dataStore, runtimeConfig, airshipChannel, pushManager,
                        analytics, remoteData, tagGroupRegistrar);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            Logger.error(e, "Unable to create loader %s", LOCATION_MODULE_LOADER_FACTORY);
        }

        return null;
    }


}
