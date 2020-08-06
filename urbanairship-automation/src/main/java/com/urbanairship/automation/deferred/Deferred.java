/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred;

import com.urbanairship.automation.ScheduleData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.net.MalformedURLException;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Deferred schedule data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Deferred implements ScheduleData {

    private static final String URL_KEY = "url";
    private static final String RETRY_ON_TIMEOUT = "retry_on_timeout";

    public URL url;
    public boolean retryOnTimeout;

    public Deferred(@NonNull URL url, boolean retryOnTimeout) {
        this.url = url;
        this.retryOnTimeout = retryOnTimeout;
    }

    /**
     * The deferred URL.
     *
     * @return The URL.
     */
    @NonNull
    public URL getUrl() {
        return url;
    }

    /**
     * If the deferred URL should be retried if no response was received.
     *
     * @return
     */
    public boolean isRetriableOnTimeout() {
        return retryOnTimeout;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(URL_KEY, url.toString())
                      .put(RETRY_ON_TIMEOUT, retryOnTimeout)
                      .build().toJsonValue();
    }

    /**
     * Parses a json value.
     *
     * @param jsonValue The json value.
     * @return The parsed deferred schedule data.
     * @throws JsonException If the json is invalid.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static Deferred fromJson(@NonNull JsonValue jsonValue) throws JsonException {
        String urlString = jsonValue.optMap().opt(URL_KEY).getString();
        if (urlString == null) {
            throw new JsonException("Missing URL");
        }

        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new JsonException("Invalid URL " + urlString, e);
        }

        boolean retryOnTimeout = jsonValue.optMap().opt(RETRY_ON_TIMEOUT).getBoolean(true);
        return new Deferred(url, retryOnTimeout);
    }

}
