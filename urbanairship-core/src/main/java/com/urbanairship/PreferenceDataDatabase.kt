/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.Room.inMemoryDatabaseBuilder
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * PreferenceData database
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Database(entities = [PreferenceData::class], version = 2)
public abstract class PreferenceDataDatabase public constructor() : RoomDatabase() {

    public abstract val dao: PreferenceDataDao

    public fun exists(context: Context): Boolean {
        return openHelper.databaseName == null
                || context.getDatabasePath(openHelper.databaseName).exists()
    }

    public companion object {

        private const val DATABASE_DIRECTORY_NAME = "com.urbanairship.databases"
        public const val DATABASE_NAME: String = "ua_preferences.db"
        public const val TABLE_NAME: String = "preferences"
        public const val NEW_TABLE_NAME: String = "preferences_new"
        public const val COLUMN_NAME_KEY: String = "_id"
        public const val COLUMN_NAME_VALUE: String = "value"

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                //Create new table
                db.execSQL(
                    ("CREATE TABLE $NEW_TABLE_NAME ($COLUMN_NAME_KEY TEXT PRIMARY KEY NOT NULL, $COLUMN_NAME_VALUE TEXT);")
                )

                //Copy the data
                db.execSQL(
                    ("INSERT INTO $NEW_TABLE_NAME ($COLUMN_NAME_KEY, $COLUMN_NAME_VALUE) SELECT $COLUMN_NAME_KEY, $COLUMN_NAME_VALUE FROM $TABLE_NAME")
                )

                //Remove the old table
                db.execSQL("DROP TABLE $TABLE_NAME")

                //Rename the new table
                db.execSQL("ALTER TABLE $NEW_TABLE_NAME RENAME TO $TABLE_NAME")
            }
        }

        public fun createDatabase(
            context: Context,
            config: AirshipConfigOptions
        ): PreferenceDataDatabase {
            val name = config.appKey + "_" + DATABASE_NAME
            val urbanAirshipNoBackupDirectory = File(ContextCompat.getNoBackupFilesDir(context), DATABASE_DIRECTORY_NAME)
            val path = File(urbanAirshipNoBackupDirectory, name).absolutePath

            return databaseBuilder(context, PreferenceDataDatabase::class.java, path)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }

        @VisibleForTesting
        public fun createInMemoryDatabase(context: Context): PreferenceDataDatabase {
            return inMemoryDatabaseBuilder(context, PreferenceDataDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}
