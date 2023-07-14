/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import android.content.Context;

import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.json.JsonTypeConverters;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Automation database.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = { ScheduleEntity.class, TriggerEntity.class }, version = 5)
@TypeConverters({ Converters.class, JsonTypeConverters.class })
public abstract class AutomationDatabase extends RoomDatabase {

    public abstract AutomationDao getScheduleDao();

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE schedules "
                    + " ADD COLUMN campaigns TEXT");

        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE schedules "
                    + " ADD COLUMN frequencyConstraintIds TEXT");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE schedules "
                    + " ADD COLUMN reportingContext TEXT");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE schedules "
                    + " ADD COLUMN messageType TEXT");
            database.execSQL("ALTER TABLE schedules "
                    + " ADD COLUMN bypassHoldoutGroups INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE schedules "
                    + " ADD COLUMN newUserEvaluationDate INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AutomationDatabase createDatabase(@NonNull Context context, @NonNull AirshipRuntimeConfig config) {
        String name = config.getConfigOptions().appKey + "_in-app-automation";
        String path = new File(ContextCompat.getNoBackupFilesDir(context), name).getAbsolutePath();
        return Room.databaseBuilder(context, AutomationDatabase.class, path)
                   .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                   .fallbackToDestructiveMigrationOnDowngrade()
                   .build();

    }

}
