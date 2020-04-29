/* Copyright Airship and Contributors */

package com.urbanairship.http;

import androidx.annotation.RestrictTo;

/**
 * An exception raised when an error occurs during a request.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RequestException extends Exception {

    public RequestException(String message) {
        super(message);
    }

    public RequestException(String message, Throwable e) {
        super(message, e);
    }

}
