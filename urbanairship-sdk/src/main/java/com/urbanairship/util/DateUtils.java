/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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


import android.support.annotation.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtils {

    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final SimpleDateFormat ALT_ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    static {
        ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        ALT_ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private DateUtils() {}

    /**
     * Parses an ISO 8601 timestamp.
     *
     * @param timeStamp The ISO time stamp string.
     * @return The time in milliseconds since Jan. 1, 1970, midnight GMT or -1 if the timestamp was unable
     * to be parsed.
     * @throws java.text.ParseException if the timestamp was unable to be parsed.
     */
    public static long parseIso8601(@NonNull String timeStamp) throws ParseException {
        //noinspection ConstantConditions
        if (timeStamp == null) {
            throw new ParseException("Unable to parse null timestamp", -1);
        }

        try {
            return ISO_DATE_FORMAT.parse(timeStamp).getTime();
        } catch (ParseException ignored) {
            return ALT_ISO_DATE_FORMAT.parse(timeStamp).getTime();
        }
    }

    /**
     * Parses an ISO 8601 timestamp.
     *
     * @param timeStamp The ISO time stamp string.
     * @param defaultValue The default value
     * @return The time in milliseconds since Jan. 1, 1970, midnight GMT or the default value
     * if the timestamp was unable to be parsed.
     */
    public static long parseIso8601(@NonNull String timeStamp, long defaultValue) {
        try {
            return parseIso8601(timeStamp);
        } catch (ParseException ignored) {
            return defaultValue;
        }
    }

    /**
     * Creates an ISO 8601 formatted time stamp.
     *
     * @param milliseconds The time in milliseconds since Jan. 1, 1970, midnight GMT.
     * @return An ISO 8601 formatted time stamp.
     */
    public static String createIso8601TimeStamp(long milliseconds) {
        return ISO_DATE_FORMAT.format(new Date(milliseconds));
    }
}
