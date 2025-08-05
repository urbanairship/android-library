/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.DataManager
import com.urbanairship.util.UAStringUtil

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
    public fun savePayloads(payloads: Set<RemoteDataPayload>): Boolean {
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
                value.put(COLUMN_NAME_TIMESTAMP, timestamp)
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
    public val payloads: Set<RemoteDataPayload>
        get() = getPayloads(null)

    /**
     * Gets all payloads of the specified types.
     *
     * @param types The specified types.
     * @return A List of RemoteDataPayload.
     */
    public fun getPayloads(types: Collection<String>?): Set<RemoteDataPayload> {
        var cursor: Cursor? = null

        try {
            if (types == null) {
                cursor = this.query(TABLE_NAME, null, null, null, null)
            } else {
                val where =
                    COLUMN_NAME_TYPE + " IN ( " + UAStringUtil.repeat("?", types.size, ", ") + " )"

                cursor = this.query(
                    TABLE_NAME, null, where, types.toTypedArray<String>(), null
                )
            }

            if (cursor == null) {
                return emptySet()
            }

            return generatePayloadEntries(cursor)
        } finally {
            cursor?.close()
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
    private fun generatePayloadEntries(cursor: Cursor): Set<RemoteDataPayload> {
        cursor.moveToFirst()

        val entries: MutableSet<RemoteDataPayload> = HashSet()
        while (!cursor.isAfterLast) {
            try {
                val payload = RemoteDataPayload(
                    cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP)),
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
