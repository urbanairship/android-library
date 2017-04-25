/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.util.DataManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class EventDataManager extends DataManager {

    /**
     * The database that the provider uses as its underlying data store
     */
    private static final String DATABASE_NAME = "ua_analytics.db";

    /**
     * The database version
     */
    private static final int DATABASE_VERSION = 1;

    /**
     * Events table contract
     */
    static final class Events implements BaseColumns {

        // This class cannot be instantiated
        private Events() {}

        /**
         * The table name offered by this provider
         */
        public static final String TABLE_NAME = "events";

        /**
         * Default sort order that sorts the events in ASCENDING order by age
         */
        public static final String ASCENDING_SORT_ORDER = Events._ID + " ASC";


        /*
         * Column definitions
         */

        private static final String COLUMN_NAME_TYPE = "type";
        private static final String COLUMN_NAME_EVENT_ID = "event_id";

        /**
         * Column name for the creation timestamp
         * <P>Type: INTEGER (long from System.currentTimeMillis())</P>
         */
        private static final String COLUMN_NAME_TIME = "time";

        //serialized
        private static final String COLUMN_NAME_DATA = "data";

        private static final String COLUMN_NAME_SESSION_ID = "session_id";
        private static final String COLUMN_NAME_EVENT_SIZE = "event_size";

    }

    EventDataManager(@NonNull Context context, @NonNull String appKey) {
        super(context, appKey, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Logs that the database is being upgraded
        Logger.debug("EventDataManager - Upgrading analytics database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data");

        // Kills the table and existing data
        db.execSQL("DROP TABLE IF EXISTS events");

        // Recreates the database with a new version
        onCreate(db);
    }

    @Override
    protected void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + Events.TABLE_NAME + " ("
                + Events._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + Events.COLUMN_NAME_TYPE + " TEXT,"
                + Events.COLUMN_NAME_EVENT_ID + " TEXT,"
                + Events.COLUMN_NAME_TIME + " INTEGER,"
                + Events.COLUMN_NAME_DATA + " TEXT,"
                + Events.COLUMN_NAME_SESSION_ID + " TEXT,"
                + Events.COLUMN_NAME_EVENT_SIZE + " INTEGER"
                + ");");
    }

    @Override
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop the table and recreate it
        db.execSQL("DROP TABLE IF EXISTS " + Events.TABLE_NAME);
        onCreate(db);
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
                Events.COLUMN_NAME_EVENT_ID,
                Events.COLUMN_NAME_DATA
        };

        Cursor c = query(Events.TABLE_NAME, columns, null, null, Events.ASCENDING_SORT_ORDER, "0, " + count);

        if (c == null) {
            return events;
        }

        c.moveToFirst();
        while (!c.isAfterLast()) {
            events.put(c.getString(0), c.getString(1));
            c.moveToNext();
        }
        c.close();

        return events;
    }

    /**
     * Deletes all events.
     */
    void deleteAllEvents() {
        delete(Events.TABLE_NAME, null, null);
    }

    /**
     * Deletes a single event
     *
     * @param eventId The id of the event to delete
     * @return <code>true</code> if the event was deleted, otherwise <code>false</code>
     */
    boolean deleteEvent(String eventId) {
        return delete(Events.TABLE_NAME, Events.COLUMN_NAME_EVENT_ID + " = ?", new String[] { eventId }) > 0;
    }

    /**
     * Deletes all events of a certain type
     *
     * @param type The type of events to delete
     * @return <code>true</code> if any events where deleted, otherwise <code>false</code>
     */
    boolean deleteEventType(String type) {
        return delete(Events.TABLE_NAME, Events.COLUMN_NAME_TYPE + " = ?", new String[] { type }) > 0;
    }

    /**
     * Delete a set of events
     *
     * @param eventIds Ids of the events to delete
     * @return <code>true</code> if any events where deleted, otherwise <code>false</code>
     */
    boolean deleteEvents(Set<String> eventIds) {
        if (eventIds == null || eventIds.size() == 0) {
            Logger.verbose("EventDataManager - Nothing to delete. Returning.");
            return false;
        }

        int numOfEventIds = eventIds.size();
        String inStatement = repeat("?", numOfEventIds, ", ");
        int deleted = delete(Events.TABLE_NAME, Events.COLUMN_NAME_EVENT_ID + " IN ( " + inStatement + " )",
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
     * @param sessionId Session id to delete
     * @return <code>true</code> if the delete operation was successful,
     * otherwise <code>false</code>
     */
    boolean deleteSession(String sessionId) {
        int deleted = delete(Events.TABLE_NAME, Events.COLUMN_NAME_SESSION_ID + " = ?", new String[] { sessionId });

        if (deleted > 0) {
            Logger.debug("EventDataManager - Deleted " + deleted + " rows with session ID " + sessionId);
            return true;
        }

        return false;
    }

    /**
     * Gets the oldest session id in the
     * database
     *
     * @return The oldest session id if exists, null otherwise
     */
    String getOldestSessionId() {
        String[] columns = new String[] { Events.COLUMN_NAME_SESSION_ID };
        Cursor cursor = query(Events.TABLE_NAME, columns, null, null, Events.ASCENDING_SORT_ORDER, "0, 1");

        if (cursor == null) {
            Logger.error("EventDataManager - Unable to query database.");
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
        Cursor cursor = query(Events.TABLE_NAME, columns, null, null, null, null);

        if (cursor == null) {
            Logger.error("EventDataManager - Unable to query events database.");
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
        String[] columns = new String[] { "SUM(" + Events.COLUMN_NAME_EVENT_SIZE + ") as _size" };
        Cursor cursor = query(Events.TABLE_NAME, columns, null, null, null, null);

        if (cursor == null) {
            Logger.error("EventDataManager - Unable to query events database.");
            return -1;
        }

        if (cursor.moveToFirst()) {
            result = cursor.getInt(0);
            cursor.close();
        }

        return result == null ? -1 : result;
    }


    /**
     * Inserts an event into the database.
     *
     *
     * @return Row Id of the event or -1 if the insert failed.
     */

    /**
     * Inserts an event into the database.
     *
     * @param eventType The event type.
     * @param eventData The event data.
     * @param eventId The event ID.
     * @param sessionId The session ID.
     * @param eventTime The time the event occurred.
     *
     * @return Row Id of the event or -1 if the insert failed.
     */
    long insertEvent(String eventType, String eventData, String eventId, String sessionId, String eventTime) {
        ContentValues values = new ContentValues();
        values.put(EventDataManager.Events.COLUMN_NAME_TYPE, eventType);
        values.put(EventDataManager.Events.COLUMN_NAME_EVENT_ID, eventId);
        values.put(EventDataManager.Events.COLUMN_NAME_DATA, eventData);
        values.put(EventDataManager.Events.COLUMN_NAME_TIME, eventTime);
        values.put(EventDataManager.Events.COLUMN_NAME_SESSION_ID, sessionId);
        values.put(EventDataManager.Events.COLUMN_NAME_EVENT_SIZE, eventData.length());

        return insert(Events.TABLE_NAME, values);
    }

}
