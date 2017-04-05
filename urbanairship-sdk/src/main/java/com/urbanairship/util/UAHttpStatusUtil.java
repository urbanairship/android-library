/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;

/**
 * Range-check utility class for Http status codes
 */
public class UAHttpStatusUtil {

    /**
     * Check if the status code is in the success range 2xx
     *
     * @param statusCode The HTTP status code integer
     * @return <code>true</code> if it is 2xx, <code>false</code> otherwise
     */
    public static boolean inSuccessRange(int statusCode) {
        return statusCode / 100 == 2;
    }

    /**
     * Check if the status code is in the redirection range 3xx
     *
     * @param statusCode The HTTP status code integer
     * @return <code>true</code> if it is 3xx, <code>false</code> otherwise
     */
    public static boolean inRedirectionRange(int statusCode) {
        return statusCode / 100 == 3;
    }

    /**
     * Check if the status code is in the client error range 4xx
     *
     * @param statusCode The HTTP status code integer
     * @return <code>true</code> if it is 4xx, <code>false</code> otherwise
     */
    public static boolean inClientErrorRange(int statusCode) {
        return statusCode / 100 == 4;
    }

    /**
     * Check if the status code is in the server error range 5xx
     *
     * @param statusCode The HTTP status code integer
     * @return <code>true</code> if it is 5xx, <code>false</code> otherwise
     */
    public static boolean inServerErrorRange(int statusCode) {
        return statusCode / 100 == 5;
    }
}
