/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestBody;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestSession;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;

/**
 * A client that handles uploading analytic events
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EventApiClient {

    private static final String WARP9_PATH = "warp9/";

    private final RequestSession session;
    private final AirshipRuntimeConfig runtimeConfig;

    /**
     * Default constructor.
     *
     * @param runtimeConfig The runtime config.
     */
    public EventApiClient(@NonNull AirshipRuntimeConfig runtimeConfig) {
        this(runtimeConfig, runtimeConfig.getRequestSession());
    }

    @VisibleForTesting
    EventApiClient(@NonNull AirshipRuntimeConfig runtimeConfig,
                   @NonNull RequestSession session) {
        this.runtimeConfig = runtimeConfig;
        this.session = session;
    }

    /**
     * Sends a collection of events.
     *
     * @param events Specified events
     * @param headers Headers
     * @return eventResponse
     */
    @NonNull
    Response<EventResponse> sendEvents(@NonNull List<JsonValue> events,
                                       @NonNull @Size(min=1) Map<String, String> headers) throws RequestException {

        double sentAt = System.currentTimeMillis() / 1000.0;
        Map<String, String> requestHeaders = new HashMap<>(headers);
        requestHeaders.put("X-UA-Sent-At", String.format(Locale.US, "%.3f", sentAt));

        Uri url = runtimeConfig.getUrlConfig()
                               .analyticsUrl()
                               .appendEncodedPath(WARP9_PATH)
                               .build();

        Request request = new Request(
                url,
                "POST",
                null,
                new RequestBody.GzippedJson(JsonValue.wrapOpt(events)),
                requestHeaders
        );

        Logger.debug("Sending analytics events. Request: %s Events: %s", request, events);
        Response<EventResponse> response = session.execute(request, (status, responseHeaders, responseBody) -> {
            return new EventResponse(responseHeaders);
        });

        Logger.debug("Analytics event response: %s", response);
        return response;
    }
}
