/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.Request;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.http.ResponseParser;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
     * @return eventResponse
     */
    @NonNull
    Response<EventResponse> sendEvents(@NonNull List<JsonValue> events,
                                       @NonNull @Size(min=1) Map<String, String> headers) throws RequestException {

        Uri url = runtimeConfig.getUrlConfig()
                               .analyticsUrl()
                               .appendEncodedPath(WARP9_PATH)
                               .build();

        String payload = JsonValue.wrapOpt(events).toString();
        double sentAt = System.currentTimeMillis() / 1000.0;

        Request request = requestFactory.createRequest()
                                        .setOperation("POST", url)
                                        .setRequestBody(payload, "application/json")
                                        .setCompressRequestBody(true)
                                        .setHeader("X-UA-Sent-At", String.format(Locale.US, "%.3f", sentAt))
                                        .setAirshipUserAgent(runtimeConfig)
                                        .addHeaders(headers);

        Logger.debug("Sending analytics events. Request: %s Events: %s", request, events);
        Response<EventResponse> response = request.execute(new ResponseParser<EventResponse>() {
            @Override
            public EventResponse parseResponse(int status, @Nullable Map<String, List<String>> headers, @Nullable String responseBody) {
                headers = headers == null ? Collections.<String, List<String>>emptyMap() : headers;
                return new EventResponse(headers);
            }
        });

        Logger.debug("Analytics event response: %s", response);
        return response;
    }
}
