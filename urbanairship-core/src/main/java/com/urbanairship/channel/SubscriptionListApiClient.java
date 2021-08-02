package com.urbanairship.channel;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Subscription list API client.
 */
public class SubscriptionListApiClient {

    private static final String CHANNEL_SUBSCRIPTIONS_UPDATE_PATH = "api/channels/subscription_lists";
    private static final String CHANNEL_SUBSCRIPTIONS_LIST_PATH = "api/subscription_lists/channels";

    private static final String ANDROID_CHANNEL_KEY = "android_channel";
    private static final String AMAZON_CHANNEL_KEY = "amazon_channel";

    private static final String AUDIENCE_KEY = "audience";
    private static final String SUBSCRIPTION_LISTS_KEY = "subscription_lists";

    private static final String LIST_IDS_KEY = "list_ids";

    private final AirshipRuntimeConfig runtimeConfig;
    private final RequestFactory requestFactory;
    private final Callable<String> audienceKey;
    private final String listPath;
    private final String updatePath;

    @VisibleForTesting
    SubscriptionListApiClient(
            @NonNull AirshipRuntimeConfig runtimeConfig,
            @NonNull RequestFactory requestFactory,
            @NonNull Callable<String> audienceKey,
            @NonNull String listPath,
            @NonNull String updatePath
    ) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
        this.audienceKey = audienceKey;
        this.listPath = listPath;
        this.updatePath = updatePath;
    }

    public static SubscriptionListApiClient channelClient(final AirshipRuntimeConfig runtimeConfig) {
        return new SubscriptionListApiClient(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY,
                new Callable<String>() {
                    @Override
                    public String call() {
                        switch (runtimeConfig.getPlatform()) {
                            case UAirship.AMAZON_PLATFORM:
                                return AMAZON_CHANNEL_KEY;
                            case UAirship.ANDROID_PLATFORM:
                                return ANDROID_CHANNEL_KEY;
                            case UAirship.UNKNOWN_PLATFORM:
                            default:
                                throw new IllegalStateException("Invalid platform");
                        }
                    }
                },
                CHANNEL_SUBSCRIPTIONS_LIST_PATH,
                CHANNEL_SUBSCRIPTIONS_UPDATE_PATH
        );
    }

    /**
     * Uploads subscription list edits.
     *
     * @param identifier The identifier (a Channel ID).
     * @param mutations The subscription list mutations.
     * @return The response.
     * @throws RequestException
     */
    @NonNull
    Response<Void> updateSubscriptionLists(@NonNull String identifier, @NonNull List<SubscriptionListMutation> mutations) throws RequestException {
        Uri uri = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(updatePath)
                               .build();

        ArrayList<JsonValue> mutationsValues = new ArrayList<>(mutations.size());
        for (SubscriptionListMutation mutation : mutations) {
            mutationsValues.add(mutation.toJsonValue());
        }
        JsonList mutationsJson = new JsonList(mutationsValues);
        JsonMap audienceJson = JsonMap.newBuilder()
                                      .put(getAudienceKey(), identifier)
                                      .build();

        JsonMap payload = JsonMap.newBuilder()
                                 .put(SUBSCRIPTION_LISTS_KEY, mutationsJson)
                                 .put(AUDIENCE_KEY, audienceJson)
                                 .build();

        Logger.verbose("Updating subscription lists for ID: %s with payload: %s", identifier, payload);

        return requestFactory.createRequest()
                .setOperation("POST", uri)
                .setAirshipUserAgent(runtimeConfig)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setRequestBody(payload)
                .setAirshipJsonAcceptsHeader()
                .execute();
    }

    /**
     * Fetches the current set of subscriptions for the channel.
     *
     * @return The response.
     * @throws RequestException
     */
    @NonNull
    Response<Set<String>> getSubscriptionLists(@NonNull String identifier) throws RequestException {
        Uri uri = runtimeConfig.getUrlConfig()
                               .deviceUrl()
                               .appendEncodedPath(getListPath(identifier))
                               .build();

        return requestFactory.createRequest()
                .setOperation("GET", uri)
                .setAirshipUserAgent(runtimeConfig)
                .setCredentials(runtimeConfig.getConfigOptions().appKey, runtimeConfig.getConfigOptions().appSecret)
                .setAirshipJsonAcceptsHeader()
                .execute(new ResponseParser<Set<String>>() {
                    @Override
                    public Set<String> parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) throws Exception {
                        if (!UAHttpStatusUtil.inSuccessRange(status)) {
                            return null;
                        }
                        JsonValue json = JsonValue.parseString(responseBody);
                        Set<String> listIds = new HashSet<>();

                        for (JsonValue idJson : json.optMap().opt(LIST_IDS_KEY).optList()) {
                            String id = idJson.getString();
                            if (!UAStringUtil.isEmpty(id)) {
                                listIds.add(id);
                            }
                        }
                        return listIds;
                    }
                });
    }

    @VisibleForTesting
    String getListPath(String identifier) {
        return String.format("%s/%s", listPath, identifier);
    }

    @VisibleForTesting
    String getUpdatePath() {
        return updatePath;
    }

    @VisibleForTesting
    String getAudienceKey() throws RequestException {
        try {
            return audienceKey.call();
        } catch (Exception e) {
            throw new RequestException("Audience exception", e);
        }
    }
}
