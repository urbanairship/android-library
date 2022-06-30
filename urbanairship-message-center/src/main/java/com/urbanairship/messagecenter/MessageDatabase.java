package com.urbanairship.messagecenter;

import android.content.Context;

import com.urbanairship.AirshipConfigOptions;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Message database
 */
@Database(
    version = 4,
    entities = { MessageEntity.class }
)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class MessageDatabase extends RoomDatabase {

    static final String DB_NAME = "ua_richpush.db";
    static final String TABLE_NAME = "richpush";
    static final String KEY = "_id";
    static final String MESSAGE_ID = "message_id";
    static final String MESSAGE_URL = "message_url";
    static final String BODY_URL = "message_body_url";
    static final String READ_URL = "message_read_url";
    static final String TITLE = "title";
    static final String EXTRA = "extra";
    static final String UNREAD = "unread";
    static final String UNREAD_ORIG = "unread_orig";
    static final String DELETED = "deleted";
    static final String TIMESTAMP = "timestamp";
    static final String RAW_MESSAGE = "raw_message_object";
    static final String EXPIRATION = "expiration_timestamp";

    private static final String DB_DIR = "com.urbanairship.databases";

    public abstract MessageDao getDao();

    /**
     * Recreates the table to match the schema expected by Room.
     * No actual schema changes were introduced in v4.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        static final String NEW_TABLE_NAME = "richpush_new";
        static final String INDEX_MESSAGE_ID = "index_richpush_message_id";

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + NEW_TABLE_NAME + " ("
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

            // Drop existing table and rename the new table to match the old one.
            db.execSQL("DROP TABLE " + TABLE_NAME);
            db.execSQL("ALTER TABLE " + NEW_TABLE_NAME + " RENAME TO " + TABLE_NAME);

            // Add unique index on message_id.
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `" + INDEX_MESSAGE_ID + "` "
                    + "ON `" + TABLE_NAME + "` (`" + MESSAGE_ID + "`)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        static final String INDEX_MESSAGE_ID = "index_richpush_message_id";

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Clean up duplicate messages, if any are present.
            db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + KEY + " NOT IN "
                    + "(SELECT MIN(" + KEY + ") FROM " + TABLE_NAME + " GROUP BY " + MESSAGE_ID + ")");
            // Add unique index on message_id.
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `" + INDEX_MESSAGE_ID + "` "
                    + "ON `" + TABLE_NAME + "` (`" + MESSAGE_ID + "`)");
        }
    };

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        static final String NEW_TABLE_NAME = "richpush_new";

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Create new table
            db.execSQL("CREATE TABLE " + NEW_TABLE_NAME + " ("
                    + KEY + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + MESSAGE_ID + " TEXT UNIQUE, "
                    + MESSAGE_URL + " TEXT, "
                    + BODY_URL + " TEXT, "
                    + READ_URL + " TEXT, "
                    + TITLE + " TEXT, "
                    + EXTRA + " TEXT, "
                    + UNREAD + " INTEGER, "
                    + UNREAD_ORIG + " INTEGER, "
                    + DELETED + " INTEGER, "
                    + TIMESTAMP + " TEXT, "
                    + RAW_MESSAGE + " TEXT, "
                    + EXPIRATION + " TEXT "
                    + ");");

            // Copy the table
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

            // Drop existing table
            db.execSQL("DROP TABLE " + TABLE_NAME);

            // Rename the new table to match the old
            db.execSQL("ALTER TABLE " + NEW_TABLE_NAME + " RENAME TO " + TABLE_NAME);
        }
    };

    public static MessageDatabase createDatabase(@NonNull Context context, @NonNull AirshipConfigOptions config) {
        String name = config.appKey + "_" + DB_NAME;
        File urbanAirshipNoBackupDirectory = new File(ContextCompat.getNoBackupFilesDir(context), DB_DIR);
        String path = new File(urbanAirshipNoBackupDirectory, name).getAbsolutePath();

        return Room.databaseBuilder(context, MessageDatabase.class, path)
                   .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                   .fallbackToDestructiveMigrationOnDowngrade()
                   .build();
    }

    @VisibleForTesting
    public static MessageDatabase createInMemoryDatabase(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(context, MessageDatabase.class)
                   .allowMainThreadQueries()
                   .build();
    }
}
