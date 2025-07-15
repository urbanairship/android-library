/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.urbanairship.UALog;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DataManager;
import com.urbanairship.util.UAStringUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * {@link DataManager} class for remote data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataStore extends DataManager {

    private static final String TABLE_NAME = "payloads";

    private static final String COLUMN_NAME_ID = "id";

    // The payload type
    private static final String COLUMN_NAME_TYPE = "type";

    // The timestamp as a long integer of milliseconds
    private static final String COLUMN_NAME_TIMESTAMP = "time";

    // Arbitrary JSON-serialized data
    private static final String COLUMN_NAME_DATA = "data";

    // Metadata JSON-serialized data.
    private static final String COLUMN_NAME_METADATA = "metadata";

    /**
     * The database version.
     */
    private static final int DATABASE_VERSION = 2;

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
        UALog.d("Creating database");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NAME_TYPE + " TEXT,"
                + COLUMN_NAME_TIMESTAMP + " INTEGER,"
                + COLUMN_NAME_DATA + " TEXT,"
                + COLUMN_NAME_METADATA + " TEXT"
                + ");");
    }

    @Override
    protected void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1:
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_NAME_METADATA + " TEXT;");
                break;
            default:
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
                onCreate(db);
        }
    }

    @Override
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        super.onDowngrade(db, oldVersion, newVersion);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    /**
     * Saves a list of RemoteDataPayloads.
     *
     * @param payloads The payloads.
     * @return A boolean indicating success.
     */
    public boolean savePayloads(@NonNull Set<RemoteDataPayload> payloads) {
        if (payloads.isEmpty()) {
            return true;
        }

        final SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            UALog.e("RemoteDataStore - Unable to save remote data payloads.");
            return false;
        }

        try {
            db.beginTransaction();

            for (RemoteDataPayload payload : payloads) {
                ContentValues value = new ContentValues();
                value.put(COLUMN_NAME_TYPE, payload.getType());
                value.put(COLUMN_NAME_TIMESTAMP, payload.getTimestamp());
                value.put(COLUMN_NAME_DATA, payload.getData().toString());
                if (payload.getRemoteDataInfo() != null) {
                    value.put(COLUMN_NAME_METADATA, payload.getRemoteDataInfo().toJsonValue().toString());
                } else {
                    value.put(COLUMN_NAME_METADATA, JsonValue.NULL.toString());
                }

                try {
                    long id = db.insert(TABLE_NAME, null, value);
                    if (id == -1) {
                        db.endTransaction();
                        return false;
                    }
                } catch (SQLException e) {
                    UALog.e(e, "RemoteDataStore - Unable to save remote data payload.");
                }
            }

            db.setTransactionSuccessful();
            db.endTransaction();
        } catch (SQLException e) {
            UALog.e(e, "RemoteDataStore - Unable to save remote data payloads.");
            return false;
        }

        return true;
    }

    /**
     * Gets all payloads.
     *
     * @return A List of RemoteDataPayload.
     */
    @NonNull
    public Set<RemoteDataPayload> getPayloads() {
        return getPayloads(null);
    }

    /**
     * Gets all payloads of the specified types.
     *
     * @param types The specified types.
     * @return A List of RemoteDataPayload.
     */
    @NonNull
    Set<RemoteDataPayload> getPayloads(@Nullable Collection<String> types) {
        Cursor cursor = null;

        try {
            if (types == null) {
                cursor = this.query(TABLE_NAME, null,
                        null, null, null);
            } else {
                String where = COLUMN_NAME_TYPE + " IN ( " + UAStringUtil.repeat("?", types.size(), ", ") + " )";

                cursor = this.query(TABLE_NAME, null,
                        where, types.toArray(new String[0]), null);
            }

            if (cursor == null) {
                return Collections.emptySet();
            }

            return generatePayloadEntries(cursor);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Deletes all payloads.
     *
     * @return Number of payloads deleted.
     */
    int deletePayloads() {
        return delete(TABLE_NAME, null, null);
    }

    /**
     * Helper method to generate payload entries from a a cursor.
     *
     * @param cursor The cursor.
     * @return A list of RemoteDataPayloadEntry objects.
     */
    @NonNull
    private Set<RemoteDataPayload> generatePayloadEntries(@NonNull Cursor cursor) {
        cursor.moveToFirst();

        Set<RemoteDataPayload> entries = new HashSet<>();
        while (!cursor.isAfterLast()) {

            try {
                RemoteDataPayload payload = new RemoteDataPayload(
                        cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE)),
                        cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP)),
                        JsonValue.parseString(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA))).optMap(),
                        parseRemoteDataInfo(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_METADATA)))
                );
                entries.add(payload);
            } catch (IllegalArgumentException | JsonException e) {
                UALog.e(e, "RemoteDataStore - failed to retrieve payload");
            }

            cursor.moveToNext();
        }

        return entries;
    }

    @Nullable
    private RemoteDataInfo parseRemoteDataInfo(@Nullable String json) {
        if (json == null) {
            return null;
        }

        try {
            JsonValue jsonValue = JsonValue.parseString(json);
            if (jsonValue.isNull()) {
                return null;
            }
            return new RemoteDataInfo(jsonValue);
        } catch (JsonException e) {
            // Can happen during migration
            return null;
        }
    }
}
