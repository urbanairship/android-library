/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.UAStringUtil;

import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Tag Group lookup API client.
 */
class TagGroupLookupApiClient {

    // Request path
    private static final String CHANNEL_TAG_LOOKUP_PATH = "api/channel-tags-lookup";

    // Request keys
    private static final String CHANNEL_ID_KEY = "channel_id";

    private static final String DEVICE_TYPE_KEY = "device_type";

    private static final String TAG_GROUPS_KEY = "tag_groups";

    private static final String IF_MODIFIED_SINCE_KEY = "if_modified_since";

    private static final String ANDROID_PLATFORM = "android";

    private static final String AMAZON_PLATFORM = "amazon";

    private final RequestFactory requestFactory;
    private final AirshipRuntimeConfig runtimeConfig;

    /**
     * Default constructor.
     *
     * @param runtimeConfig The runtime config.
     */
    TagGroupLookupApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    TagGroupLookupApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                            @NonNull RequestFactory requestFactory) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
    }

    /**
     * Looks up the tag groups.
     *
     * @param channelId The channel ID.
     * @param requestedTags The tags to request.
     * @param cachedResponse Optional cached response.
     * @return A tag group response.
     */
    @Nullable
    TagGroupResponse lookupTagGroups(String channelId,
                                     Map<String, Set<String>> requestedTags,
                                     @Nullable TagGroupResponse cachedResponse) {

        Uri url = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(CHANNEL_TAG_LOOKUP_PATH)
                               .build();

        if (url == null) {
            Logger.debug("Tag Group URL is null, unable to fetch tag groups.");
            return null;
        }

        String deviceType = runtimeConfig.getPlatform() == UAirship.AMAZON_PLATFORM ? AMAZON_PLATFORM : ANDROID_PLATFORM;

        JsonMap payload = JsonMap.newBuilder()
                                 .put(CHANNEL_ID_KEY, channelId)
                                 .put(DEVICE_TYPE_KEY, deviceType)
                                 .putOpt(TAG_GROUPS_KEY, requestedTags)
                                 .put(IF_MODIFIED_SINCE_KEY, cachedResponse != null ? cachedResponse.lastModifiedTime : null)
                                 .build();

        String tagPayload = payload.toString();
        Logger.debug("Looking up tags with payload: %s", tagPayload);

        Response<Void> response;
        try {
            response = requestFactory.createRequest()
                                     .setOperation("POST", url)
                                     .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                                     .setRequestBody(tagPayload, "application/json")
                                     .setAirshipJsonAcceptsHeader()
                                     .setAirshipUserAgent(runtimeConfig)
                                     .execute();
        } catch (RequestException e) {
            Logger.error(e, "Failed to refresh the cache.");
            return null;
        }

        TagGroupResponse parsedResponse;
        try {
            parsedResponse = TagGroupResponse.fromResponse(response);
        } catch (JsonException e) {
            Logger.error(e, "Failed to parse tag group response.");
            return null;
        }

        // 200
        if (parsedResponse.status == HttpsURLConnection.HTTP_OK) {
            // Since we are doing a post here, we will never get a 304. Instead we get a response with the same
            // last modified and no tags.
            if (cachedResponse != null && parsedResponse.lastModifiedTime != null && UAStringUtil.equals(parsedResponse.lastModifiedTime, cachedResponse.lastModifiedTime)) {
                return cachedResponse;
            }
        }

        return parsedResponse;
    }

}
