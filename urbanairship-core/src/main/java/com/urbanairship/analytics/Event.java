/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.urbanairship.UALog;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.Network;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * This abstract class encapsulates analytics events.
 */
public abstract class Event {

    private final String eventId; //a UUID string
    protected final long timeMilliseconds;

    //top level event fields
    static final String TYPE_KEY = "type";
    static final String TIME_KEY = "time";
    static final String DATA_KEY = "data";
    static final String EVENT_ID_KEY = "event_id";

    //event data fields
    static final String SESSION_ID_KEY = "session_id";
    static final String CONNECTION_TYPE_KEY = "connection_type";
    static final String CONNECTION_SUBTYPE_KEY = "connection_subtype";
    static final String CARRIER_KEY = "carrier";
    static final String PUSH_ID_KEY = "push_id";
    static final String METADATA_KEY = "metadata";
    static final String TIME_ZONE_KEY = "time_zone";
    static final String DAYLIGHT_SAVINGS_KEY = "daylight_savings";
    static final String OS_VERSION_KEY = "os_version";
    static final String LIB_VERSION_KEY = "lib_version";
    static final String PACKAGE_VERSION_KEY = "package_version";
    static final String LAST_METADATA_KEY = "last_metadata";

    @IntDef({ LOW_PRIORITY, NORMAL_PRIORITY, HIGH_PRIORITY })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Priority {}

    /**
     * Low priority event.
     */
    public static final int LOW_PRIORITY = 0;

    /**
     * Normal priority event.
     */
    public static final int NORMAL_PRIORITY = 1;

    /**
     * High priority event.
     */
    public static final int HIGH_PRIORITY = 2;

    /**
     * Constructor for Event.
     *
     * @param timeMilliseconds The time of the event in milliseconds.
     */
    public Event(long timeMilliseconds) {
        this.eventId = UUID.randomUUID().toString();
        this.timeMilliseconds = timeMilliseconds;
    }

    /**
     * Constructor for Event using the current time.
     */
    public Event() {
        this(System.currentTimeMillis());
    }

    /**
     * Returns the UUID associated with the event.
     *
     * @return an Event id String.
     */
    @NonNull
    public String getEventId() {
        return eventId;
    }

    /**
     * Returns the timestamp associated with the event.
     *
     * @return Seconds from the epoch, as a String.
     */
    @NonNull
    public String getTime() {
        return millisecondsToSecondsString(timeMilliseconds);
    }

    /**
     * The event type.
     * @hide
     *
     * @return The event type.
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public abstract EventType getType();

    /**
     * Create the event data.
     *
     * @return The event data.
     * @hide
     */
    @NonNull
    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Deprecated
    public JsonMap getEventData() {
        return JsonMap.EMPTY_MAP;
    }


    /**
     * Create the event data.
     *
     * @param conversionData The conversion data.
     * @return The event data.
     * @hide
     */
    @NonNull
    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonMap getEventData(@NonNull ConversionData conversionData) {
        //noinspection deprecation
        return getEventData();
    }

    /**
     * Returns the connection type.
     *
     * @return The connection type as a String.
     */
    @NonNull
    public String getConnectionType() {

        int type = -1;//not connected

        //determine network connectivity state
        //each of these may return null if there is no connectivity, and this may change at any moment
        //keep a reference, then do a null check before accessing
        ConnectivityManager cm = (ConnectivityManager) UAirship.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null) {
                type = ni.getType();
            }
        }

        String typeString;
        switch (type) {
            case ConnectivityManager.TYPE_MOBILE:
                typeString = "cell";
                break;
            case ConnectivityManager.TYPE_WIFI:
                typeString = "wifi";
                break;
            case /*Connectivity.TYPE_WIMAX: (api level 8)*/ 0x00000006:
                typeString = "wimax";
                break;
            default:
                typeString = "none";
        }

        return typeString;
    }

    /**
     * Returns the connection subtype.
     *
     * @return The connection subtype as a String.
     */
    @NonNull
    public String getConnectionSubType() {
        try {
            //determine network connectivity state
            //each of these may return null if there is no connectivity, and this may change at any moment
            //keep a reference, then do a null check before accessing
            ConnectivityManager cm = (ConnectivityManager) UAirship.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null) {
                    return ni.getSubtypeName();
                }
            }
            return "";
        } catch (ClassCastException e) {
            // https://github.com/urbanairship/android-library/issues/115
            UALog.e("Connection subtype lookup failed", e);
            return "";
        }
    }

    /**
     * Returns the current carrier.
     *
     * @return The carrier as a String.
     */
    @Nullable
    protected String getCarrier() {
        return Network.getCarrier();
    }

    /**
     * Returns the current time zone.
     *
     * @return The time zone as a long.
     */
    protected long getTimezone() {
        TimeZone tz = Calendar.getInstance().getTimeZone();
        return tz.getOffset(System.currentTimeMillis()) / 1000;
    }

    /**
     * Indicates whether it is currently daylight savings time.
     *
     * @return <code>true</code> if it is currently daylight savings time, <code>false</code> otherwise.
     */
    protected boolean isDaylightSavingsTime() {
        return Calendar.getInstance().getTimeZone().inDaylightTime(new Date());
    }

    /**
     * Validates the Event.
     *
     * @return True if valid, false otherwise.
     */
    public boolean isValid() {
        return true;
    }

    /**
     * Helper method to convert milliseconds to a seconds string containing a double.
     *
     * @param milliseconds Milliseconds to convert.
     * @return Seconds as a string containing a double.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static String millisecondsToSecondsString(long milliseconds) {
        return String.format(Locale.US, "%.3f", milliseconds / 1000.0);
    }

    /**
     * The event's send priority.
     *
     * @return The event's send priority.
     */
    @Priority
    public int getPriority() {
        return NORMAL_PRIORITY;
    }

}
