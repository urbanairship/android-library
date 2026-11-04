/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.DataManager
import com.urbanairship.util.repeat
import java.time.Instant

/**
 * [DataManager] class for remote data.
 *
 * @hide
 */
internal class RemoteDataStore (
    context: Context,
    appKey: String,
    dbName: String
) : DataManager(context, appKey, dbName, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        UALog.d("Creating database")
        db.execSQL(
            ("CREATE TABLE IF NOT EXISTS $TABLE_NAME ($COLUMN_NAME_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "$COLUMN_NAME_TYPE TEXT,$COLUMN_NAME_TIMESTAMP INTEGER,$COLUMN_NAME_DATA TEXT," +
                    "$COLUMN_NAME_METADATA TEXT);")
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_NAME_METADATA TEXT;")
            else -> {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
                onCreate(db)
            }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        super.onDowngrade(db, oldVersion, newVersion)
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * Saves a list of RemoteDataPayloads.
     *
     * @param payloads The payloads.
     * @return A boolean indicating success.
     */
    fun savePayloads(payloads: Set<RemoteDataPayload>): Boolean {
        if (payloads.isEmpty()) {
            return true
        }

        val db = getWritableDatabase() ?: run {
            UALog.e("RemoteDataStore - Unable to save remote data payloads.")
            return false
        }

        try {
            db.beginTransaction()

            for ((type, timestamp, data, remoteDataInfo) in payloads) {
                val value = ContentValues()
                value.put(COLUMN_NAME_TYPE, type)
                value.put(COLUMN_NAME_TIMESTAMP, timestamp.toEpochMilli())
                value.put(COLUMN_NAME_DATA, data.toString())
                if (remoteDataInfo != null) {
                    value.put(COLUMN_NAME_METADATA, remoteDataInfo.toJsonValue().toString())
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
                    UALog.e(e, "RemoteDataStore - Unable to save remote data payload.")
                }
            }

            db.setTransactionSuccessful()
            db.endTransaction()
        } catch (e: SQLException) {
            UALog.e(e, "RemoteDataStore - Unable to save remote data payloads.")
            return false
        }

        return true
    }

    /**
     * Gets all payloads.
     */
    val payloads: Set<RemoteDataPayload>
        get() = getPayloads(null)

    /**
     * Gets all payloads of the specified types.
     *
     * @param types The specified types.
     * @param onReadFailed Invoked if the database is unopenable or corrupt, so callers can tell
     * a failure apart from a legitimately empty result. Not invoked for a transient read error
     * (e.g. a locked database), since that doesn't mean the stored data is stale or wrong - but
     * IS invoked if the database is still unopenable after its own retries are exhausted, since
     * by then the condition is no longer transient.
     * @return A List of RemoteDataPayload.
     */
    fun getPayloads(
        types: Collection<String>?,
        onReadFailed: () -> Unit = {}
    ): Set<RemoteDataPayload> {
        val cursor = if (types == null) {
            this.query(TABLE_NAME, null, null, null, null)
        } else {
            val where =
                COLUMN_NAME_TYPE + " IN ( " + "?".repeat(times = types.size, separator = ", ") + " )"

            this.query(
                TABLE_NAME, null, where, types.toTypedArray<String>(), null
            )
        } ?: run {
            onReadFailed()
            return emptySet()
        }

        try {
            return try {
                generatePayloadEntries(cursor)
            } catch (e: SQLiteDatabaseCorruptException) {
                UALog.e(e, "RemoteDataStore - Unable to read remote data payloads.")
                onReadFailed()
                emptySet()
            } catch (e: SQLiteException) {
                // Transient contention (e.g. a locked database) rather than bad data, so this
                // doesn't call onReadFailed() - the store isn't known to be stale or wrong.
                UALog.e(e, "RemoteDataStore - Unable to read remote data payloads.")
                emptySet()
            }
        } finally {
            try {
                cursor.close()
            } catch (e: Throwable) {
                // Any Throwable, not just SQLiteException: left uncaught, it would replace the
                // result or exception the try block above already settled on.
                UALog.e(e, "RemoteDataStore - Unable to close remote data payloads cursor.")
            }
        }
    }

    /**
     * Deletes all payloads.
     *
     * @return Number of payloads deleted.
     */
    fun deletePayloads(): Int {
        return delete(TABLE_NAME, null, null)
    }

    /**
     * Helper method to generate payload entries from a a cursor.
     *
     * @param cursor The cursor.
     * @return A list of RemoteDataPayloadEntry objects.
     */
    private fun generatePayloadEntries(cursor: Cursor): Set<RemoteDataPayload> {
        cursor.moveToFirst()

        val entries: MutableSet<RemoteDataPayload> = HashSet()
        while (!cursor.isAfterLast) {
            try {
                val payload = RemoteDataPayload(
                    cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE)),
                    Instant.ofEpochMilli(cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP))),
                    JsonValue.parseString(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA))).optMap(),
                    parseRemoteDataInfo(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_METADATA)))
                )
                entries.add(payload)
            } catch (e: IllegalArgumentException) {
                UALog.e(e, "RemoteDataStore - failed to retrieve payload")
            } catch (e: JsonException) {
                UALog.e(e, "RemoteDataStore - failed to retrieve payload")
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
            if (jsonValue.isNull) {
                return null
            }
            return RemoteDataInfo(jsonValue)
        } catch (e: JsonException) {
            // Can happen during migration
            return null
        }
    }

    private companion object {
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

        /** The database version. */
        private const val DATABASE_VERSION = 2
    }
}
