/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.RestrictTo

/**
 * Range-check utility class for Http status codes
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object UAHttpStatusUtil {

    /**
     * Check if the status code is in the success range 2xx
     *
     * @param statusCode The HTTP status code integer
     * @return `true` if it is 2xx, `false` otherwise
     */
    public fun inSuccessRange(statusCode: Int): Boolean {
        return statusCode / 100 == 2
    }

    /**
     * Check if the status code is in the redirection range 3xx
     *
     * @param statusCode The HTTP status code integer
     * @return `true` if it is 3xx, `false` otherwise
     */
    public fun inRedirectionRange(statusCode: Int): Boolean {
        return statusCode / 100 == 3
    }

    /**
     * Check if the status code is in the client error range 4xx
     *
     * @param statusCode The HTTP status code integer
     * @return `true` if it is 4xx, `false` otherwise
     */
    public fun inClientErrorRange(statusCode: Int): Boolean {
        return statusCode / 100 == 4
    }

    /**
     * Check if the status code is in the server error range 5xx
     *
     * @param statusCode The HTTP status code integer
     * @return `true` if it is 5xx, `false` otherwise
     */
    public fun inServerErrorRange(statusCode: Int): Boolean {
        return statusCode / 100 == 5
    }
}
