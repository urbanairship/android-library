/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.http;

import android.support.annotation.NonNull;

import java.net.URL;

/**
 * Class that creates the request.
 */
public class RequestFactory {

    /**
     * Default request factory.
     */
    public static final RequestFactory DEFAULT_REQUEST_FACTORY = new RequestFactory();

    /**
     * Creates the request.
     *
     * @param requestMethod The request method string.
     * @param url The request URL.
     * @return The request.
     */
    @NonNull
    public Request createRequest(String requestMethod, URL url) {
        return new Request(requestMethod, url);
    }
}
