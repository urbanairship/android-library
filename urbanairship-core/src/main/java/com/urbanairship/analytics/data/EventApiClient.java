/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * A client that handles uploading analytic events
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EventApiClient {

    private static final String WARP9_PATH = "warp9/";

    private final RequestFactory requestFactory;
    private final AirshipRuntimeConfig runtimeConfig;

    /**
     * Default constructor.
     *
     * @param runtimeConfig The runtime config.
     */
    public EventApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, RequestFactory.DEFAULT_REQUEST_FACTORY);
    }

    @VisibleForTesting
    EventApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                   @NonNull RequestFactory requestFactory) {
        this.runtimeConfig = runtimeConfig;
        this.requestFactory = requestFactory;
    }

    /**
     * Sends a collection of events.
     *
     * @param events Specified events
     * @param headers Headers
     * @return eventResponse or null if an error occurred
     */
    @Nullable
    EventResponse sendEvents(@NonNull Collection<String> events,
                             @NonNull Map<String, String> headers) {

        if (events.isEmpty()) {
            Logger.verbose("EventApiClient - No analytics events to send.");
            return null;
        }

        URL url = runtimeConfig.getUrlConfig()
                               .analyticsUrl()
                               .appendEncodedPath(WARP9_PATH)
                               .build();

        if (url == null) {
            Logger.debug("Analytics URL is null, unable to send events.");
            return null;
        }

        List<JsonValue> eventJSON = new ArrayList<>();

        for (String eventPayload : events) {
            try {
                eventJSON.add(JsonValue.parseString(eventPayload));
            } catch (JsonException e) {
                Logger.error(e, "EventApiClient - Invalid eventPayload.");
            }
        }

        String payload = new JsonList(eventJSON).toString();
        double sentAt = System.currentTimeMillis() / 1000.0;

        Request request = requestFactory.createRequest("POST", url)
                                        .setRequestBody(payload, "application/json")
                                        .setCompressRequestBody(true)
                                        .setHeader("X-UA-Sent-At", String.format(Locale.US, "%.3f", sentAt))
                                        .addHeaders(headers);

        Logger.debug("EventApiClient - Sending analytics events. Request: %s Events: %s", request, events);
        Response response = request.execute();

        Logger.debug("EventApiClient - Analytics event response: %s", response);
        return response == null ? null : new EventResponse(response);
    }

}
