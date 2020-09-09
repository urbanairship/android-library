/* Copyright Airship and Contributors */

package com.urbanairship.automation.storage;

import android.content.Context;

import com.urbanairship.config.AirshipRuntimeConfig;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

/**
 * Automation database.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = { ScheduleEntity.class, TriggerEntity.class }, version = 1, exportSchema = false)
@TypeConverters({ Converters.class })
public abstract class AutomationDatabase extends RoomDatabase {

    public abstract AutomationDao getScheduleDao();

    public static AutomationDatabase createDatabase(@NonNull Context context, @NonNull AirshipRuntimeConfig config) {
        String name = config.getConfigOptions().appKey + "_in-app-automation";
        String path = new File(ContextCompat.getNoBackupFilesDir(context), name).getAbsolutePath();
        return Room.databaseBuilder(context, AutomationDatabase.class, path)
                   .build();

    }

}
