/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Function;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remote config manager.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteConfigManager extends AirshipComponent {

    // Remote config types
    private static final String CONFIG_TYPE_COMMON = "app_config";
    private static final String CONFIG_TYPE_ANDROID = "app_config:android";
    private static final String CONFIG_TYPE_AMAZON = "app_config:amazon";

    // Disable config key
    private static final String DISABLE_INFO_KEY = "disable_features";

    // comparator for remote config payload sorting
    private static Comparator<RemoteDataPayload> COMPARE_BY_PAYLOAD_TYPE = new Comparator<RemoteDataPayload>() {
        @Override
        public int compare(RemoteDataPayload o1, RemoteDataPayload o2) {
            if (o1.getType().equals(o2.getType())) {
                return 0;
            }
            if (o1.getType().equals(CONFIG_TYPE_COMMON)) {
                return -1;
            }
            return 1;
        }
    };

    private final RemoteData remoteData;
    private final ModuleAdapter moduleAdapter;
    private Subscription subscription;

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @param remoteData The remote data manager.
     */
    public RemoteConfigManager(@NonNull Context context, @NonNull PreferenceDataStore dataStore, @NonNull RemoteData remoteData) {
        this(context, dataStore, remoteData, new ModuleAdapter());
    }

    @VisibleForTesting
    public RemoteConfigManager(@NonNull Context context, @NonNull PreferenceDataStore dataStore, @NonNull RemoteData remoteData, @NonNull ModuleAdapter moduleAdapter) {
        super(context, dataStore);
        this.remoteData = remoteData;
        this.moduleAdapter = moduleAdapter;
    }

    @Override
    protected void init() {
        super.init();

        String platformConfig = UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? CONFIG_TYPE_AMAZON : CONFIG_TYPE_ANDROID;
        subscription = remoteData.payloadsForTypes(CONFIG_TYPE_COMMON, platformConfig)
                                 .map(new Function<Collection<RemoteDataPayload>, JsonMap>() {
                                     @NonNull
                                     @Override
                                     public JsonMap apply(@NonNull Collection<RemoteDataPayload> remoteDataPayloads) {
                                         // sort the payloads, common first followed by platform-specific
                                         List<RemoteDataPayload> remoteDataPayloadList = new ArrayList<RemoteDataPayload>(remoteDataPayloads);
                                         Collections.sort(remoteDataPayloadList, COMPARE_BY_PAYLOAD_TYPE);

                                         // combine the payloads, overwriting common config with platform-specific config
                                         JsonMap.Builder combinedPayloadDataBuilder = JsonMap.newBuilder();
                                         for (RemoteDataPayload payload : remoteDataPayloadList) {
                                             combinedPayloadDataBuilder.putAll(payload.getData());
                                         }
                                         return combinedPayloadDataBuilder.build();
                                     }
                                 })
                                 .subscribe(new Subscriber<JsonMap>() {
                                     @Override
                                     public void onNext(@NonNull JsonMap config) {
                                         try {
                                             processConfig(config);
                                         } catch (Exception e) {
                                             Logger.error(e, "Failed to process remote data");
                                         }
                                     }
                                 });
    }

    /**
     * Processes the remote config.
     *
     * @param config The remote data config.
     */
    private void processConfig(@NonNull JsonMap config) {
        List<DisableInfo> disableInfos = new ArrayList<>();
        Map<String, JsonValue> moduleConfigs = new HashMap<>();

        for (String key : config.keySet()) {
            JsonValue value = config.opt(key);

            if (DISABLE_INFO_KEY.equals(key)) {
                for (JsonValue disableInfoJson : value.optList()) {
                    try {
                        disableInfos.add(DisableInfo.fromJson(disableInfoJson));
                    } catch (JsonException e) {
                        Logger.error(e, "Failed to parse remote config: %s", config);
                    }
                }
                continue;
            }

            moduleConfigs.put(key, value);
        }

        apply(DisableInfo.filter(disableInfos, UAirship.getVersion(), UAirship.getAppVersion()));

        // Notify new config
        Set<String> modules = new HashSet<>(Modules.ALL_MODULES);
        modules.addAll(moduleConfigs.keySet());
        for (String module : modules) {
            JsonValue moduleConfig = moduleConfigs.get(module);
            if (moduleConfig == null) {
                moduleAdapter.onNewConfig(module, null);
            } else {
                moduleAdapter.onNewConfig(module, moduleConfig.optMap());
            }
        }
    }

    @Override
    protected void tearDown() {
        super.tearDown();

        if (this.subscription != null) {
            this.subscription.cancel();
        }
    }

    /**
     * Disables and enables airship components.
     *
     * @param disableInfos The list of disable infos.
     */

    private void apply(@NonNull List<DisableInfo> disableInfos) {
        Set<String> disableModules = new HashSet<>();
        Set<String> enabledModules = new HashSet<>(Modules.ALL_MODULES);

        long remoteDataInterval = 0;

        // Combine the disable modules and remote data interval
        for (DisableInfo info : disableInfos) {
            disableModules.addAll(info.getDisabledModules());
            enabledModules.removeAll(info.getDisabledModules());
            remoteDataInterval = Math.max(remoteDataInterval, info.getRemoteDataRefreshInterval());
        }

        // Disable
        for (String module : disableModules) {
            moduleAdapter.setComponentEnabled(module, false);
        }

        // Enable
        for (String module : enabledModules) {
            moduleAdapter.setComponentEnabled(module, true);
        }

        // Remote data refresh interval
        remoteData.setForegroundRefreshInterval(remoteDataInterval);
    }

}
