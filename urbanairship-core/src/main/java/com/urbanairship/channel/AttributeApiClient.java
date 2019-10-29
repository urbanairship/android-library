/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

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

    private final AirshipConfigOptions configOptions;

    @UAirship.Platform
    private final int platform;

    AttributeApiClient(@UAirship.Platform int platform, @NonNull AirshipConfigOptions configOptions) {
        this(platform, configOptions, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    AttributeApiClient(@UAirship.Platform int platform, @NonNull AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        super(configOptions, requestFactory);
        this.configOptions = configOptions;
        this.platform = platform;
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
        URL attributeUrl = getAttributeURL(channelId, getPlatformKey(platform));

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
    private URL getAttributeURL(@NonNull String channelId, @NonNull String platform) {
        URL url;
        // /api/channels/expected_identifier/attributes?platform=amazon
        try {
            Uri.Builder builder = Uri.parse(configOptions.deviceUrl)
                                     .buildUpon()
                                     .appendEncodedPath(CHANNEL_API_PATH)
                                     .appendPath(channelId)
                                     .appendPath(ATTRIBUTE_PARAM)
                                     .appendQueryParameter(ATTRIBUTE_PLATFORM_QUERY_PARAM, platform);

            url = new URL(builder.build().toString());
        } catch (MalformedURLException e) {
            Logger.error(e, "Invalid URL.");
            return null;
        }

        return url;
    }

    /**
     * Helper to convert the platform into its corresponding platform key.
     *
     * @param platform The attribute's platform.
     */
    private String getPlatformKey(int platform) {
        if (platform == AMAZON_PLATFORM) {
            return ATTRIBUTE_PLATFORM_AMAZON;
        }

        return ATTRIBUTE_PLATFORM_ANDROID;
    }

}
