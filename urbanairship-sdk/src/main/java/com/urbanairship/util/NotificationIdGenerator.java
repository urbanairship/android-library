/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.IntRange;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * An auto-incrementing notification ID generator.
 */
public class NotificationIdGenerator {

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
        editor.apply();
    }

    private static int getInt(String key, int defValue) {
        return getPreferences().getInt(key, defValue);
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
    public static void setRange(@IntRange(from = 0, to = MAX_RANGE) int newRange) {

        if (newRange > MAX_RANGE) {
            Logger.error("The maximum number of notifications allowed is " + MAX_RANGE + ". Limiting alert id range to conform.");
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
            Logger.verbose("NotificationIdGenerator - Incrementing notification ID count");
            putInt(NEXT_ID_KEY, nextId);
        }

        //in which case, cycle
        else {
            Logger.verbose("NotificationIdGenerator - Resetting notification ID count");
            putInt(NEXT_ID_KEY, start);
        }

        Logger.verbose("NotificationIdGenerator - Notification ID: " + id);

        return id;
    }

}
