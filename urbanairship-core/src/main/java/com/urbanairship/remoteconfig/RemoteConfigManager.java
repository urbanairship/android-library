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
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.reactive.Subscriber;
import com.urbanairship.reactive.Subscription;
import com.urbanairship.remotedata.RemoteData;
import com.urbanairship.remotedata.RemoteDataPayload;

import java.util.ArrayList;
import java.util.Collection;
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
                                 .subscribe(new Subscriber<Collection<RemoteDataPayload>>() {
                                     @Override
                                     public void onNext(@NonNull Collection<RemoteDataPayload> remoteDataPayloads) {
                                         try {
                                             processRemoteData(remoteDataPayloads);
                                         } catch (Exception e) {
                                             Logger.error(e, "Failed process remote data");
                                         }
                                     }
                                 });
    }

    /**
     * Processes the remote data payloads.
     *
     * @param remoteDataPayloads The remote data payloads.
     */
    private void processRemoteData(@NonNull Collection<RemoteDataPayload> remoteDataPayloads) {

        List<DisableInfo> disableInfos = new ArrayList<>();

        Map<String, List<JsonValue>> config = new HashMap<>();

        for (RemoteDataPayload payload : remoteDataPayloads) {
            for (String key : payload.getData().keySet()) {

                JsonValue value = payload.getData().opt(key);

                if (DISABLE_INFO_KEY.equals(key)) {
                    for (JsonValue disableInfoJson : value.optList()) {
                        try {
                            disableInfos.add(DisableInfo.fromJson(disableInfoJson));
                        } catch (JsonException e) {
                            Logger.error(e, "Failed to parse remote config: %s", payload);
                        }
                    }
                    continue;
                }

                // Treat it like its config
                List<JsonValue> moduleConfig = config.get(key);
                if (moduleConfig == null) {
                    moduleConfig = new ArrayList<>();
                    config.put(key, moduleConfig);
                }
                moduleConfig.add(value);
            }
        }

        apply(DisableInfo.filter(disableInfos, UAirship.getVersion(), UAirship.getAppVersion()));

        // Notify new config
        for (Map.Entry<String, List<JsonValue>> entry : config.entrySet()) {
            String module = entry.getKey();
            moduleAdapter.onNewConfig(module, new JsonList(entry.getValue()));
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
