/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonMap;

import java.util.List;

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
    private final RequestFactory requestFactory;
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
                       @NonNull RequestFactory requestFactory,
                       @NonNull UrlFactory urlFactory) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
        this.urlFactory = urlFactory;
    }

    public static AttributeApiClient namedUserClient(AirshipRuntimeConfig runtimeConfig) {
        return new AttributeApiClient(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY,
                NAMED_USER_URL_FACTORY);
    }

    public static AttributeApiClient channelClient(AirshipRuntimeConfig runtimeConfig) {
        return new AttributeApiClient(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY,
                CHANNEL_URL_FACTORY);
    }

    public static AttributeApiClient contactClient(AirshipRuntimeConfig runtimeConfig) {
        return new AttributeApiClient(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY,
                CONTACT_URL_FACTORY);
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

        return requestFactory.createRequest()
                             .setOperation("POST", url)
                             .setAirshipUserAgent(runtimeConfig)
                             .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                             .setRequestBody(attributePayload)
                             .setAirshipJsonAcceptsHeader()
                             .execute();
    }

}
