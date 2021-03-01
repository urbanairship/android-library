/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.SystemClock;

import com.urbanairship.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;

/**
 * An abstract class to manage a SQLiteDatabase.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class DataManager {

    private static final String DATABASE_DIRECTORY_NAME = "com.urbanairship.databases";

    private static final int MAX_ATTEMPTS = 3;
    private final SQLiteOpenHelper openHelper;
    private final String path;

    /**
     * Default Constructor for DataManager
     *
     * @param context The context used for opening and creating databases
     * @param appKey The application key. Used to prefix the database file.
     * @param name The name of the database
     * @param version The version of the database
     */
    public DataManager(@NonNull Context context, @NonNull String appKey, @NonNull String name, int version) {
        this.path = migrateDatabase(context, appKey, name);

        openHelper = new SQLiteOpenHelper(context, path, null, version) {

            @Override
            public void onCreate(@NonNull SQLiteDatabase db) {
                DataManager.this.onCreate(db);
            }

            @Override
            public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
                Logger.debug("Upgrading database %s from version %s to %s", db, oldVersion, newVersion);
                DataManager.this.onUpgrade(db, oldVersion, newVersion);
            }

            @Override
            public void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
                Logger.debug("Downgrading database %s from version %s to %s", db, oldVersion, newVersion);
                DataManager.this.onDowngrade(db, oldVersion, newVersion);
            }

            @Override
            public void onConfigure(@NonNull SQLiteDatabase db) {
                super.onConfigure(db);
                DataManager.this.onConfigure(db);
            }

            @Override
            public void onOpen(@NonNull SQLiteDatabase db) {
                super.onOpen(db);
                DataManager.this.onOpen(db);

            }
        };
    }

    /**
     * Called when the database connection is opened.
     *
     * @param db The database.
     */
    protected void onOpen(@NonNull SQLiteDatabase db) {
    }

    /**
     * Called when the database connection is configured.
     *
     * @param db The database.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onConfigure(@NonNull SQLiteDatabase db) {
    }

    /**
     * Called when the database is created for the first time.
     *
     * @param db The newly created database
     */
    protected abstract void onCreate(@NonNull SQLiteDatabase db);

    /**
     * Opens a writable database
     *
     * @return a writable SQLiteDatabase
     */
    @Nullable
    protected SQLiteDatabase getWritableDatabase() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return openHelper.getWritableDatabase();
            } catch (SQLiteException e) {

                // It's very bad for the app if the DB cannot be opened, so it's worth
                // a sleep to wait for a lock to go away.
                SystemClock.sleep(100);
                Logger.error(e, "DataManager - Error opening writable database. Retrying...");
            }
        }

        return null;
    }

    /**
     * Opens a readable database
     *
     * @return a readable SQLiteDatabase
     */
    @Nullable
    protected SQLiteDatabase getReadableDatabase() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return openHelper.getReadableDatabase();
            } catch (SQLiteException e) {

                // It's very bad for the app if the DB cannot be opened, so it's worth
                // a sleep to wait for a lock to go away.
                SystemClock.sleep(100);
                Logger.error(e, "DataManager - Error opening readable database. Retrying...");
            }
        }

        return null;
    }

    /**
     * Called when a database needs to be upgraded
     *
     * @param db The database to upgrade
     * @param oldVersion Version of the old database
     * @param newVersion Version of the new database
     */
    protected void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        Logger.debug("onUpgrade not implemented yet.");
    }

    /**
     * Called when a database needs to be downgraded
     *
     * @param db The database to downgrade
     * @param oldVersion Version of the old database
     * @param newVersion Version of the new database
     */
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new SQLiteException("Unable to downgrade database");
    }

    /**
     * Deletes items from the database
     *
     * @param table Table to delete the value from
     * @param selection Optional WHERE statement
     * @param selectionArgs arguments to the WHERE clause
     * @return number of rows deleted, or -1 if an error occurred
     */
    public int delete(@NonNull String table, @Nullable String selection, @Nullable String[] selectionArgs) {
        // If the where clause is null (deletes all rows), set it to "1" so that the delete() call
        // will return the number of rows deleted rather than 0.
        if (selection == null) {
            selection = "1";
        }

        SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            return -1;
        }

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return db.delete(table, selection, selectionArgs);
            } catch (Exception ex) {
                Logger.error(ex, "Unable to delete item from a database");
            }
        }

        return -1;
    }

    /**
     * Inserts several items into the database
     *
     * @param table Table to insert the values into
     * @param values An array of values to insert into the database
     * @return A list of the values inserted into the database
     */
    @NonNull
    public List<ContentValues> bulkInsert(@NonNull String table, @NonNull ContentValues[] values) {
        SQLiteDatabase db = getWritableDatabase();
        List<ContentValues> inserted = new ArrayList<>();
        if (db == null) {
            return inserted;
        }

        db.beginTransaction();
        for (ContentValues value : values) {
            try {
                db.replaceOrThrow(table, null, value);
            } catch (Exception ex) {
                Logger.error(ex, "Unable to insert into database");
                db.endTransaction();
                return Collections.emptyList();
            }
        }

        db.setTransactionSuccessful();
        db.endTransaction();

        return inserted;
    }

    /**
     * Inserts an item into the data
     *
     * @param table Name of the table to insert the item into
     * @param values The values to insert into the database
     * @return Row id of the inserted values
     */
    public long insert(@NonNull String table, @Nullable ContentValues values) {
        SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            return -1;
        }

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return getWritableDatabase().replaceOrThrow(table, null, values);
            } catch (Exception ex) {
                Logger.error(ex, "Unable to insert into database");
            }
        }

        return -1;
    }

    /**
     * Updates a row in the database
     *
     * @param table The table to update
     * @param values The values to update with
     * @param selection Optional WHERE statement, null will update all rows
     * @param selectionArgs arguments to the WHERE clause
     * @return number of rows updated
     */
    public int update(@NonNull String table, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            return -1;
        }

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return db.update(table, values, selection, selectionArgs);
            } catch (SQLException e) {
                Logger.error(e, "Update Failed");
            }
        }

        return -1;
    }

    /**
     * Queries the database
     *
     * @param table The database table to query
     * @param columns The columns to return in the query
     * @param selection Optional WHERE statement, null will return all rows
     * @param selectionArgs arguments to the WHERE clause
     * @param sortOrder How to sort the rows
     * @return A cursor with the query results, or null if anything went wrong
     */
    @Nullable
    public Cursor query(@NonNull String table, @Nullable String[] columns, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return query(table, columns, selection, selectionArgs, sortOrder, null);
    }

    @Nullable
    public Cursor query(@NonNull String table, @Nullable String[] columns, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder, @Nullable String limit) {
        SQLiteDatabase db = getReadableDatabase();
        if (db == null) {
            return null;
        }

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return db.query(table, columns, selection,
                        selectionArgs, null, null, sortOrder, limit);
            } catch (SQLException e) {
                Logger.error(e, "Query Failed");
            }
        }

        return null;
    }

    /**
     * Queries the database with a raw SQL query
     *
     * @param query The SQL query
     * @param selectionArgs Arguments to the WHERE clause
     * @return A cursor with the query results, or null if anything went wrong
     */
    @Nullable
    public Cursor rawQuery(@NonNull String query, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = getReadableDatabase();
        if (db == null) {
            return null;
        }

        for (int i = 0; i < 3; i++) {
            try {
                return db.rawQuery(query, selectionArgs);
            } catch (SQLException e) {
                Logger.error(e, "Query failed");
            }
        }

        return null;
    }

    /**
     * Closes the connection to the database
     */
    public void close() {
        try {
            openHelper.close();
        } catch (Exception ex) {
            Logger.error(ex, "Failed to close the database.");
        }
    }


    public boolean databaseExists(@NonNull Context context) {
        return context.getDatabasePath(path).exists();
    }

    public boolean deleteDatabase(@NonNull Context context) {
        try {
            return context.getDatabasePath(path).delete();
        } catch (Exception e) {
            Logger.error(e, "Failed to delete database: " + path);
            return false;
        }
    }

    /**
     * Tries to move the database to a prefixed name in the no backup directory.
     *
     * @param context The application context.
     * @param appKey The appKey.
     * @param name The database name.
     * @return The full path of the database.
     */
    protected static String migrateDatabase(@NonNull Context context, String appKey, String name) {
        String targetName = appKey + "_" + name;

        File urbanAirshipNoBackupDirectory = new File(ContextCompat.getNoBackupFilesDir(context), DATABASE_DIRECTORY_NAME);
        if (!urbanAirshipNoBackupDirectory.exists() && !urbanAirshipNoBackupDirectory.mkdirs()) {
            Logger.error("Failed to create UA no backup directory.");
        }

        File target = new File(urbanAirshipNoBackupDirectory, targetName);
        File[] sources = new File[] {
                // Standard directory with the appKey prefix
                context.getDatabasePath(targetName),

                // No backup directory with database name without appKey prefix
                new File(urbanAirshipNoBackupDirectory, name),

                // Standard directory without the appKey prefix
                context.getDatabasePath(name)
        };

        if (target.exists()) {
            return target.getAbsolutePath();
        }

        for (File oldFile : sources) {
            if (!oldFile.exists()) {
                continue;
            }

            // Failed, we will get it next time
            if (!oldFile.renameTo(target)) {
                return oldFile.getAbsolutePath();
            }

            // Move the journal file if it exists
            File journal = new File(oldFile.getAbsolutePath() + "-journal");
            if (journal.exists()) {
                if (!journal.renameTo(new File(target.getAbsolutePath() + "-journal"))) {
                    Logger.error("Failed to move the journal file: " + journal);
                }
            }
        }

            return target.getAbsolutePath();
    }

}
