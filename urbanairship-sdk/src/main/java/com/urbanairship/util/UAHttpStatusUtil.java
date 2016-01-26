/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
