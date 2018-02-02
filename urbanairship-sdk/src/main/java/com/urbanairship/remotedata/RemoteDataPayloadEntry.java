/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.json.JsonMap;

/**
 * Helper class for reading and writing RemoteDataPayloads to the data store.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataPayloadEntry {

    static final String TABLE_NAME = "payloads";

    static final String COLUMN_NAME_ID = "id";

    // The payload type
    static final String COLUMN_NAME_TYPE = "type";

    // The timestamp as a long integer of milliseconds
    static final String COLUMN_NAME_TIMESTAMP = "time";

    // Arbitrary JSON-serialized data
    static final String COLUMN_NAME_DATA = "data";

    private long id = -1;
    private boolean isDirty = false;

    /**
     * The type.
     */
    public final String type;
    /**
     * The timestamp.
     */
    public final long timestamp;
    /**
     * The data as a JSON string
     */
    public final String data;


    /**
     * RemoteDataPayloadEntry constructor.
     *
     * @param payload A RemoteDataPayload
     */
    RemoteDataPayloadEntry(RemoteDataPayload payload) {
        this(payload.getType(),payload.getTimestamp(), payload.getData());
    }

    /**
     * RemoteDataPayloadEntry constructor.
     *
     * @param type The type.
     * @param timestamp The timestamp.
     * @param data The data.
     */
    RemoteDataPayloadEntry(String type, long timestamp, JsonMap data) {
        this.type = type;
        this.timestamp = timestamp;
        this.data = data.toString();
    }

    /**
     * RemoteDataPayloadEntry constructor.
     *
     * @param cursor The cursor from which to read the data.
     */
    RemoteDataPayloadEntry(Cursor cursor) {
        this.type = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE));
        this.timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP));
        this.data = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA));
        this.id = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_ID));
    }

    /**
     * Saves to a database.
     *
     * @param database The database.
     * @return A boolean indicating success.
     */
    @WorkerThread
    boolean save(SQLiteDatabase database) {
        if (id == -1) {
            ContentValues value = new ContentValues();
            value.put(COLUMN_NAME_TYPE, type);
            value.put(COLUMN_NAME_TIMESTAMP, timestamp);
            value.put(COLUMN_NAME_DATA, data);

            id = database.insert(TABLE_NAME, null, value);
            if (id != -1) {
                isDirty = false;
                return true;
            }
        } else if (isDirty) {
            ContentValues value = new ContentValues();

            if (database.updateWithOnConflict(TABLE_NAME, value, COLUMN_NAME_ID + " = ?", new String[] { String.valueOf(id) }, SQLiteDatabase.CONFLICT_REPLACE) != 0) {
                isDirty = false;
                return true;
            } else {
                return false;
            }
        }

        return true;
    }
}
