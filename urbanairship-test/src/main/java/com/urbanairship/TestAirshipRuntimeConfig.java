/* Copyright Airship and Contributors */

package com.urbanairship;

import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.AirshipUrlConfig;
import com.urbanairship.config.AirshipUrlConfigProvider;

import androidx.annotation.NonNull;

public class TestAirshipRuntimeConfig extends AirshipRuntimeConfig {

    private final SettableConfigProvider configProvider;

    @UAirship.Platform
    private int platform;

    private AirshipConfigOptions configOptions;


    public static TestAirshipRuntimeConfig newTestConfig() {
        AirshipConfigOptions configOptions =  new AirshipConfigOptions.Builder()
                .setAppKey("appKey")
                .setAppSecret("appSecret")
                .build();

        AirshipUrlConfig urlConfig = AirshipUrlConfig.newBuilder()
                                                     .setAnalyticsUrl(configOptions.analyticsUrl)
                                                     .setDeviceUrl(configOptions.deviceUrl)
                                                     .setWalletUrl(configOptions.walletUrl)
                                                     .setRemoteDataUrl(configOptions.remoteDataUrl)
                                                     .build();

        return new TestAirshipRuntimeConfig(new SettableConfigProvider(urlConfig), configOptions, UAirship.ANDROID_PLATFORM);
    }

    private TestAirshipRuntimeConfig(@NonNull SettableConfigProvider configProvider, @NonNull AirshipConfigOptions configOptions, int platform) {
        super(platform, configOptions, configProvider);

        this.platform = platform;
        this.configOptions = configOptions;
        this.configProvider = configProvider;
    }

    @Override
    public int getPlatform() {
        return platform;
    }

    @NonNull
    @Override
    public AirshipConfigOptions getConfigOptions() {
        return configOptions;
    }

    public void setPlatform(@UAirship.Platform int platform) {
        this.platform = platform;
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

}
