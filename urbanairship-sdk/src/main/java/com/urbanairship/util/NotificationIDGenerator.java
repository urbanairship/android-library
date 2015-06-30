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

import android.content.Context;
import android.content.SharedPreferences;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * An auto-incrementing notification ID generator.
 */
public class NotificationIDGenerator {

    private static final String SHARED_PREFERENCES_FILE = "com.urbanairship.notificationidgenerator";

    private static final String NEXT_ID_KEY = "count";
    private static final int MAX_RANGE = 50;

    private static int start = 1000;
    private static int range = 40; //Android allows a maximum of 50 notifications per package (undocumented)

    private static SharedPreferences getPreferences() {
        Context appContext = UAirship.getApplicationContext();
        return appContext.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
    }

    private static void putInt(String key, int value) {
        SharedPreferences prefs = getPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(key, value);
        editor.commit();
    }

    private static int getInt(String key, int defvalue) {
        return getPreferences().getInt(key, defvalue);
    }

    //implicitly resets the count

    /**
     * Set the count and start value.
     *
     * @param value The integer value
     */
    public static void setStart(int value) {
        putInt(NEXT_ID_KEY, value);
        start = value;
    }

    //implicitly resets the count

    /**
     * Set the number of notifications to display (max range). Implicitly resets
     * the current id to {@link #getStart()}.
     *
     * @param newRange The number of notifications to display
     */
    public static void setRange(int newRange) {

        if (newRange > MAX_RANGE) {
            Logger.error("The maximum numer of notifications allowed is " + MAX_RANGE + ". Limiting alert id range to conform.");
            newRange = MAX_RANGE;
        }

        putInt(NEXT_ID_KEY, start);
        range = newRange;
    }

    /**
     * Get the start value.
     *
     * @return The int start.
     */
    public static int getStart() {
        return start;
    }

    /**
     * Get the range.
     *
     * @return The int range.
     */
    public static int getRange() {
        return range;
    }

    /**
     * Store the next ID.
     *
     * @return The int next ID.
     */
    public static int nextID() {

        //get the next id from the shared prefs
        int id = getInt(NEXT_ID_KEY, start);

        //and write the next value back out

        //store a new next id: increment by one, unless we're already at the maximum
        int nextId = ++id;
        if (nextId < start + range) {
            Logger.verbose("NotificationIDGenerator - Incrementing notification ID count");
            putInt(NEXT_ID_KEY, nextId);
        }

        //in which case, cycle
        else {
            Logger.verbose("NotificationIDGenerator - Resetting notification ID count");
            putInt(NEXT_ID_KEY, start);
        }

        Logger.verbose("NotificationIDGenerator - Notification ID: " + id);

        return id;
    }

}
