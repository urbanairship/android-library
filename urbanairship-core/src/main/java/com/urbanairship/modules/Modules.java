/* Copyright Airship and Contributors */

package com.urbanairship.modules;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.modules.accengage.AccengageModule;
import com.urbanairship.modules.accengage.AccengageModuleFactory;
import com.urbanairship.modules.automation.AutomationModuleFactory;
import com.urbanairship.modules.location.LocationModule;
import com.urbanairship.modules.location.LocationModuleFactory;
import com.urbanairship.modules.messagecenter.MessageCenterModuleFactory;
import com.urbanairship.push.PushManager;
import com.urbanairship.remotedata.RemoteData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Creates module used by {@link com.urbanairship.UAirship}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Modules {

    private static final String ACCENGAGE_MODULE_FACTORY = "com.urbanairship.accengage.AccengageModuleFactoryImpl";
    private static final String MESSAGE_CENTER_MODULE_FACTORY = "com.urbanairship.messagecenter.MessageCenterModuleFactoryImpl";
    private static final String LOCATION_MODULE_FACTORY = "com.urbanairship.location.LocationModuleFactoryImpl";
    private static final String AUTOMATION_MODULE_FACTORY = "com.urbanairship.automation.AutomationModuleFactoryImpl";

    @Nullable
    public static AccengageModule accengage(@NonNull Context context,
                                            @NonNull PreferenceDataStore preferenceDataStore,
                                            @NonNull AirshipChannel channel,
                                            @NonNull PushManager pushManager,
                                            @NonNull Analytics analytics) {
        try {
            Class clazz = Class.forName(ACCENGAGE_MODULE_FACTORY);
            Object object = clazz.newInstance();

            if (object instanceof AccengageModuleFactory) {
                AccengageModuleFactory factory = (AccengageModuleFactory) object;
                return factory.build(context, preferenceDataStore, channel, pushManager, analytics);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            Logger.error(e, "Unable to create module %s", ACCENGAGE_MODULE_FACTORY);
        }

        return null;
    }

    @Nullable
    public static Module messageCenter(@NonNull Context context,
                                       @NonNull PreferenceDataStore preferenceDataStore,
                                       @NonNull AirshipChannel channel,
                                       @NonNull PushManager pushManager) {
        try {
            Class clazz = Class.forName(MESSAGE_CENTER_MODULE_FACTORY);
            Object object = clazz.newInstance();

            if (object instanceof MessageCenterModuleFactory) {
                MessageCenterModuleFactory factory = (MessageCenterModuleFactory) object;
                return factory.build(context, preferenceDataStore, channel, pushManager);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            Logger.error(e, "Unable to create module %s", MESSAGE_CENTER_MODULE_FACTORY);
        }

        return null;
    }

    @Nullable
    public static LocationModule location(@NonNull Context context,
                                          @NonNull PreferenceDataStore preferenceDataStore,
                                          @NonNull AirshipChannel channel,
                                          @NonNull Analytics analytics) {
        try {
            Class clazz = Class.forName(LOCATION_MODULE_FACTORY);
            Object object = clazz.newInstance();

            if (object instanceof LocationModuleFactory) {
                LocationModuleFactory factory = (LocationModuleFactory) object;
                return factory.build(context, preferenceDataStore, channel, analytics);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            Logger.error(e, "Unable to create module %s", LOCATION_MODULE_FACTORY);
        }

        return null;
    }

    @Nullable
    public static Module automation(@NonNull Context context,
                                    @NonNull PreferenceDataStore dataStore,
                                    @NonNull AirshipRuntimeConfig runtimeConfig,
                                    @NonNull AirshipChannel airshipChannel,
                                    @NonNull PushManager pushManager,
                                    @NonNull Analytics analytics,
                                    @NonNull RemoteData remoteData,
                                    @NonNull TagGroupRegistrar tagGroupRegistrar) {
        try {
            Class clazz = Class.forName(AUTOMATION_MODULE_FACTORY);
            Object object = clazz.newInstance();

            if (object instanceof AutomationModuleFactory) {
                AutomationModuleFactory factory = (AutomationModuleFactory) object;
                return factory.build(context, dataStore, runtimeConfig, airshipChannel, pushManager,
                        analytics, remoteData, tagGroupRegistrar);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            Logger.error(e, "Unable to create module %s", AUTOMATION_MODULE_FACTORY);
        }

        return null;
    }


}
