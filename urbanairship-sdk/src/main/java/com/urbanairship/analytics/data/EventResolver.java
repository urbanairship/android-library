/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.Logger;
import com.urbanairship.UrbanAirshipProvider;
import com.urbanairship.UrbanAirshipResolver;
import com.urbanairship.analytics.Event;
import com.urbanairship.util.UAStringUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Performs event database operations.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EventResolver extends UrbanAirshipResolver {

    /**
     * Default sort order that sorts the events in ASCENDING order by age
     */
    public static final String ASCENDING_SORT_ORDER = EventsStorage.Events._ID + " ASC";
    private final Uri uri;

    public EventResolver(Context context) {
        super(context);
        this.uri = UrbanAirshipProvider.getEventsContentUri(context);
    }

    /**
     * Gets a map of event data
     *
     * @param count Number of events to return, starts from the oldest to the newest.
     * @return Map of event id to event data
     */
    @NonNull
    Map<String, String> getEvents(int count) {
        HashMap<String, String> events = new HashMap<>(count);

        String[] columns = new String[] {
                EventsStorage.Events.COLUMN_NAME_EVENT_ID,
                EventsStorage.Events.COLUMN_NAME_DATA
        };

        Uri eventsUri = uri.buildUpon().appendQueryParameter(UrbanAirshipProvider.QUERY_PARAMETER_LIMIT, String.valueOf(count)).build();

        Cursor cursor = query(eventsUri, columns, null, null, ASCENDING_SORT_ORDER);

        if (cursor == null) {
            return events;
        }

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            events.put(cursor.getString(0), cursor.getString(1));
            cursor.moveToNext();
        }

        cursor.close();

        return events;
    }

    /**
     * Deletes all events.
     */
    void deleteAllEvents() {
        delete(uri, null, null);
    }

    /**
     * Delete a set of events
     *
     * @param eventIds Ids of the events to delete
     * @return <code>true</code> if any events were deleted, otherwise <code>false</code>
     */
    boolean deleteEvents(Set<String> eventIds) {
        if (eventIds == null || eventIds.size() == 0) {
            Logger.verbose("EventsStorage - Nothing to delete. Returning.");
            return false;
        }

        int numOfEventIds = eventIds.size();
        String inStatement = repeat("?", numOfEventIds, ", ");
        int deleted = delete(uri, EventsStorage.Events.COLUMN_NAME_EVENT_ID + " IN ( " + inStatement + " )",
                eventIds.toArray(new String[numOfEventIds]));

        return deleted > 0;
    }

    private static String repeat(String repeater, int times, String separator) {
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
     * Gets the oldest session id in the
     * database
     *
     * @return The oldest session id if exists, null otherwise
     */
    private String getOldestSessionId() {
        String[] columns = new String[] { EventsStorage.Events.COLUMN_NAME_SESSION_ID };
        Uri eventsUri = uri.buildUpon().appendQueryParameter(UrbanAirshipProvider.QUERY_PARAMETER_LIMIT, "1").build();

        Cursor cursor = query(eventsUri, columns, null, null, ASCENDING_SORT_ORDER);

        if (cursor == null) {
            Logger.error("EventsStorage - Unable to query database.");
            return null;
        }

        String sessionId = null;
        if (cursor.moveToFirst()) {
            sessionId = cursor.getString(0);
        }
        cursor.close();

        return sessionId;
    }

    /**
     * Gets the current event count in the database
     *
     * @return The current event count
     */
    int getEventCount() {
        Integer result = null;
        String[] columns = new String[] { "COUNT(*) as _cnt" };
        Cursor cursor = query(uri, columns, null, null, null);

        if (cursor == null) {
            Logger.error("EventsStorage - Unable to query events database.");
            return -1;
        }

        if (cursor.moveToFirst()) {
            result = cursor.getInt(0);
        }

        cursor.close();

        return result == null ? -1 : result;
    }

    /**
     * Returns the sum of the events data fields in bytes
     *
     * @return The current size of the database in bytes
     */
    int getDatabaseSize() {
        Integer result = null;
        String[] columns = new String[] { "SUM(" + EventsStorage.Events.COLUMN_NAME_EVENT_SIZE + ") as _size" };
        Cursor cursor = query(uri, columns, null, null, null);

        if (cursor == null) {
            Logger.error("EventsStorage - Unable to query events database.");
            return -1;
        }

        if (cursor.moveToFirst()) {
            result = cursor.getInt(0);
        }

        cursor.close();

        return result == null ? -1 : result;
    }


    /**
     * Inserts an event into the database.
     *
     * @param event The event.
     * @param sessionId The session ID.
     */
    void insertEvent(Event event, String sessionId) {
        String eventPayload = event.createEventPayload(sessionId);

        ContentValues values = new ContentValues();
        values.put(EventsStorage.Events.COLUMN_NAME_TYPE, event.getType());
        values.put(EventsStorage.Events.COLUMN_NAME_EVENT_ID, event.getEventId());
        values.put(EventsStorage.Events.COLUMN_NAME_DATA, eventPayload);
        values.put(EventsStorage.Events.COLUMN_NAME_TIME, event.getTime());
        values.put(EventsStorage.Events.COLUMN_NAME_SESSION_ID, sessionId);
        values.put(EventsStorage.Events.COLUMN_NAME_EVENT_SIZE, eventPayload.length());

        insert(uri, values);
    }

    /**
     * Trims the database down to the specified size.
     *
     * @param maxDatabaseSize The max db size in bytes.
     */
    void trimDatabase(int maxDatabaseSize) {
        while (getDatabaseSize() > maxDatabaseSize) {

            String sessionId = getOldestSessionId();
            if (UAStringUtil.isEmpty(sessionId))  {
                break;
            }

            Logger.info("Event database size exceeded. Deleting oldest session: " + sessionId);

            int deleted = delete(uri, EventsStorage.Events.COLUMN_NAME_SESSION_ID + " = ?", new String[] { sessionId });

            if (deleted > 0) {
                Logger.debug("EventsStorage - Deleted " + deleted + " rows with session ID " + sessionId);
            } else {
                break;
            }
        }
    }
}
