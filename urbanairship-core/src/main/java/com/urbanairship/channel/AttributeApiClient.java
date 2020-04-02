/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.net.URL;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import static com.urbanairship.UAirship.AMAZON_PLATFORM;

/**
 * A high level abstraction for performing attribute requests.
 */
class AttributeApiClient extends BaseApiClient {

    private static final String CHANNEL_API_PATH = "api/channels/";
    private static final String ATTRIBUTE_PARAM = "attributes";

    private static final String ATTRIBUTE_PLATFORM_QUERY_PARAM = "platform";

    private static final String ATTRIBUTE_PAYLOAD_KEY = "attributes";

    private static final String ATTRIBUTE_PLATFORM_ANDROID = "android";
    private static final String ATTRIBUTE_PLATFORM_AMAZON = "amazon";

    private final AirshipRuntimeConfig runtimeConfig;

    AttributeApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    AttributeApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                       @NonNull RequestFactory requestFactory) {
        super(runtimeConfig, requestFactory);
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Update the attributes for the given channel identifier.
     *
     * @param channelId The channel Id.
     * @param mutations The attribute mutations.
     * @return The response or null if an error occurred.
     */
    @Nullable
    Response updateAttributes(@NonNull String channelId, @NonNull List<PendingAttributeMutation> mutations) {
        URL attributeUrl = getAttributeUrl(channelId);

        if (attributeUrl == null) {
            Logger.error("Invalid attribute URL. Unable to update attributes.");
            return null;
        }

        JsonList attributes = JsonValue.wrapOpt(mutations).optList();

        String attributePayload = JsonMap.newBuilder()
                                         .putOpt(ATTRIBUTE_PAYLOAD_KEY, attributes)
                                         .build().toString();

        Logger.verbose("Updating channel Id:%s with payload: %s", channelId, attributePayload);

        Response response = performRequest(attributeUrl, "POST", attributePayload);

        return response;
    }

    /**
     * Gets a device url for a given path.
     *
     * @return The device URL or {@code null} if the URL is invalid.
     */
    @Nullable
    private URL getAttributeUrl(@NonNull String channelId) {
        return runtimeConfig.getUrlConfig()
                            .deviceUrl()
                            .appendEncodedPath(CHANNEL_API_PATH)
                            .appendPath(channelId)
                            .appendPath(ATTRIBUTE_PARAM)
                            .appendQueryParameter(ATTRIBUTE_PLATFORM_QUERY_PARAM, getPlatform())
                            .build();
    }

    @NonNull
    private String getPlatform() {
        if (runtimeConfig.getPlatform() == AMAZON_PLATFORM) {
            return ATTRIBUTE_PLATFORM_AMAZON;
        }

        return ATTRIBUTE_PLATFORM_ANDROID;
    }

}
