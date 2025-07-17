/* Copyright Airship and Contributors */
package com.urbanairship.http

import androidx.annotation.RestrictTo

/**
 * An exception raised when an error occurs during a request.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RequestException : Exception {

    public constructor(message: String?) : super(message)

    public constructor(message: String?, e: Throwable?) : super(message, e)
}
