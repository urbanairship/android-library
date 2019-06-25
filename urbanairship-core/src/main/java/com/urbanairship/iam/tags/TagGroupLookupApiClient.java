/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.UAStringUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

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
    private final URL url;
    private final AirshipConfigOptions configOptions;

    /**
     * Default constructor.
     *
     * @param configOptions The config options.
     */
    TagGroupLookupApiClient(@NonNull AirshipConfigOptions configOptions) {
        this(configOptions, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    TagGroupLookupApiClient(@NonNull AirshipConfigOptions configOptions, RequestFactory requestFactory) {
        this.configOptions = configOptions;
        this.requestFactory = requestFactory;
        this.url = getUrl(configOptions);
    }

    private URL getUrl(AirshipConfigOptions configOptions) {
        Uri uri = Uri.withAppendedPath(Uri.parse(configOptions.deviceUrl), CHANNEL_TAG_LOOKUP_PATH);
        try {
            return new URL(uri.toString());
        } catch (MalformedURLException e) {
            Logger.error(e, "Invalid URL: %s", uri);
            return null;
        }
    }

    /**
     * Looks up the tag groups.
     *
     * @param channelId The channel ID.
     * @param platform The channel's platform.
     * @param requestedTags The tags to request.
     * @param cachedResponse Optional cached response.
     * @return A tag group response.
     */
    @Nullable
    TagGroupResponse lookupTagGroups(String channelId, @UAirship.Platform int platform,
                                     Map<String, Set<String>> requestedTags, @Nullable TagGroupResponse cachedResponse) {

        if (url == null) {
            Logger.error("No URL, unable to process request.");
            return null;
        }

        String deviceType = platform == UAirship.AMAZON_PLATFORM ? AMAZON_PLATFORM : ANDROID_PLATFORM;

        JsonMap payload = JsonMap.newBuilder()
                                 .put(CHANNEL_ID_KEY, channelId)
                                 .put(DEVICE_TYPE_KEY, deviceType)
                                 .putOpt(TAG_GROUPS_KEY, requestedTags)
                                 .put(IF_MODIFIED_SINCE_KEY, cachedResponse != null ? cachedResponse.lastModifiedTime : null)
                                 .build();

        String tagPayload = payload.toString();
        Logger.debug("Looking up tags with payload: %s", tagPayload);

        Response response = requestFactory.createRequest("POST", url)
                                          .setCredentials(configOptions.appKey, configOptions.appSecret)
                                          .setRequestBody(tagPayload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        if (response == null) {
            Logger.error("Failed to refresh the cache.");
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
