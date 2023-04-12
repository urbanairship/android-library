/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestAuth;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestSession;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import static com.urbanairship.UAirship.AMAZON_PLATFORM;

/**
 * A high level abstraction for performing attribute requests.
 */
class AttributeApiClient {
    private static final String CHANNEL_API_PATH = "api/channels/";
    private static final String NAMED_USER_API_PATH = "api/named_users/";
    private static final String CONTACT_API_PATH = "api/contacts/";

    private static final String ATTRIBUTE_PARAM = "attributes";

    private static final String ATTRIBUTE_PLATFORM_QUERY_PARAM = "platform";

    private static final String ATTRIBUTE_PAYLOAD_KEY = "attributes";

    private static final String ATTRIBUTE_PLATFORM_ANDROID = "android";
    private static final String ATTRIBUTE_PLATFORM_AMAZON = "amazon";

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestSession session;
    private final UrlFactory urlFactory;

    @VisibleForTesting
    interface UrlFactory {

        @Nullable
        Uri createUrl(@NonNull AirshipRuntimeConfig config, @NonNull String identifier);

    }

    @VisibleForTesting
    static final UrlFactory NAMED_USER_URL_FACTORY = new UrlFactory() {
        @Nullable
        @Override
        public Uri createUrl(@NonNull AirshipRuntimeConfig config, @NonNull String identifier) {
            return config.getUrlConfig()
                         .deviceUrl()
                         .appendEncodedPath(NAMED_USER_API_PATH)
                         .appendPath(identifier)
                         .appendPath(ATTRIBUTE_PARAM)
                         .build();

        }
    };

    @VisibleForTesting
    static final UrlFactory CHANNEL_URL_FACTORY = new UrlFactory() {
        @Nullable
        @Override
        public Uri createUrl(@NonNull AirshipRuntimeConfig config, @NonNull String identifier) {
            String platform = config.getPlatform() == AMAZON_PLATFORM ? ATTRIBUTE_PLATFORM_AMAZON : ATTRIBUTE_PLATFORM_ANDROID;
            return config.getUrlConfig()
                         .deviceUrl()
                         .appendEncodedPath(CHANNEL_API_PATH)
                         .appendPath(identifier)
                         .appendPath(ATTRIBUTE_PARAM)
                         .appendQueryParameter(ATTRIBUTE_PLATFORM_QUERY_PARAM, platform)
                         .build();
        }
    };

    @VisibleForTesting
    static final UrlFactory CONTACT_URL_FACTORY = new UrlFactory() {
        @Nullable
        @Override
        public Uri createUrl(@NonNull AirshipRuntimeConfig config, @NonNull String identifier) {
            return config.getUrlConfig()
                    .deviceUrl()
                    .appendEncodedPath(CONTACT_API_PATH)
                    .appendPath(identifier)
                    .appendPath(ATTRIBUTE_PARAM)
                    .build();

        }
    };

    @VisibleForTesting
    AttributeApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                       @NonNull RequestSession requestSession,
                       @NonNull UrlFactory urlFactory) {
        this.runtimeConfig = runtimeConfig;
        this.session = requestSession;
        this.urlFactory = urlFactory;
    }

    public static AttributeApiClient namedUserClient(AirshipRuntimeConfig runtimeConfig) {
        return new AttributeApiClient(runtimeConfig, runtimeConfig.getRequestSession(), NAMED_USER_URL_FACTORY);
    }

    public static AttributeApiClient channelClient(AirshipRuntimeConfig runtimeConfig) {
        return new AttributeApiClient(runtimeConfig, runtimeConfig.getRequestSession(), CHANNEL_URL_FACTORY);
    }

    public static AttributeApiClient contactClient(AirshipRuntimeConfig runtimeConfig) {
        return new AttributeApiClient(runtimeConfig, runtimeConfig.getRequestSession(), CONTACT_URL_FACTORY);
    }

    /**
     * Update the attributes for the given identifier.
     *
     * @param identifier The identifier.
     * @param mutations The attribute mutations.
     * @return The response.
     */
    @NonNull
    Response<Void> updateAttributes(@NonNull String identifier, @NonNull List<AttributeMutation> mutations) throws RequestException {
        Uri url = urlFactory.createUrl(runtimeConfig, identifier);

        JsonMap attributePayload = JsonMap.newBuilder()
                                          .putOpt(ATTRIBUTE_PAYLOAD_KEY, mutations)
                                          .build();

        Logger.verbose("Updating attributes for Id:%s with payload: %s", identifier, attributePayload);



        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.urbanairship+json; version=3;");

        Request request = new Request(
                url,
                "POST",
                RequestAuth.BasicAppAuth.INSTANCE,
                new RequestBody.Json(attributePayload),
                headers
        );

        return session.execute(request, (status, headers1, responseBody) -> null);
    }

}
