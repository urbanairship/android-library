/* Copyright Airship and Contributors */

package com.urbanairship.http;

import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Class that creates the request.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RequestFactory {

    /**
     * Default request factory.
     */
    @NonNull
    public static final RequestFactory DEFAULT_REQUEST_FACTORY = new RequestFactory();

    /**
     * Creates the request.
     *
     * @param requestMethod The request method string.
     * @param url The request URL.
     * @return The request.
     */
    @NonNull
    public Request createRequest(@NonNull String requestMethod, @Nullable URL url) {
        return new Request(requestMethod, url);
    }

    /**
     * Creates the request.
     *
     * @return The request.
     */
    @NonNull
    public Request createRequest() {
        return new Request();
    }

}
