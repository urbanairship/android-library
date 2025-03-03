/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.RestrictTo
import com.urbanairship.UALog.d
import com.urbanairship.UALog.e
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.DataManager
import com.urbanairship.util.UAStringUtil

/**
 * [DataManager] class for remote data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataStore
/**
 * RemoteDataStore constructor.
 *
 * @param context The app context.
 * @param appKey The app key.
 * @param dbName The database name.
 */
public constructor(context: Context, appKey: String, dbName: String) :
    DataManager(context, appKey, dbName, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        d("Creating database")
        db.execSQL(
            ("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_NAME_TYPE + " TEXT,"
                    + COLUMN_NAME_TIMESTAMP + " INTEGER,"
                    + COLUMN_NAME_DATA + " TEXT,"
                    + COLUMN_NAME_METADATA + " TEXT"
                    + ");")
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_NAME_METADATA + " TEXT;")
            else -> {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
                onCreate(db)
            }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        super.onDowngrade(db, oldVersion, newVersion)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
        onCreate(db)
    }

    /**
     * Saves a list of RemoteDataPayloads.
     *
     * @param payloads The payloads.
     * @return A boolean indicating success.
     */
    public fun savePayloads(payloads: MutableSet<RemoteDataPayload>): Boolean {
        if (payloads.isEmpty()) {
            return true
        }

        val db = getWritableDatabase()
        if (db == null) {
            e("RemoteDataStore - Unable to save remote data payloads.")
            return false
        }

        try {
            db.beginTransaction()

            for (payload in payloads) {
                val value = ContentValues()
                value.put(COLUMN_NAME_TYPE, payload.type)
                value.put(COLUMN_NAME_TIMESTAMP, payload.timestamp)
                value.put(COLUMN_NAME_DATA, payload.data.toString())
                if (payload.remoteDataInfo != null) {
                    value.put(COLUMN_NAME_METADATA, payload.remoteDataInfo.toJsonValue().toString())
                } else {
                    value.put(COLUMN_NAME_METADATA, JsonValue.NULL.toString())
                }

                try {
                    val id = db.insert(TABLE_NAME, null, value)
                    if (id == -1L) {
                        db.endTransaction()
                        return false
                    }
                } catch (e: SQLException) {
                    e(e, "RemoteDataStore - Unable to save remote data payload.")
                }
            }

            db.setTransactionSuccessful()
            db.endTransaction()
        } catch (e: SQLException) {
            e(e, "RemoteDataStore - Unable to save remote data payloads.")
            return false
        }

        return true
    }

    val payloads: MutableSet<RemoteDataPayload?>
        /**
         * Gets all payloads.
         *
         * @return A List of RemoteDataPayload.
         */
        get() {
            return getPayloads(null)
        }

    /**
     * Gets all payloads.
     *
     * @return A List of RemoteDataPayload.
     */
    public fun getPayloads(): MutableSet<RemoteDataPayload?> {
        return getPayloads(null)
    }

    /**
     * Gets all payloads of the specified types.
     *
     * @param types The specified types.
     * @return A List of RemoteDataPayload.
     */
    public fun getPayloads(types: MutableCollection<String?>?): MutableSet<RemoteDataPayload?> {
        var cursor: Cursor? = null

        try {
            if (types == null) {
                cursor = this.query(
                    TABLE_NAME, null,
                    null, null, null
                )
            } else {
                val where: String =
                    COLUMN_NAME_TYPE + " IN ( " + UAStringUtil.repeat("?", types.size, ", ") + " )"

                cursor = this.query(
                    TABLE_NAME, null,
                    where, types.toTypedArray<String?>(), null
                )
            }

            if (cursor == null) {
                return mutableSetOf<RemoteDataPayload?>()
            }

            return generatePayloadEntries(cursor)
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
    }

    /**
     * Deletes all payloads.
     *
     * @return Number of payloads deleted.
     */
    public fun deletePayloads(): Int {
        return delete(TABLE_NAME, null, null)
    }

    /**
     * Helper method to generate payload entries from a a cursor.
     *
     * @param cursor The cursor.
     * @return A list of RemoteDataPayloadEntry objects.
     */
    private fun generatePayloadEntries(cursor: Cursor): MutableSet<RemoteDataPayload?> {
        cursor.moveToFirst()

        val entries: MutableSet<RemoteDataPayload?> = HashSet<RemoteDataPayload?>()
        while (!cursor.isAfterLast()) {
            try {
                val payload = RemoteDataPayload(
                    cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP)),
                    JsonValue.parseString(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA)))
                        .optMap(),
                    parseRemoteDataInfo(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_METADATA)))
                )
                entries.add(payload)
            } catch (e: IllegalArgumentException) {
                e(e, "RemoteDataStore - failed to retrieve payload")
            } catch (e: JsonException) {
                e(e, "RemoteDataStore - failed to retrieve payload")
            }

            cursor.moveToNext()
        }

        return entries
    }

    private fun parseRemoteDataInfo(json: String?): RemoteDataInfo? {
        if (json == null) {
            return null
        }

        try {
            val jsonValue = JsonValue.parseString(json)
            if (jsonValue.isNull()) {
                return null
            }
            return RemoteDataInfo(jsonValue)
        } catch (e: JsonException) {
            // Can happen during migration
            return null
        }
    }

    public companion object {
        private const val TABLE_NAME = "payloads"

        private const val COLUMN_NAME_ID = "id"

        // The payload type
        private const val COLUMN_NAME_TYPE = "type"

        // The timestamp as a long integer of milliseconds
        private const val COLUMN_NAME_TIMESTAMP = "time"

        // Arbitrary JSON-serialized data
        private const val COLUMN_NAME_DATA = "data"

        // Metadata JSON-serialized data.
        private const val COLUMN_NAME_METADATA = "metadata"

        /**
         * The database version.
         */
        private const val DATABASE_VERSION = 2
    }
}
