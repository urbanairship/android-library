/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbanairship.db.RecoverableMigration
import com.urbanairship.messagecenter.MessageDatabase.Companion.BODY_URL
import com.urbanairship.messagecenter.MessageDatabase.Companion.DELETED
import com.urbanairship.messagecenter.MessageDatabase.Companion.EXPIRATION
import com.urbanairship.messagecenter.MessageDatabase.Companion.EXTRA
import com.urbanairship.messagecenter.MessageDatabase.Companion.KEY
import com.urbanairship.messagecenter.MessageDatabase.Companion.MESSAGE_ID
import com.urbanairship.messagecenter.MessageDatabase.Companion.MESSAGE_URL
import com.urbanairship.messagecenter.MessageDatabase.Companion.RAW_MESSAGE
import com.urbanairship.messagecenter.MessageDatabase.Companion.READ_URL
import com.urbanairship.messagecenter.MessageDatabase.Companion.TABLE_NAME
import com.urbanairship.messagecenter.MessageDatabase.Companion.TIMESTAMP
import com.urbanairship.messagecenter.MessageDatabase.Companion.TITLE
import com.urbanairship.messagecenter.MessageDatabase.Companion.UNREAD
import com.urbanairship.messagecenter.MessageDatabase.Companion.UNREAD_ORIG

/**
 * Message database migration that can handle migration from most previous versions of the DB,
 * with a fallback to drop and recreate the table in the event that the existing table is missing
 * columns.
 *
 * @hide
 */
internal class MessageDatabaseMultiMigration (
    startVersion: Int,
    endVersion: Int
) : RecoverableMigration(startVersion, endVersion) {

    override fun tryMigrate(db: SupportSQLiteDatabase) {
        createTable(db, NEW_TABLE_NAME)

        // Clean up duplicate messages in the existing table, if any are present.
        db.execSQL(
            """
                DELETE FROM $TABLE_NAME
                WHERE $KEY NOT IN (
                    SELECT MIN($KEY)
                    FROM $TABLE_NAME
                    GROUP BY $MESSAGE_ID
                )
            """.trimIndent()
        )

        // Apply default values to nullable columns in the existing table.
        db.execSQL("UPDATE $TABLE_NAME SET $UNREAD_ORIG = 0 WHERE $UNREAD_ORIG IS NULL")
        db.execSQL("UPDATE $TABLE_NAME SET $UNREAD = 0 WHERE $UNREAD IS NULL")
        db.execSQL("UPDATE $TABLE_NAME SET $DELETED = 0 WHERE $DELETED IS NULL")

        // Copy into the new table.
        db.execSQL(
            """
                INSERT INTO $NEW_TABLE_NAME ($KEY, $MESSAGE_ID, $MESSAGE_URL, $BODY_URL, $READ_URL, $TITLE, $EXTRA, $UNREAD, $UNREAD_ORIG, $DELETED, $TIMESTAMP, $RAW_MESSAGE, $EXPIRATION)
                SELECT $KEY, $MESSAGE_ID, $MESSAGE_URL, $BODY_URL, $READ_URL, $TITLE, $EXTRA, $UNREAD, $UNREAD_ORIG, $DELETED, $TIMESTAMP, $RAW_MESSAGE, $EXPIRATION
                FROM $TABLE_NAME
            """.trimIndent()
        )

        dropOldAndRenameNewTable(db)
        createUniqueIndexOnMessageId(db)
    }

    override fun tryRecover(db: SupportSQLiteDatabase, e: Exception) {
        // Clean up from the failed migration.
        db.execSQL("DROP TABLE IF EXISTS $NEW_TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        // Recreate a fresh table and index. Messages will be re-fetched from backend.
        createTable(db, TABLE_NAME)
        createUniqueIndexOnMessageId(db)
    }

    /** Create the message table with the given `name`.  */
    private fun createTable(db: SupportSQLiteDatabase, name: String) {
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS $name (
                    $KEY INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    $MESSAGE_ID TEXT,
                    $MESSAGE_URL TEXT,
                    $BODY_URL TEXT,
                    $READ_URL TEXT,
                    $TITLE TEXT,
                    $EXTRA TEXT,
                    $UNREAD INTEGER NOT NULL,
                    $UNREAD_ORIG INTEGER NOT NULL,
                    $DELETED INTEGER NOT NULL,
                    $TIMESTAMP TEXT,
                    $RAW_MESSAGE TEXT,
                    $EXPIRATION TEXT
                );
            """.trimIndent()
        )
    }

    /** Drop existing table and rename the new table in place of the old one. */
    private fun dropOldAndRenameNewTable(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE $TABLE_NAME")
        db.execSQL("ALTER TABLE $NEW_TABLE_NAME RENAME TO $TABLE_NAME")
    }

    /** Adds a unique index on the MESSAGE_ID column.  */
    private fun createUniqueIndexOnMessageId(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `$INDEX_MESSAGE_ID` ON `$TABLE_NAME` (`$MESSAGE_ID`)")
    }

    companion object {
        const val NEW_TABLE_NAME: String = "richpush_new"
        const val INDEX_MESSAGE_ID: String = "index_richpush_message_id"
    }
}
