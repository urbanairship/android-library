/* Copyright Airship and Contributors */

package com.urbanairship.remoteconfig;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Remote Airship config from remote-data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteAirshipConfig implements JsonSerializable {

    private static final String REMOTE_DATA_URL_KEY = "remote_data_url";
    private static final String DEVICE_API_URL_KEY = "device_api_url";
    private static final String WALLET_URL_KEY = "wallet_url";
    private static final String ANALYTICS_URL_KEY = "analytics_url";
    private static final String CHAT_URL_KEY = "chat_url";
    private static final String CHAT_SOCKET_URL_KEY = "chat_socket_url";

    private final String remoteDataUrl;
    private final String deviceApiUrl;
    private final String walletUrl;
    private final String analyticsUrl;
    private final String chatUrl;
    private final String chatSocketUrl;

    @VisibleForTesting
    public RemoteAirshipConfig(@Nullable String remoteDataUrl,
                               @Nullable String deviceApiUrl,
                               @Nullable String walletUrl,
                               @Nullable String analyticsUrl,
                               @Nullable String chatUrl,
                               @Nullable String chatSocketUrl) {
        this.remoteDataUrl = remoteDataUrl;
        this.deviceApiUrl = deviceApiUrl;
        this.walletUrl = walletUrl;
        this.analyticsUrl = analyticsUrl;
        this.chatUrl = chatUrl;
        this.chatSocketUrl = chatSocketUrl;
    }

    @NonNull
    public static RemoteAirshipConfig fromJson(@NonNull JsonValue jsonValue) {
        JsonMap jsonMap = jsonValue.optMap();
        String remoteDataUrl = jsonMap.opt(REMOTE_DATA_URL_KEY).getString();
        String deviceApiUrl = jsonMap.opt(DEVICE_API_URL_KEY).getString();
        String walletUrl = jsonMap.opt(WALLET_URL_KEY).getString();
        String analyticsUrl = jsonMap.opt(ANALYTICS_URL_KEY).getString();
        String chatUrl = jsonMap.opt(CHAT_URL_KEY).getString();
        String chatSocketUrl = jsonMap.opt(CHAT_SOCKET_URL_KEY).getString();

        return new RemoteAirshipConfig(remoteDataUrl, deviceApiUrl, walletUrl, analyticsUrl, chatUrl, chatSocketUrl);
    }

    @Nullable
    public String getRemoteDataUrl() {
        return remoteDataUrl;
    }

    @Nullable
    public String getDeviceApiUrl() {
        return deviceApiUrl;
    }

    @Nullable
    public String getWalletUrl() {
        return walletUrl;
    }

    @Nullable
    public String getAnalyticsUrl() {
        return analyticsUrl;
    }

    @Nullable
    public String getChatUrl() {
        return chatUrl;
    }

    @Nullable
    public String getChatSocketUrl() {
        return chatSocketUrl;
    }


    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(REMOTE_DATA_URL_KEY, remoteDataUrl)
                      .put(DEVICE_API_URL_KEY, deviceApiUrl)
                      .put(ANALYTICS_URL_KEY, analyticsUrl)
                      .put(WALLET_URL_KEY, walletUrl)
                      .put(CHAT_URL_KEY, chatUrl)
                      .put(CHAT_SOCKET_URL_KEY, chatSocketUrl)
                      .build()
                      .toJsonValue();
    }

}
