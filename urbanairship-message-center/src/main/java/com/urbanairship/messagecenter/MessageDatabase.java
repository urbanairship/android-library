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
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities =  {MessageEntity.class}, version = 2)
public abstract class MessageDatabase extends RoomDatabase {

    private static final String DATABASE_DIRECTORY_NAME = "com.urbanairship.databases";
    static final String DATABASE_NAME = "ua_richpush.db";
    static final String TABLE_NAME = "richpush";
    static final String NEW_TABLE_NAME = "richpush_new";
    static final String COLUMN_NAME_KEY = "_id";
    static final String COLUMN_NAME_MESSAGE_ID = "message_id";
    static final String COLUMN_NAME_MESSAGE_URL = "message_url";
    static final String COLUMN_NAME_MESSAGE_BODY_URL = "message_body_url";
    static final String COLUMN_NAME_MESSAGE_READ_URL = "message_read_url";
    static final String COLUMN_NAME_TITLE = "title";
    static final String COLUMN_NAME_EXTRA = "extra";
    static final String COLUMN_NAME_UNREAD = "unread";
    static final String COLUMN_NAME_UNREAD_ORIG = "unread_orig";
    static final String COLUMN_NAME_DELETED = "deleted";
    static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    static final String COLUMN_NAME_RAW_MESSAGE_OBJECT = "raw_message_object";
    static final String COLUMN_NAME_EXPIRATION_TIMESTAMP = "expiration_timestamp";

    static final int DATABASE_VERSION = 2;

    public abstract MessageDao getDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Create new table
            db.execSQL("CREATE TABLE " + NEW_TABLE_NAME + " ("
                    + COLUMN_NAME_KEY + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + COLUMN_NAME_MESSAGE_ID + " TEXT UNIQUE, "
                    + COLUMN_NAME_MESSAGE_URL + " TEXT, "
                    + COLUMN_NAME_MESSAGE_BODY_URL + " TEXT, "
                    + COLUMN_NAME_MESSAGE_READ_URL + " TEXT, "
                    + COLUMN_NAME_TITLE + " TEXT, "
                    + COLUMN_NAME_EXTRA + " TEXT, "
                    + COLUMN_NAME_UNREAD + " INTEGER, "
                    + COLUMN_NAME_UNREAD_ORIG + " INTEGER, "
                    + COLUMN_NAME_DELETED + " INTEGER, "
                    + COLUMN_NAME_TIMESTAMP + " TEXT, "
                    + COLUMN_NAME_RAW_MESSAGE_OBJECT + " TEXT, "
                    + COLUMN_NAME_EXPIRATION_TIMESTAMP + " TEXT "
                    + ");");

            // Copy the table
            db.execSQL("INSERT INTO " + NEW_TABLE_NAME + " ("
                    + COLUMN_NAME_KEY + ", "
                    + COLUMN_NAME_MESSAGE_ID + ", "
                    + COLUMN_NAME_MESSAGE_URL + ", "
                    + COLUMN_NAME_MESSAGE_BODY_URL + ", "
                    + COLUMN_NAME_MESSAGE_READ_URL + ", "
                    + COLUMN_NAME_TITLE + ", "
                    + COLUMN_NAME_EXTRA + ", "
                    + COLUMN_NAME_UNREAD + ", "
                    + COLUMN_NAME_UNREAD_ORIG + ", "
                    + COLUMN_NAME_DELETED + ", "
                    + COLUMN_NAME_TIMESTAMP + ", "
                    + COLUMN_NAME_RAW_MESSAGE_OBJECT + ", "
                    + COLUMN_NAME_EXPIRATION_TIMESTAMP + ") "
                    + "SELECT "
                    + COLUMN_NAME_KEY + ", "
                    + COLUMN_NAME_MESSAGE_ID + ", "
                    + COLUMN_NAME_MESSAGE_URL + ", "
                    + COLUMN_NAME_MESSAGE_BODY_URL + ", "
                    + COLUMN_NAME_MESSAGE_READ_URL + ", "
                    + COLUMN_NAME_TITLE + ", "
                    + COLUMN_NAME_EXTRA + ", "
                    + COLUMN_NAME_UNREAD + ", "
                    + COLUMN_NAME_UNREAD_ORIG + ", "
                    + COLUMN_NAME_DELETED + ", "
                    + COLUMN_NAME_TIMESTAMP + ", "
                    + COLUMN_NAME_RAW_MESSAGE_OBJECT + ", "
                    + COLUMN_NAME_EXPIRATION_TIMESTAMP + " "
                    + "FROM " + TABLE_NAME);

            // Drop existing table
            db.execSQL("DROP TABLE " + TABLE_NAME);

            // Rename the new table to match the old
            db.execSQL("ALTER TABLE " + NEW_TABLE_NAME + " RENAME TO " + TABLE_NAME);
        }
    };

    public static MessageDatabase createDatabase(@NonNull Context context, @NonNull AirshipConfigOptions config) {
        String name = config.appKey + "_" + DATABASE_NAME;
        File urbanAirshipNoBackupDirectory = new File(ContextCompat.getNoBackupFilesDir(context), DATABASE_DIRECTORY_NAME);
        String path = new File(urbanAirshipNoBackupDirectory, name).getAbsolutePath();

        return Room.databaseBuilder(context, MessageDatabase.class, path)
                   .addMigrations(MIGRATION_1_2)
                   .fallbackToDestructiveMigrationOnDowngrade()
                   .build();
    }

    @VisibleForTesting
    public static MessageDatabase createInMemoryDatabase(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(context, MessageDatabase.class)
                   .allowMainThreadQueries()
                   .build();
    }

    public boolean exists(@NonNull Context context) {
        return getOpenHelper().getDatabaseName() == null ||
                context.getDatabasePath(getOpenHelper().getDatabaseName()).exists();
    }

};
