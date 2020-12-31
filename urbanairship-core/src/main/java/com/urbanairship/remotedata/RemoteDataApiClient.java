/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.os.Build;

import com.urbanairship.PushProviders;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.config.UrlBuilder;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushProvider;
import com.urbanairship.util.UAStringUtil;

import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * API client for fetching remote data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataApiClient {

    private static final String REMOTE_DATA_PATH = "api/remote-data/app/";
    private static final String SDK_VERSION_QUERY_PARAM = "sdk_version";
    // ISO 639-2 two digit country code
    private static final String COUNTRY_QUERY_PARAM = "country";
    // ISO 3166-2 two digit language code
    private static final String LANGUAGE_QUERY_PARAM = "language";

    private static final String MANUFACTURER_QUERY_PARAM = "manufacturer";
    private static final String PUSH_PROVIDER_QUERY_PARAM = "push_providers";

    private static final List<String> MANUFACTURERS_ALLOWED = Collections.singletonList("huawei");

    private static final String AMAZON = "amazon";
    private static final String ANDROID = "android";

    private final AirshipRuntimeConfig runtimeConfig;
    private final PushProviders pushProviders;
    private final RequestFactory requestFactory;

    /**
     * RemoteDataApiClient constructor.
     *
     * @param runtimeConfig The runtime config.
     * @param pushProviders The push providers
     */
    RemoteDataApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                        @NonNull PushProviders pushProviders) {
        this(runtimeConfig, pushProviders, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    /**
     * RemoteDataApiClient constructor.
     *
     * @param runtimeConfig The runtime config.
     * @param pushProviders The push providers
     * @param requestFactory A RequestFactory.
     */
    @VisibleForTesting
    RemoteDataApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                        @NonNull PushProviders pushProviders,
                        @NonNull RequestFactory requestFactory) {
        this.runtimeConfig = runtimeConfig;
        this.pushProviders = pushProviders;
        this.requestFactory = requestFactory;
    }

    /**
     * Executes a remote data request.
     *
     * @param lastModified An optional last-modified timestamp in ISO-8601 format.
     * @param locale The current locale.
     * @return A Response.
     */
    @NonNull
    Response<Set<RemoteDataPayload>> fetchRemoteData(@Nullable String lastModified, @NonNull final Locale locale) throws RequestException {
        URL url = getRemoteDataURL(locale);

        Request request = requestFactory.createRequest()
                                        .setOperation("GET", url)
                                        .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret);

        if (lastModified != null) {
            request.setHeader("If-Modified-Since", lastModified);
        }

        return request.execute(new ResponseParser<Set<RemoteDataPayload>>() {
            @Override
            public Set<RemoteDataPayload> parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                if (status == 200) {
                    JsonMap metadata = RemoteData.createMetadata(locale);
                    JsonValue json = JsonValue.parseString(responseBody);
                    JsonMap map = json.optMap();
                    if (map.containsKey("payloads")) {
                        return RemoteDataPayload.parsePayloads(map.opt("payloads"), metadata);
                    } else {
                        throw new JsonException("Response does not contain payloads");
                    }
                } else {
                    return null;
                }
            }
        });
    }

    /**
     * Gets a device url for a given path.
     *
     * @param locale The current locale.
     * @return The device URL or {@code null} if the URL is invalid.
     */
    @Nullable
    private URL getRemoteDataURL(@NonNull Locale locale) {
        // api/remote-data/app/{appkey}/{platform}?sdk_version={version}&language={language}&country={country}&manufacturer={manufacturer}&push_providers={push_providers}

        UrlBuilder builder = runtimeConfig.getUrlConfig()
                                              .remoteDataUrl()
                                              .appendEncodedPath(REMOTE_DATA_PATH)
                                              .appendPath(runtimeConfig.getConfigOptions().appKey)
                                              .appendPath(runtimeConfig.getPlatform() == UAirship.AMAZON_PLATFORM ? AMAZON : ANDROID)
                                              .appendQueryParameter(SDK_VERSION_QUERY_PARAM, UAirship.getVersion());

        String manufacturer = getManufacturer();
        if (shouldIncludeManufacturer(manufacturer)) {
            builder.appendQueryParameter(MANUFACTURER_QUERY_PARAM, manufacturer);
        }

        String providers = getPushProviderCsv();
        if (providers != null) {
            builder.appendQueryParameter(PUSH_PROVIDER_QUERY_PARAM, providers);
        }

        if (!UAStringUtil.isEmpty(locale.getLanguage())) {
            builder.appendQueryParameter(LANGUAGE_QUERY_PARAM, locale.getLanguage());
        }

        if (!UAStringUtil.isEmpty(locale.getCountry())) {
            builder.appendQueryParameter(COUNTRY_QUERY_PARAM, locale.getCountry());
        }

        return builder.build();
    }

    private boolean shouldIncludeManufacturer(@NonNull String manufacturer) {
        return MANUFACTURERS_ALLOWED.contains(manufacturer.toLowerCase());
    }

    @NonNull
    private static String getManufacturer() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer == null) {
            return "";
        }
        return manufacturer.toLowerCase(Locale.US);
    }

    @Nullable
    private String getPushProviderCsv() {
        Set<String> deliveryTypes = new HashSet<>();
        for (PushProvider provider : pushProviders.getAvailableProviders()) {
            deliveryTypes.add(provider.getDeliveryType());
        }

        if (deliveryTypes.isEmpty()) {
            return null;
        }

        return UAStringUtil.join(deliveryTypes, ",");
    }

}
