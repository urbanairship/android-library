/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits.storage;

import android.content.Context;

import com.urbanairship.config.AirshipRuntimeConfig;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = { ConstraintEntity.class, OccurrenceEntity.class }, version = 1)
public abstract class FrequencyLimitDatabase extends RoomDatabase {

    public abstract FrequencyLimitDao getDao();

    public static FrequencyLimitDatabase createDatabase(@NonNull Context context, @NonNull AirshipRuntimeConfig config) {
        String name = config.getConfigOptions().appKey + "_frequency_limits";
        String path = new File(ContextCompat.getNoBackupFilesDir(context), name).getAbsolutePath();
        return Room.databaseBuilder(context, FrequencyLimitDatabase.class, path)
                   .fallbackToDestructiveMigrationOnDowngrade()
                   .build();
    }

}
