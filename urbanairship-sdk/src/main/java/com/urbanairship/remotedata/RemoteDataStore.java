/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.util.DataManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link DataManager} class for remote data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataStore extends DataManager {

    /**
     * The database version.
     */
    private static final int DATABASE_VERSION = 1;

    /**
     * Query for retrieving payloads.
     */
    private static final String GET_PAYLOADS_QUERY = "SELECT * FROM " + RemoteDataPayloadEntry.TABLE_NAME;

    /**
     * RemoteDataStore constructor.
     *
     * @param context The app context.
     * @param appKey The app key.
     * @param dbName The database name.
     */
    public RemoteDataStore(@NonNull Context context, @NonNull String appKey, @NonNull String dbName) {
        super(context, appKey, dbName, DATABASE_VERSION);
    }

    @Override
    protected void onCreate(@NonNull SQLiteDatabase db) {
        Logger.debug("RemoteDataStore - Creating database");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + RemoteDataPayloadEntry.TABLE_NAME + " ("
                + RemoteDataPayloadEntry.COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + RemoteDataPayloadEntry.COLUMN_NAME_TYPE + " TEXT,"
                + RemoteDataPayloadEntry.COLUMN_NAME_TIMESTAMP + " INTEGER,"
                + RemoteDataPayloadEntry.COLUMN_NAME_DATA + " TEXT"
                + ");");
    }

    /**
     * Saves a list of RemoteDataPayloads.
     *
     * @param payloads The payloads.
     * @return A boolean indicating success.
     */
    public boolean savePayloads(@NonNull List<RemoteDataPayload> payloads) {
        if (payloads.isEmpty()) {
            return true;
        }

        final SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            Logger.error("RemoteDataStore - Unable to save remote data payloads.");
            return false;
        }

        db.beginTransaction();

        for (RemoteDataPayload payload : payloads) {
            RemoteDataPayloadEntry entry = new RemoteDataPayloadEntry(payload);
            if (!entry.save(db)) {
                db.endTransaction();
                return false;
            }
        }

        db.setTransactionSuccessful();
        db.endTransaction();

        return true;
    }

    public boolean savePayload(@NonNull RemoteDataPayload payload) {
        final SQLiteDatabase db = getWritableDatabase();

        if (db == null) {
            Logger.error("RemoteDataStore - Unable to save remote data payload.");
            return false;
        }

        RemoteDataPayloadEntry entry = new RemoteDataPayloadEntry(payload);
        return entry.save(db);
    }

    /**
     * Gets all payloads.
     *
     * @return A List of RemoteDataPayload.
     */
    public List<RemoteDataPayload> getPayloads() {
        return payloadsForEntries(getPayloadEntries());
    }

    /**
     * Gets the payload for a specific type, or <code>null</code> if none could be found.
     *
     * @param type A payload type.
     * @return A RemoteDataPayload, or <code>null</code> if none could be found.
     */
    public RemoteDataPayload getPayload(String type) {
        return payloadForEntry(getPayloadEntry(type));
    }

    /**
     * Deletes all payloads.
     *
     * @return A boolean indicating success.
     */
    public boolean deletePayloads() {
        boolean success = delete(RemoteDataPayloadEntry.TABLE_NAME, null, null) >= 0;
        if (!success) {
            Logger.warn("RemoteDataStore - failed to delete payloads");
        }
        return success;
    }

    /**
     * Converts payload entries to payload model objects.
     *
     * @param entries A list of payload entries.
     * @return A list of payloads.
     */
    private List<RemoteDataPayload> payloadsForEntries(List<RemoteDataPayloadEntry> entries) {
        ArrayList<RemoteDataPayload> payloads = new ArrayList<>();
        for (RemoteDataPayloadEntry entry : entries) {
            RemoteDataPayload payload = payloadForEntry(entry);
            if (payload != null) {
                payloads.add(payload);
            }
        }

        return payloads;
    }

    /**
     * Converts a payload entry to a payload model object.
     *
     * @param entry A payload entry, or <code>null</code> if one could not be constructed.
     * @return A payload, or <code>null</code> if one could not be constructed.
     */
    private RemoteDataPayload payloadForEntry(RemoteDataPayloadEntry entry) {
        if (entry == null) {
            return null;
        }

        RemoteDataPayload payload = null;
        try {
            payload = new RemoteDataPayload(entry);
        } catch (JsonException e) {
            Logger.error("Unable to construct RemoteDataPayload", e);
        }

        return payload;
    }

    /**
     * Helper method for getting payload entries from the database.
     *
     * @return A list of RemoteDataPayloadEntry
     */
    private List<RemoteDataPayloadEntry> getPayloadEntries() {
        String query = GET_PAYLOADS_QUERY;
        Cursor cursor = rawQuery(query, null);
        if (cursor == null) {
            return Collections.emptyList();
        }

        List<RemoteDataPayloadEntry> entries = generatePayloadEntries(cursor);
        cursor.close();
        return entries;
    }

    /**
     * Helper method for getting a payload entry by type from the database.
     *
     * @return A RemoteDataPayloadEntry or <code>null</code> if none could be found.
     */
    private RemoteDataPayloadEntry getPayloadEntry(String type) {
        String query = GET_PAYLOADS_QUERY + " WHERE " + RemoteDataPayloadEntry.COLUMN_NAME_TYPE + " = ?";
        Cursor cursor = rawQuery(query, new String[]{type});
        if (cursor == null) {
            return null;
        }

        List<RemoteDataPayloadEntry> entries = generatePayloadEntries(cursor);
        cursor.close();
        return entries.size() > 0 ? entries.get(0) : null;
    }

    /**
     * Helper method to generate payload entries from a a cursor.
     *
     * @param cursor The cursor.
     * @return A list of RemoteDataPayloadEntry objects.
     */
    @NonNull
    private List<RemoteDataPayloadEntry> generatePayloadEntries(@NonNull Cursor cursor) {
        cursor.moveToFirst();

        List<RemoteDataPayloadEntry> entries = new ArrayList<>();
        while (!cursor.isAfterLast()) {
            RemoteDataPayloadEntry entry = new RemoteDataPayloadEntry(cursor);
            if (entry != null) {
                entries.add(entry);
            }
            cursor.moveToNext();
        }

        return entries;
    }
}
