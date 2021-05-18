/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;

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
 * PreferenceData database
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = {PreferenceData.class}, version = 2)
public abstract class PreferenceDataDatabase extends RoomDatabase {

    private static final String DATABASE_DIRECTORY_NAME = "com.urbanairship.databases";
    static final String DATABASE_NAME = "ua_preferences.db";
    static final String TABLE_NAME = "preferences";
    static final String NEW_TABLE_NAME = "preferences_new";
    static final String COLUMN_NAME_KEY = "_id";
    static final String COLUMN_NAME_VALUE = "value";

    static final int DATABASE_VERSION = 2;

    public abstract PreferenceDataDao getDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            //Create new table
            database.execSQL("CREATE TABLE " + NEW_TABLE_NAME + " ("
                    + COLUMN_NAME_KEY + " TEXT PRIMARY KEY NOT NULL, "
                    + COLUMN_NAME_VALUE + " TEXT);");

            //Copy the data
            database.execSQL("INSERT INTO " + NEW_TABLE_NAME + " (" + COLUMN_NAME_KEY + ", "
                    + COLUMN_NAME_VALUE + ") SELECT " + COLUMN_NAME_KEY + ", " + COLUMN_NAME_VALUE
                    + " FROM " + TABLE_NAME);

            //Remove the old table
            database.execSQL("DROP TABLE " + TABLE_NAME);

            //Rename the new table
            database.execSQL("ALTER TABLE " + NEW_TABLE_NAME + " RENAME TO " + TABLE_NAME);
        }
    };

    public static PreferenceDataDatabase createDatabase(@NonNull Context context, @NonNull AirshipConfigOptions config) {
        String name = config.appKey + "_" + DATABASE_NAME;
        File urbanAirshipNoBackupDirectory = new File(ContextCompat.getNoBackupFilesDir(context), DATABASE_DIRECTORY_NAME);
        String path = new File(urbanAirshipNoBackupDirectory, name).getAbsolutePath();

        return Room.databaseBuilder(context, PreferenceDataDatabase.class, path)
                   .addMigrations(MIGRATION_1_2)
                   .fallbackToDestructiveMigrationOnDowngrade()
                   .build();
    }

    @VisibleForTesting
    public static PreferenceDataDatabase createInMemoryDatabase(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(context, PreferenceDataDatabase.class)
                   .allowMainThreadQueries()
                   .build();
    }

}
