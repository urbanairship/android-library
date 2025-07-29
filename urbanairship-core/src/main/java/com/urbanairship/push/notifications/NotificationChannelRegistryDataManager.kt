/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.WorkerThread
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.DataManager

/**
 * [DataManager] class for NotificationChannelRegistry.
 *
 * @hide
 */
internal class NotificationChannelRegistryDataManager(
    context: Context,
    appKey: String,
    dbName: String
) : DataManager(context, appKey, dbName, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        UALog.d("Creating database")
        db.execSQL(
            ("CREATE TABLE IF NOT EXISTS $TABLE_NAME ($COLUMN_NAME_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "$COLUMN_NAME_CHANNEL_ID TEXT UNIQUE,$COLUMN_NAME_DATA TEXT);")
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                // Remove duplicate channel records (by channel ID).
                db.execSQL(
                    ("DELETE FROM $TABLE_NAME WHERE rowid NOT IN ( SELECT max(rowid) FROM $TABLE_NAME GROUP BY $COLUMN_NAME_CHANNEL_ID);")
                )
                // Add UNIQUE constraint to channel_id column by creating an index on it.
                db.execSQL(
                    ("CREATE UNIQUE INDEX ${TABLE_NAME}_$COLUMN_NAME_CHANNEL_ID ON $TABLE_NAME($COLUMN_NAME_CHANNEL_ID);")
                )
            }

            2 -> {}
            else -> {
                // Fall back to destructive migration.
                db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
                onCreate(db)
            }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop the table and recreate it.
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * Creates a channel in the database. This is a no-op if a channel already exists under
     * the associated identifier.
     *
     * @param channelCompat A NotificationChannelCompat.
     */
    @WorkerThread
    public fun createChannel(channelCompat: NotificationChannelCompat): Boolean {
        val db = writableDatabase ?: run {
            UALog.e("NotificationChannelRegistryDataManager - Unable to save notification channel.")
            return false
        }

        saveChannel(channelCompat, db)
        return true
    }

    /**
     * Gets all the saved notification channels.
     */
    @get:WorkerThread
    public val channels: Set<NotificationChannelCompat?>
        get() {
            val cursor = query(
                TABLE_NAME, null, null, null, null
            ) ?: return emptySet()

            val channels = mutableSetOf<NotificationChannelCompat>()

            cursor.moveToFirst()

            while (!cursor.isAfterLast) {
                val channelCompat = getChannel(cursor)
                channelCompat?.let { channels.add(it) }
                cursor.moveToNext()
            }

            return channels
        }

    /**
     * Gets a notification channel corresponding to the provided identifier.
     *
     * @param channelId The channel ID.
     * @return A NotificationChannelCompat instance, or `null` if one could not be found.
     */
    @WorkerThread
    public fun getChannel(channelId: String): NotificationChannelCompat? {
        val where = "$COLUMN_NAME_CHANNEL_ID = ?"
        val cursor = query(TABLE_NAME, null, where, arrayOf(channelId), null)
            ?: return null

        cursor.moveToFirst()

        var channelCompat: NotificationChannelCompat? = null
        if (!cursor.isAfterLast) {
            channelCompat = getChannel(cursor)
        }

        cursor.close()

        return channelCompat
    }

    /**
     * Deletes a notification channel from disk, by identifier.
     *
     * @param channelId
     */
    @WorkerThread
    public fun deleteChannel(channelId: String): Boolean {
        val where = "$COLUMN_NAME_CHANNEL_ID = ?"
        val result = delete(TABLE_NAME, where, arrayOf(channelId))

        if (result == -1) {
            UALog.e("Unable to remove notification channel: %s", channelId)
            return false
        }

        return true
    }

    /**
     * Deletes all payloads.
     *
     * @return A boolean indicating success.
     */
    @WorkerThread
    public fun deleteChannels(): Boolean {
        val success = delete(TABLE_NAME, null, null) >= 0
        if (!success) {
            UALog.e("NotificationChannelRegistryDatamanager - failed to delete channels")
        }
        return success
    }

    /**
     * Gets a notification channel from disk, by reading from the provided Cursor.
     *
     * @param cursor The Cursor.
     * @return A NotificationChannelCompat, or `null` if one could not be found or created.
     */
    @WorkerThread
    private fun getChannel(cursor: Cursor): NotificationChannelCompat? {
        val data = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA))

        try {
            return NotificationChannelCompat.fromJson(JsonValue.parseString(data))
        } catch (e: JsonException) {
            UALog.e("Unable to parse notification channel: %s", data)
            return null
        }
    }

    @WorkerThread
    private fun saveChannel(channelCompat: NotificationChannelCompat, database: SQLiteDatabase) {
        val value = ContentValues()
        value.put(COLUMN_NAME_CHANNEL_ID, channelCompat.id)
        value.put(COLUMN_NAME_DATA, channelCompat.toJsonValue().toString())

        database.insertWithOnConflict(TABLE_NAME, null, value, SQLiteDatabase.CONFLICT_REPLACE)
    }

    public companion object {

        public const val TABLE_NAME: String = "notification_channels"

        public const val COLUMN_NAME_ID: String = "id"

        // The channel ID
        public const val COLUMN_NAME_CHANNEL_ID: String = "channel_id"

        // JSON-serialized channel data
        public const val COLUMN_NAME_DATA: String = "data"

        /**
         * The database version.
         */
        private const val DATABASE_VERSION = 2
    }
}
