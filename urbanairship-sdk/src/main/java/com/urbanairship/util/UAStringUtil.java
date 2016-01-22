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

import java.util.Collection;
import java.util.Iterator;

/**
 * A class containing utility methods related to strings.
 */
public abstract class UAStringUtil {

    /**
     * Builds a string.
     *
     * @param repeater The string to build.
     * @param times The number of times to append the string.
     * @param separator The separator string.
     * @return The new string.
     */
    public static String repeat(String repeater, int times, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(repeater);
            if (i + 1 != times) {
                builder.append(separator);
            }
        }
        return builder.toString();
    }

    /**
     * Checks if the string is empty.
     *
     * @param stringToCheck The string to check.
     * @return <code>true</code> if the string is null, <code>false</code> otherwise.
     */
    public static boolean isEmpty(String stringToCheck) {
        return stringToCheck == null || stringToCheck.length() == 0;
    }

    /**
     * Checks if the strings are equal.
     *
     * @param firstString The first string.
     * @param secondString The second string.
     * @return <code>true</code> if the strings are equal, <code>false</code> otherwise.
     */
    public static boolean equals(String firstString, String secondString) {
        return firstString == null ? secondString == null : firstString.equals(secondString);
    }

    /**
     * Append a collection of strings and delimiter.
     *
     * @param c A collection of strings.
     * @param delimiter A delimiter string.
     * @return The new string.
     */
    public static String join(Collection<String> c, String delimiter) {
        if (c == null || delimiter == null) {
            throw new IllegalArgumentException("Collections and delimiters given to join cannot be null!");
        }
        StringBuilder builder = new StringBuilder("");
        Iterator<String> iter = c.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
            if (iter.hasNext()) {
                builder.append(delimiter);
            }
        }
        return builder.toString();
    }
}
