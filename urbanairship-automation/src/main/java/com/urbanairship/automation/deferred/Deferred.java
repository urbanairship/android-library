/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred;

import android.net.Uri;

import com.urbanairship.automation.ScheduleData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.MalformedURLException;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringDef;

/**
 * Deferred schedule data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Deferred implements ScheduleData {

    /**
     * Schedule types.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @StringDef({ TYPE_IN_APP_MESSAGE })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    /**
     * Message in-app automation type.
     *
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String TYPE_IN_APP_MESSAGE = "in_app_message";

    private static final String URL_KEY = "url";
    private static final String RETRY_ON_TIMEOUT = "retry_on_timeout";
    private static final String TYPE = "type";

    private final Uri url;
    private final boolean retryOnTimeout;
    private final String type;

    public Deferred(@NonNull Uri url, boolean retryOnTimeout) {
        this(url, retryOnTimeout, null);
    }

    public Deferred(@NonNull Uri url, boolean retryOnTimeout, @Type @Nullable String type) {
        this.url = url;
        this.retryOnTimeout = retryOnTimeout;
        this.type = type;
    }

    /**
     * The deferred URL.
     *
     * @return The URL.
     */
    @NonNull
    public Uri getUrl() {
        return url;
    }

    /**
     * The deferred type.
     *
     * @return The deferred type.
     */
    @Type
    @Nullable
    public String getType() {
        return type;
    }


    public boolean getRetryOnTimeout() {
        return retryOnTimeout;
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
                      .put(TYPE, type)
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

        String type = jsonValue.optMap().opt(TYPE).getString();

        Uri uri = Uri.parse(urlString);
        boolean retryOnTimeout = jsonValue.optMap().opt(RETRY_ON_TIMEOUT).getBoolean(true);
        return new Deferred(uri, retryOnTimeout, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Deferred deferred = (Deferred) o;

        if (retryOnTimeout != deferred.retryOnTimeout) return false;
        if (!url.equals(deferred.url)) return false;
        return type != null ? type.equals(deferred.type) : deferred.type == null;
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + (retryOnTimeout ? 1 : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

}
