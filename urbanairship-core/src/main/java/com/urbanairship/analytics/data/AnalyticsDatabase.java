package com.urbanairship.analytics.data;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.json.JsonTypeConverters;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

/**
 * Analytics database.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = { EventEntity.class }, version = 3)
@TypeConverters({ JsonTypeConverters.class })
public abstract class AnalyticsDatabase extends RoomDatabase {

    public abstract EventDao getEventDao();

    private static final String DATABASE_DIR = "com.urbanairship.databases";
    private static final String DATABASE_NAME = "ua_analytics.db";

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        private static final String OLD_TABLE_NAME = "events";
        private static final String OLD_ID = "_id";
        private static final String OLD_TYPE = "type";
        private static final String OLD_EVENT_ID = "event_id";
        private static final String OLD_TIME = "time";
        private static final String OLD_DATA = "data";
        private static final String OLD_SESSION_ID = "session_id";
        private static final String OLD_EVENT_SIZE = "event_size";

        private static final String NEW_TABLE_NAME = "events_new";
        private static final String NEW_ID = "id";
        private static final String NEW_TYPE = "type";
        private static final String NEW_EVENT_ID = "eventId";
        private static final String NEW_TIME = "time";
        private static final String NEW_DATA = "data";
        private static final String NEW_SESSION_ID = "sessionId";
        private static final String NEW_EVENT_SIZE = "eventSize";

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Create new table
            db.execSQL("CREATE TABLE " + NEW_TABLE_NAME + " ("
                    + NEW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + NEW_TYPE + " TEXT, "
                    + NEW_EVENT_ID + " TEXT, "
                    + NEW_TIME + " TEXT, "
                    + NEW_DATA + " TEXT, "
                    + NEW_SESSION_ID + " TEXT, "
                    + NEW_EVENT_SIZE + " INTEGER NOT NULL"
                    + ");");

            // Copy data from existing table
            db.execSQL("INSERT INTO " + NEW_TABLE_NAME + " ("
                    + NEW_ID + ", "
                    + NEW_TYPE + ", "
                    + NEW_EVENT_ID + ", "
                    + NEW_TIME + ", "
                    + NEW_DATA + ", "
                    + NEW_SESSION_ID + ", "
                    + NEW_EVENT_SIZE + ") "
                    + "SELECT "
                    + OLD_ID + ", "
                    + OLD_TYPE + ", "
                    + OLD_EVENT_ID + ", "
                    + OLD_TIME + ", "
                    + OLD_DATA + ", "
                    + OLD_SESSION_ID + ", "
                    + OLD_EVENT_SIZE + " "
                    + "FROM " + OLD_TABLE_NAME);

            // Drop existing table
            db.execSQL("DROP TABLE " + OLD_TABLE_NAME);

            // Rename the new table to match the old
            db.execSQL("ALTER TABLE " + NEW_TABLE_NAME + " RENAME TO " + OLD_TABLE_NAME);
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        private static final String TABLE_NAME = "events";

        private static final String ID = "id";
        private static final String EVENT_ID = "eventId";
        private static final String INDEX_EVENT_ID = "index_events_eventId";

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Clean up duplicate events, if any are present.
            db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + ID + " NOT IN "
                + "(SELECT MIN(" + ID +") FROM " + TABLE_NAME + " GROUP BY " + EVENT_ID + ")");
            // Add unique index on eventId.
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `" + INDEX_EVENT_ID + "` "
                + "ON `" + TABLE_NAME + "` (`" + EVENT_ID + "`)");
        }
    };

    public static AnalyticsDatabase createDatabase(@NonNull Context context, @NonNull AirshipRuntimeConfig config) {
        // Attempt to migrate an existing analytics db by moving it to the new location. The 1 -> 2
        // migration will handle updating the events schema and records when it runs.
        String path = migrateExistingDbIfExists(context, config);

        return Room.databaseBuilder(context, AnalyticsDatabase.class, path)
                   .addMigrations(
                       MIGRATION_1_2,
                       MIGRATION_2_3
                   )
                   .fallbackToDestructiveMigrationOnDowngrade()
                   .build();
    }

    private static String migrateExistingDbIfExists(@NonNull Context context, @NonNull AirshipRuntimeConfig config) {
        File oldDbDir = new File(ContextCompat.getNoBackupFilesDir(context), DATABASE_DIR);
        String oldName = config.getConfigOptions().appKey + "_" + DATABASE_NAME;
        File oldDb = new File(oldDbDir, oldName);

        String newName = config.getConfigOptions().appKey + "_analytics";
        File newDb = new File(ContextCompat.getNoBackupFilesDir(context), newName);

        if (oldDb.exists() && !newDb.exists()) {
            if (!oldDb.renameTo(newDb)) {
                Logger.warn("Failed to move analytics db: %s -> %s", oldDb.getPath(), newDb.getPath());
            }
        }

        return newDb.getAbsolutePath();
    }

    @VisibleForTesting
    public static AnalyticsDatabase createInMemoryDatabase(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(context, AnalyticsDatabase.class)
                   .allowMainThreadQueries()
                   .build();
    }

    public boolean exists(Context context) {
        SupportSQLiteOpenHelper helper = getOpenHelper();
        // null databaseName means it is an in-memory database. Lets assume the database exists when in-memory.
        return helper.getDatabaseName() == null || context.getDatabasePath(helper.getDatabaseName()).exists();
    }
}
