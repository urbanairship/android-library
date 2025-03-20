/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter.core;

import com.urbanairship.db.RecoverableMigration;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.sqlite.db.SupportSQLiteDatabase;

import static com.urbanairship.messagecenter.core.MessageDatabase.BODY_URL;
import static com.urbanairship.messagecenter.core.MessageDatabase.DELETED;
import static com.urbanairship.messagecenter.core.MessageDatabase.EXPIRATION;
import static com.urbanairship.messagecenter.core.MessageDatabase.EXTRA;
import static com.urbanairship.messagecenter.core.MessageDatabase.KEY;
import static com.urbanairship.messagecenter.core.MessageDatabase.MESSAGE_ID;
import static com.urbanairship.messagecenter.core.MessageDatabase.MESSAGE_URL;
import static com.urbanairship.messagecenter.core.MessageDatabase.RAW_MESSAGE;
import static com.urbanairship.messagecenter.core.MessageDatabase.READ_URL;
import static com.urbanairship.messagecenter.core.MessageDatabase.TABLE_NAME;
import static com.urbanairship.messagecenter.core.MessageDatabase.TIMESTAMP;
import static com.urbanairship.messagecenter.core.MessageDatabase.TITLE;
import static com.urbanairship.messagecenter.core.MessageDatabase.UNREAD;
import static com.urbanairship.messagecenter.core.MessageDatabase.UNREAD_ORIG;

/**
 * Message database migration that can handle migration from most previous versions of the DB,
 * with a fallback to drop and recreate the table in the event that the existing table is missing
 * columns.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class MessageDatabaseMultiMigration extends RecoverableMigration {
    static final String NEW_TABLE_NAME = "richpush_new";
    static final String INDEX_MESSAGE_ID = "index_richpush_message_id";

    public MessageDatabaseMultiMigration(int startVersion, int endVersion) {
        super(startVersion, endVersion);
    }

    @Override
    public void tryMigrate(@NonNull SupportSQLiteDatabase db) {
        createTable(db, NEW_TABLE_NAME);

        // Clean up duplicate messages in the existing table, if any are present.
        db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + KEY + " NOT IN "
                + "(SELECT MIN(" + KEY + ") FROM " + TABLE_NAME
                + " GROUP BY " + MESSAGE_ID + ")");

        // Apply default values to nullable columns in the existing table.
        db.execSQL("UPDATE " + TABLE_NAME +
                " SET " + UNREAD_ORIG + " = 0 WHERE " + UNREAD_ORIG + " IS NULL");
        db.execSQL("UPDATE " + TABLE_NAME + "" +
                " SET " + UNREAD + " = 0 WHERE " + UNREAD + " IS NULL");
        db.execSQL("UPDATE " + TABLE_NAME + "" +
                " SET " + DELETED + " = 0 WHERE " + DELETED + " IS NULL");

        // Copy into the new table.
        db.execSQL("INSERT INTO " + NEW_TABLE_NAME + " ("
                + KEY + ", "
                + MESSAGE_ID + ", "
                + MESSAGE_URL + ", "
                + BODY_URL + ", "
                + READ_URL + ", "
                + TITLE + ", "
                + EXTRA + ", "
                + UNREAD + ", "
                + UNREAD_ORIG + ", "
                + DELETED + ", "
                + TIMESTAMP + ", "
                + RAW_MESSAGE + ", "
                + EXPIRATION + ") "
                + "SELECT "
                + KEY + ", "
                + MESSAGE_ID + ", "
                + MESSAGE_URL + ", "
                + BODY_URL + ", "
                + READ_URL + ", "
                + TITLE + ", "
                + EXTRA + ", "
                + UNREAD + ", "
                + UNREAD_ORIG + ", "
                + DELETED + ", "
                + TIMESTAMP + ", "
                + RAW_MESSAGE + ", "
                + EXPIRATION + " "
                + "FROM " + TABLE_NAME);

        dropOldAndRenameNewTable(db);
        createUniqueIndexOnMessageId(db);
    }

    @Override
    public void tryRecover(@NonNull SupportSQLiteDatabase db, @NonNull Exception e) {
        // Clean up from the failed migration.
        db.execSQL("DROP TABLE IF EXISTS " + NEW_TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        // Recreate a fresh table and index. Messages will be re-fetched from backend.
        createTable(db, TABLE_NAME);
        createUniqueIndexOnMessageId(db);
    }

    /** Create the message table with the given {@code name}. */
    private void createTable(@NonNull SupportSQLiteDatabase db, @NonNull String name) {
        db.execSQL("CREATE TABLE " + name + " ("
                + KEY + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + MESSAGE_ID + " TEXT, "
                + MESSAGE_URL + " TEXT, "
                + BODY_URL + " TEXT, "
                + READ_URL + " TEXT, "
                + TITLE + " TEXT, "
                + EXTRA + " TEXT, "
                + UNREAD + " INTEGER NOT NULL, "
                + UNREAD_ORIG + " INTEGER NOT NULL, "
                + DELETED + " INTEGER NOT NULL, "
                + TIMESTAMP + " TEXT, "
                + RAW_MESSAGE + " TEXT, "
                + EXPIRATION + " TEXT "
                + ");");
    }

    /* Drop existing table and rename the new table in place of the old one. */
    private void dropOldAndRenameNewTable(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE " + TABLE_NAME);
        db.execSQL("ALTER TABLE " + NEW_TABLE_NAME + " RENAME TO " + TABLE_NAME);
    }

    /** Adds a unique index on the MESSAGE_ID column. */
    private void createUniqueIndexOnMessageId(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `" + INDEX_MESSAGE_ID + "` "
                + "ON `" + TABLE_NAME + "` (`" + MESSAGE_ID + "`)");
    }
}
