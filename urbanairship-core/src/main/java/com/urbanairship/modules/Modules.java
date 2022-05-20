/* Copyright Airship and Contributors */

package com.urbanairship.modules;

import android.content.Context;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.contacts.Contact;
import com.urbanairship.modules.aaid.AdIdModuleFactory;
import com.urbanairship.modules.accengage.AccengageModule;
import com.urbanairship.modules.accengage.AccengageModuleFactory;
import com.urbanairship.modules.automation.AutomationModuleFactory;
import com.urbanairship.modules.chat.ChatModuleFactory;
import com.urbanairship.modules.debug.DebugModuleFactory;
import com.urbanairship.modules.location.LocationModule;
import com.urbanairship.modules.location.LocationModuleFactory;
import com.urbanairship.modules.messagecenter.MessageCenterModuleFactory;
import com.urbanairship.modules.preferencecenter.PreferenceCenterModuleFactory;
import com.urbanairship.permission.PermissionsManager;
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
    private static final String DEBUG_MODULE_FACTORY = "com.urbanairship.debug.DebugModuleFactoryImpl";
    private static final String AD_ID_FACTORY = "com.urbanairship.aaid.AdIdModuleFactoryImpl";
    private static final String CHAT_FACTORY = "com.urbanairship.chat.ChatModuleFactoryImpl";
    private static final String PREFERENCE_CENTER_FACTORY = "com.urbanairship.preferencecenter.PreferenceCenterModuleFactoryImpl";

    @Nullable
    public static AccengageModule accengage(@NonNull Context context,
                                            @NonNull AirshipConfigOptions configOptions,
                                            @NonNull PreferenceDataStore preferenceDataStore,
                                            @NonNull PrivacyManager privacyManager,
                                            @NonNull AirshipChannel channel,
                                            @NonNull PushManager pushManager) {
        try {
            AccengageModuleFactory moduleFactory = createFactory(ACCENGAGE_MODULE_FACTORY, AccengageModuleFactory.class);
            if (moduleFactory != null) {
                return moduleFactory.build(context, configOptions, preferenceDataStore, privacyManager, channel, pushManager);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to build Accengage module");
        }
        return null;
    }

    @Nullable
    public static Module messageCenter(@NonNull Context context,
                                       @NonNull PreferenceDataStore preferenceDataStore,
                                       @NonNull PrivacyManager privacyManager,
                                       @NonNull AirshipChannel channel,
                                       @NonNull PushManager pushManager,
                                       @NonNull AirshipConfigOptions configOptions) {

        try {
            MessageCenterModuleFactory moduleFactory = createFactory(MESSAGE_CENTER_MODULE_FACTORY, MessageCenterModuleFactory.class);
            if (moduleFactory != null) {
                return moduleFactory.build(context, preferenceDataStore, privacyManager, channel, pushManager, configOptions);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to build Message Center module");
        }
        return null;
    }

    @Nullable
    public static LocationModule location(@NonNull Context context,
                                          @NonNull PreferenceDataStore preferenceDataStore,
                                          @NonNull PrivacyManager privacyManager,
                                          @NonNull AirshipChannel channel,
                                          @NonNull PermissionsManager permissionsManager) {
        try {
            LocationModuleFactory moduleFactory = createFactory(LOCATION_MODULE_FACTORY, LocationModuleFactory.class);
            if (moduleFactory != null) {
                return moduleFactory.build(context, preferenceDataStore, privacyManager, channel, permissionsManager);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to build Location module");
        }
        return null;
    }

    @Nullable
    public static Module automation(@NonNull Context context,
                                    @NonNull PreferenceDataStore dataStore,
                                    @NonNull AirshipRuntimeConfig runtimeConfig,
                                    @NonNull PrivacyManager privacyManager,
                                    @NonNull AirshipChannel airshipChannel,
                                    @NonNull PushManager pushManager,
                                    @NonNull Analytics analytics,
                                    @NonNull RemoteData remoteData,
                                    @NonNull Contact contact) {
        try {
            AutomationModuleFactory moduleFactory = createFactory(AUTOMATION_MODULE_FACTORY, AutomationModuleFactory.class);
            if (moduleFactory != null) {
                return moduleFactory.build(context, dataStore, runtimeConfig, privacyManager, airshipChannel, pushManager,
                        analytics, remoteData, contact);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to build Automation module");
        }
        return null;
    }

    @Nullable
    public static Module debug(@NonNull Context context,
                               @NonNull PreferenceDataStore dataStore) {
        try {
            DebugModuleFactory moduleFactory = createFactory(DEBUG_MODULE_FACTORY, DebugModuleFactory.class);
            if (moduleFactory != null) {
                return moduleFactory.build(context, dataStore);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to build Debug module");
        }
        return null;
    }

    @Nullable
    public static Module adId(@NonNull Context context,
                              @NonNull PreferenceDataStore dataStore,
                              @NonNull AirshipRuntimeConfig runtimeConfig,
                              @NonNull PrivacyManager privacyManager,
                              @NonNull Analytics analytics) {
        try {
            AdIdModuleFactory moduleFactory = createFactory(AD_ID_FACTORY, AdIdModuleFactory.class);
            if (moduleFactory != null) {
                return moduleFactory.build(context, dataStore, runtimeConfig, privacyManager, analytics);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to build Ad Id module");
        }
        return null;
    }

    @Nullable
    public static Module chat(@NonNull Context context,
                              @NonNull PreferenceDataStore dataStore,
                              @NonNull AirshipRuntimeConfig config,
                              @NonNull PrivacyManager privacyManager,
                              @NonNull AirshipChannel airshipChannel,
                              @NonNull PushManager pushManager) {
        try {
            ChatModuleFactory moduleFactory = createFactory(CHAT_FACTORY, ChatModuleFactory.class);
            if (moduleFactory != null) {
                return moduleFactory.build(context, dataStore, config, privacyManager, airshipChannel, pushManager);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to build Chat module");
        }
        return null;
    }

    @Nullable
    public static Module preferenceCenter(@NonNull Context context,
                                          @NonNull PreferenceDataStore dataStore,
                                          @NonNull PrivacyManager privacyManager,
                                          @NonNull RemoteData remoteData) {
        try {
            PreferenceCenterModuleFactory moduleFactory =
                    createFactory(PREFERENCE_CENTER_FACTORY, PreferenceCenterModuleFactory.class);
            if (moduleFactory != null) {
                return moduleFactory.build(context, dataStore, privacyManager, remoteData);
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to build Preference Center module");
        }
        return null;
    }

    /**
     * Creates the factory instance.
     *
     * @param className The class name.
     * @param factoryClass The factory class.
     * @return The instance or null if not available.
     */
    @Nullable
    private static <T extends AirshipVersionInfo> T createFactory(@NonNull String className, @NonNull Class<T> factoryClass) {
        try {
            Class<? extends T> clazz = Class.forName(className).asSubclass(factoryClass);
            T instance = clazz.newInstance();
            if (!UAirship.getVersion().equals(instance.getAirshipVersion())) {
                Logger.error("Unable to load module with factory %s, versions do not match. Core Version: %s, Module Version: %s.", factoryClass, UAirship.getVersion(), instance.getAirshipVersion());
                return null;
            }
            return instance;
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            Logger.error(e, "Unable to create module factory %s", factoryClass);
        }

        return null;
    }

}
