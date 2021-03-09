/* Copyright Airship and Contributors */

package com.urbanairship.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;

/**
 * Airship URL config.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipUrlConfig {

    private final String deviceUrl;
    private final String analyticsUrl;
    private final String walletUrl;
    private final String remoteDataUrl;

    private AirshipUrlConfig(Builder builder) {
        this.deviceUrl = builder.deviceUrl;
        this.analyticsUrl = builder.analyticsUrl;
        this.walletUrl = builder.walletUrl;
        this.remoteDataUrl = builder.remoteDataUrl;
    }

    /**
     * Listener interface for receiving URL config updates.
     */
    public interface Listener {
        /**
         * Called when the URL configuration is updated.
         */
        void onUrlConfigUpdated();
    }

    /**
     * Builder factory method.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static AirshipUrlConfig.Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns a new device URL builder.
     *
     * @return A URL builder.
     */
    @NonNull
    public UrlBuilder deviceUrl() {
        return new UrlBuilder(deviceUrl);
    }

    /**
     * Returns a new wallet URL builder.
     *
     * @return A URL builder.
     */
    @NonNull
    public UrlBuilder walletUrl() {
        return new UrlBuilder(walletUrl);
    }

    /**
     * Returns a new analytics URL builder.
     *
     * @return A URL builder.
     */
    @NonNull
    public UrlBuilder analyticsUrl() {
        return new UrlBuilder(analyticsUrl);
    }

    /**
     * Returns a new remote-data URL builder.
     *
     * @return A URL builder.
     */
    @NonNull
    public UrlBuilder remoteDataUrl() {
        return new UrlBuilder(remoteDataUrl);
    }

    /**
     * URL config builder.
     */
    public static class Builder {

        private String deviceUrl;
        private String analyticsUrl;
        private String walletUrl;
        private String remoteDataUrl;

        @NonNull
        public Builder setDeviceUrl(@Nullable String url) {
            this.deviceUrl = url;
            return this;
        }

        @NonNull
        public Builder setRemoteDataUrl(@Nullable String url) {
            this.remoteDataUrl = url;
            return this;
        }

        @NonNull
        public Builder setWalletUrl(@Nullable String url) {
            this.walletUrl = url;
            return this;
        }

        @NonNull
        public Builder setAnalyticsUrl(@Nullable String url) {
            this.analyticsUrl = url;
            return this;
        }

        @NonNull
        public AirshipUrlConfig build() {
            return new AirshipUrlConfig(this);
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AirshipUrlConfig that = (AirshipUrlConfig) o;
        return ObjectsCompat.equals(analyticsUrl, that.analyticsUrl) &&
                ObjectsCompat.equals(deviceUrl, that.deviceUrl) &&
                ObjectsCompat.equals(remoteDataUrl, that.remoteDataUrl) &&
                ObjectsCompat.equals(walletUrl, that.walletUrl);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(analyticsUrl, deviceUrl, remoteDataUrl, walletUrl);
    }
}
