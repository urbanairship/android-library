/* Copyright Airship and Contributors */

package com.urbanairship.config;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.remoteconfig.RemoteAirshipConfig;
import com.urbanairship.remoteconfig.RemoteAirshipConfigListener;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * AirshipUrlConfigProvider that is able to update from RemoteAirshipConfig.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteAirshipUrlConfigProvider implements AirshipUrlConfigProvider, RemoteAirshipConfigListener {

    private static final String REMOTE_CONFIG_KEY = "com.urbanairship.config.REMOTE_CONFIG_KEY";

    private final PreferenceDataStore preferenceDataStore;
    private final AirshipConfigOptions configOptions;
    private AirshipUrlConfig urlConfig;

    public RemoteAirshipUrlConfigProvider(@NonNull AirshipConfigOptions configOptions,
                                          @NonNull PreferenceDataStore preferenceDataStore) {

        this.configOptions = configOptions;
        this.preferenceDataStore = preferenceDataStore;

        restoreConfig();
    }

    private void restoreConfig() {
        RemoteAirshipConfig config = RemoteAirshipConfig.fromJson(preferenceDataStore.getJsonValue(REMOTE_CONFIG_KEY));
        updateConfig(config);
    }

    private void updateConfig(@NonNull RemoteAirshipConfig remoteAirshipConfig) {
        this.urlConfig = AirshipUrlConfig.newBuilder()
                                         .setRemoteDataUrl(firstOrNull(remoteAirshipConfig.getRemoteDataUrl(), configOptions.remoteDataUrl))
                                         .setWalletUrl(firstOrNull(remoteAirshipConfig.getWalletUrl(), configOptions.walletUrl))
                                         .setAnalyticsUrl(firstOrNull(remoteAirshipConfig.getAnalyticsUrl(), configOptions.analyticsUrl))
                                         .setDeviceUrl(firstOrNull(remoteAirshipConfig.getDeviceApiUrl(), configOptions.deviceUrl))
                                         .build();
    }

    @NonNull
    @Override
    public AirshipUrlConfig getConfig() {
        return urlConfig;
    }

    @Override
    public void onRemoteConfigUpdated(@NonNull RemoteAirshipConfig remoteAirshipConfig) {
        updateConfig(remoteAirshipConfig);
        preferenceDataStore.put(REMOTE_CONFIG_KEY, remoteAirshipConfig);
    }

    private static String firstOrNull(@NonNull String... args) {
        for (String arg : args) {
            if (!UAStringUtil.isEmpty(arg)) {
                return arg;
            }
        }
        return null;
    }

}
