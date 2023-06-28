/* Copyright Airship and Contributors */
package com.urbanairship.http

import androidx.annotation.RestrictTo

/**
 * Parses a response.
 *
 * @param <T> The result type.
 * @hide
</T> */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun interface ResponseParser<T> {

    @Throws(Exception::class)
    public fun parseResponse(status: Int, headers: Map<String, String>, responseBody: String?): T
}
