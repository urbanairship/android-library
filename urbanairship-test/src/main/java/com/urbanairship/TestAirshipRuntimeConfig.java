/* Copyright Airship and Contributors */

package com.urbanairship;

import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.config.AirshipUrlConfigProvider;
import com.urbanairship.config.PlatformProvider;

import androidx.annotation.NonNull;

public class TestAirshipRuntimeConfig extends AirshipRuntimeConfig {

    private final SettableConfigProvider configProvider;
    private final SettablePlatformProvider platformProvider;
    private AirshipConfigOptions configOptions;

    public static TestAirshipRuntimeConfig newTestConfig() {
        AirshipConfigOptions configOptions = new AirshipConfigOptions.Builder()
                .setAppKey("appKey")
                .setAppSecret("appSecret")
                .build();

        AirshipUrlConfig urlConfig = AirshipUrlConfig.newBuilder()
                                                     .setAnalyticsUrl(configOptions.analyticsUrl)
                                                     .setDeviceUrl(configOptions.deviceUrl)
                                                     .setWalletUrl(configOptions.walletUrl)
                                                     .setRemoteDataUrl(configOptions.remoteDataUrl)
                                                     .build();



        return new TestAirshipRuntimeConfig(new SettablePlatformProvider(), new SettableConfigProvider(urlConfig), configOptions);
    }

    private TestAirshipRuntimeConfig(@NonNull SettablePlatformProvider platformProvider, @NonNull SettableConfigProvider configProvider, @NonNull AirshipConfigOptions configOptions) {
        super(platformProvider, configOptions, configProvider);
        this.platformProvider = platformProvider;
        this.configOptions = configOptions;
        this.configProvider = configProvider;
    }

    @NonNull
    @Override
    public AirshipConfigOptions getConfigOptions() {
        return configOptions;
    }

    public void setPlatform(@UAirship.Platform int platform) {
        this.platformProvider.platform = platform;
    }

    public void setUrlConfig(@NonNull AirshipUrlConfig urlConfig) {
        this.configProvider.urlConfig = urlConfig;
    }

    public void setConfigOptions(@NonNull AirshipConfigOptions configOptions) {
        this.configOptions = configOptions;
    }

    private static class SettableConfigProvider implements AirshipUrlConfigProvider {

        AirshipUrlConfig urlConfig;

        SettableConfigProvider(AirshipUrlConfig urlConfig) {
            this.urlConfig = urlConfig;
        }

        @NonNull
        @Override
        public AirshipUrlConfig getConfig() {
            return urlConfig;
        }

    }

    private static class SettablePlatformProvider implements PlatformProvider {

        @UAirship.Platform
        private int platform = UAirship.ANDROID_PLATFORM;

        @Override
        public int getPlatform() {
            return platform;
        }

    }
}
