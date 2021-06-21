/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.concurrent.Callable;

/**
 * TagGroup API client.
 */
class TagGroupApiClient {

    private static final String CHANNEL_TAGS_PATH = "api/channels/tags/";
    private static final String NAMED_USER_TAG_GROUP_PATH = "api/named_users/tags/";
    private static final String CONTACT_TAG_GROUP_PATH = "api/contacts/tags/";

    private static final String ANDROID_CHANNEL_KEY = "android_channel";
    private static final String AMAZON_CHANNEL_KEY = "amazon_channel";
    private static final String NAMED_USER_ID_KEY = "named_user_id";
    private static final String CONTACT_ID_KEY = "contact_id";

    private static final String AUDIENCE_KEY = "audience";

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestFactory requestFactory;
    private Callable<String> audienceKey;
    private final String path;

    @VisibleForTesting
    TagGroupApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                      @NonNull RequestFactory requestFactory,
                      @NonNull Callable<String> audienceKey,
                      @NonNull String path) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
        this.audienceKey = audienceKey;
        this.path = path;
    }

    public static TagGroupApiClient namedUserClient(AirshipRuntimeConfig runtimeConfig) {
        return new TagGroupApiClient(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY,
                new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return NAMED_USER_ID_KEY;
                    }
                }, NAMED_USER_TAG_GROUP_PATH);
    }

    public static TagGroupApiClient channelClient(final AirshipRuntimeConfig runtimeConfig) {
        return new TagGroupApiClient(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY,
                new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        switch (runtimeConfig.getPlatform()) {
                            case UAirship.AMAZON_PLATFORM:
                                return AMAZON_CHANNEL_KEY;
                            case UAirship.ANDROID_PLATFORM:
                                return ANDROID_CHANNEL_KEY;
                            default:
                                throw new IllegalStateException("Invalid platform");
                        }
                    }
                }, CHANNEL_TAGS_PATH);
    }

    public static TagGroupApiClient contactClient(AirshipRuntimeConfig runtimeConfig) {
        return new TagGroupApiClient(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY,
                new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return CONTACT_ID_KEY;
                    }
                }, CONTACT_TAG_GROUP_PATH);
    }

    /**
     * Uploads tags.
     *
     * @param identifier The identifier. Either named user Id or the channel id.
     * @param mutation The tag mutations.
     * @return The response.
     * @throws RequestException
     */
    @NonNull
    Response<Void> updateTags(@NonNull String identifier, @NonNull TagGroupsMutation mutation) throws RequestException {
        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(path)
                               .build();

        JsonMap payload = JsonMap.newBuilder()
                                 .putAll(mutation.toJsonValue().optMap())
                                 .put(AUDIENCE_KEY, JsonMap.newBuilder()
                                                           .put(getAudienceKey(), identifier)
                                                           .build())
                                 .build();

        Logger.verbose("Updating tag groups with path: %s, payload: %s", path, payload);
        Response<Void> response = requestFactory.createRequest()
                                                .setOperation("POST", url)
                                                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                                                .setRequestBody(payload)
                                                .setAirshipJsonAcceptsHeader()
                                                .setAirshipUserAgent(runtimeConfig)
                                                .execute();

        logTagGroupResponseIssues(response);

        return response;
    }

    @VisibleForTesting
    String getPath() {
        return path;
    }

    @VisibleForTesting
    String getAudienceKey() throws RequestException {
        try {
            return audienceKey.call();
        } catch (Exception e) {
            throw new RequestException("Audience exception", e);
        }
    }

    /**
     * Log the response warnings and errors if they exist in the response body.
     *
     * @param response The tag group response.
     */
    private void logTagGroupResponseIssues(@Nullable Response response) {
        if (response == null || response.getResponseBody() == null) {
            return;
        }

        String responseBody = response.getResponseBody();

        JsonValue responseJson;
        try {
            responseJson = JsonValue.parseString(responseBody);
        } catch (JsonException e) {
            Logger.error(e, "Unable to parse tag group response");
            return;
        }

        if (responseJson.isJsonMap()) {
            // Check for any warnings in the response and log them if they exist.
            if (responseJson.optMap().containsKey("warnings")) {
                for (JsonValue warning : responseJson.optMap().opt("warnings").optList()) {
                    Logger.warn("Tag Groups warnings: %s", warning);
                }
            }

            // Check for any errors in the response and log them if they exist.
            if (responseJson.optMap().containsKey("error")) {
                Logger.error("Tag Groups error: %s", responseJson.optMap().get("error"));
            }
        }
    }

}
