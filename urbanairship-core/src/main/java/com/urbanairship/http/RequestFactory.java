/* Copyright Airship and Contributors */

package com.urbanairship.http;

import androidx.annotation.NonNull;
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
     * @return The request.
     */
    @NonNull
    public Request createRequest() {
        return new Request();
    }

}
