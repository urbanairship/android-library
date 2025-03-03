/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.util.DataManager
import com.urbanairship.util.UAStringUtil
import java.io.File
import java.util.UUID

/**
 * [DataManager] class for remote data.
 *
 * @param context The app context.
 * @param appKey The app key.
 * @param dbName The database name.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataStore(
    private val context: Context,
    appKey: String,
    dbName: String,
    private val dataFileManager: RemoteDataFileManager = RemoteDataFileManager(context)
) : DataManager(context, appKey, dbName, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        UALog.d("Creating database (version: ${db.version})")
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
            1 -> db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_NAME_METADATA TEXT;")
            2 -> migrateV2ToV3(db)
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

        val db = getWritableDatabase()
        if (db == null) {
            UALog.e("RemoteDataStore - Unable to save remote data payloads.")
            return false
        }

        try {
            db.beginTransaction()

            for (payload in payloads) {
                // Generate a path for the data and write it to disk.
                val dataPath = generateDataFilePath()
                try {
                    dataFileManager.writeData(dataPath, payload.data)
                } catch (e: Exception) {
                    UALog.e(e, "RemoteDataStore - Unable to save remote data payload.")
                    db.endTransaction()
                    return false
                }

                val metadata = payload.remoteDataInfo?.toJsonValue() ?: JsonValue.NULL

                val value = ContentValues().apply {
                    put(COLUMN_NAME_TYPE, payload.type)
                    put(COLUMN_NAME_TIMESTAMP, payload.timestamp)
                    put(COLUMN_NAME_DATA, dataPath)
                    put(COLUMN_NAME_METADATA, metadata.toString())
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
     *
     * @return A List of RemoteDataPayload.
     */
    public val payloads: Set<RemoteDataPayload>
        get() = getPayloads(null)

    /**
     * Gets all payloads of the specified types.
     *
     * @param types The specified types.
     * @return A List of RemoteDataPayload.
     */
    public fun getPayloads(types: List<String>?): Set<RemoteDataPayload> {
        var cursor: Cursor? = null

        try {
            if (types == null) {
                cursor = this.query(TABLE_NAME, null, null, null, null)
            } else {
                val where: String = COLUMN_NAME_TYPE + " IN ( " + UAStringUtil.repeat("?", types.size, ", ") + " )"

                cursor = this.query(TABLE_NAME, null, where, types.toTypedArray<String>(), null)
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
        dataFileManager.deleteData()
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

        val entries = mutableSetOf<RemoteDataPayload>()
        while (!cursor.isAfterLast) {
            try {
                // Load json data from disk
                val dataPath = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA))
                val data = dataFileManager.readData(dataPath).getOrThrow()

                val payload = RemoteDataPayload(
                    cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP)),
                    data,
                    parseRemoteDataInfo(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_METADATA)))
                )
                entries.add(payload)
            } catch (e: Exception) {
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

    private fun generateDataFilePath(): String {
        return UUID.randomUUID().toString() + ".json"
    }

    private fun migrateV2ToV3(db: SQLiteDatabase) {
        UALog.d { "Migrating RemoteDataStore from version 2 to 3" }

        val tempTableName = "${TABLE_NAME}_temp"

        try {
            db.beginTransaction()

            // Create a temp table with the new schema
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + tempTableName + "_temp ("
                        + COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + COLUMN_NAME_TYPE + " TEXT,"
                        + COLUMN_NAME_TIMESTAMP + " INTEGER,"
                        + COLUMN_NAME_DATA + " TEXT,"
                        + COLUMN_NAME_METADATA + " TEXT"
                        + ");"
            )

            // Query all rows from the old table
            val cursor = db.query(TABLE_NAME, null, null, null, null, null, null)

            try {
                // Iterate through the cursor and insert into the new table
                cursor.moveToFirst()
                while (!cursor.isAfterLast) {
                    val id = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_ID))
                    val type = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TYPE))
                    val timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP))
                    val data = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA))
                    val metadata = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_METADATA))

                    val dataJson = JsonValue.parseString(data).optMap()

                    // Generate a path for the data and write it to disk.
                    val dataPath = generateDataFilePath()
                    dataFileManager.writeData(dataPath, dataJson).getOrThrow()

                    val value = ContentValues().apply {
                        put(COLUMN_NAME_ID, id)
                        put(COLUMN_NAME_TYPE, type)
                        put(COLUMN_NAME_TIMESTAMP, timestamp)
                        put(COLUMN_NAME_DATA, dataPath)
                        put(COLUMN_NAME_METADATA, metadata)
                    }

                    db.insert(tempTableName, null, value)

                    cursor.moveToNext()
                }
            } finally {
                cursor.close()
            }
            // Drop the original table and rename the temp table
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            db.execSQL("ALTER TABLE $tempTableName RENAME TO $TABLE_NAME")

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            // Drop and recreate
            db.execSQL("DROP TABLE IF EXISTS $tempTableName")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(db)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    public companion object {
        private const val TABLE_NAME = "payloads"

        private const val COLUMN_NAME_ID = "id"

        // The payload type
        private const val COLUMN_NAME_TYPE = "type"

        // The timestamp as a long integer of milliseconds
        private const val COLUMN_NAME_TIMESTAMP = "time"

        // File path to arbitrary JSON-serialized data
        private const val COLUMN_NAME_DATA = "data"

        // Metadata JSON-serialized data.
        private const val COLUMN_NAME_METADATA = "metadata"

        /**
         * The database version.
         */
        private const val DATABASE_VERSION = 3
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataFileManager(
    private val context: Context
) {
    private val dataDirectory
        get() = File(context.filesDir, DATA_DIRECTORY)

    internal fun readData(filename: String): Result<JsonMap> {
        val file = File(dataDirectory, filename)
        return try {
            if (!file.exists()) error("File does not exist: $filename")

            val json = file.bufferedReader().use { it.readText() }

            Result.success(JsonValue.parseString(json).optMap())
        } catch (e: Exception) {
            UALog.e(e, "Failed to read remote data from ${file.path}")
            Result.failure(e)
        }
    }

    internal fun writeData(filename: String, data: JsonMap): Result<Unit> {
        val file = File(dataDirectory, filename)
        return try {
            // Create dirs, if needed
            dataDirectory.mkdirs()

            file.bufferedWriter().use { it.write(data.toString()) }
            Result.success(Unit)
        } catch (e: Exception) {
            UALog.e(e, "Failed to write remote data to ${file.path}")
            Result.failure(e)
        }
    }

    internal fun deleteData() {
        dataDirectory.deleteRecursively()
    }

    internal companion object {
        internal const val DATA_DIRECTORY = "com.urbanairship.remotedata"
    }
}
