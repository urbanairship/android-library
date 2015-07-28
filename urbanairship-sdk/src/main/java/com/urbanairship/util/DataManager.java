/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract class to manage a SQLiteDatabase.
 */
public abstract class DataManager {
    private static final int MAX_ATTEMPTS = 3;
    private final SQLiteOpenHelper openHelper;

    /**
     * Default Constructor for DataManager
     *
     * @param context The context used for opening and creating databases
     * @param name The name of the database
     * @param version The version of the database
     */
    public DataManager(@NonNull Context context, @NonNull final String name, int version) {
        openHelper = new SQLiteOpenHelper(context, name, null, version) {

            @Override
            public void onCreate(SQLiteDatabase db) {
                DataManager.this.onCreate(db);
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                Logger.debug("DataManager - Upgrading database " + name + " from version " + oldVersion + " to " + newVersion);
                DataManager.this.onUpgrade(db, oldVersion, newVersion);
            }

            @Override
            public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                Logger.debug("DataManager - Downgrading database " + name + " from version " + oldVersion + " to " + newVersion);
                DataManager.this.onDowngrade(db, oldVersion, newVersion);
            }
        };
    }

    /**
     * Called when the database is created for the first time.
     *
     * @param db The newly created database
     */
    protected abstract void onCreate(@NonNull SQLiteDatabase db);

    /**
     * Binds values to the statement. Used for bulk insert.
     *
     * @param statement The statement to bind values to
     * @param values The values to bind
     */
    protected abstract void bindValuesToSqliteStatement(@NonNull SQLiteStatement statement, @NonNull ContentValues values);

    /**
     * Get the insert statement
     *
     * @param table The table to insert into
     * @param db The database to insert values into
     * @return A constructed SQLiteStatement
     */
    protected abstract SQLiteStatement getInsertStatement(@NonNull String table, @NonNull SQLiteDatabase db);

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
                Logger.error("DataManager - Error opening writable database. Retrying...");
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
                Logger.error("DataManager - Error opening readable database. Retrying...");
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
        Logger.debug("DataManager - onUpgrade not implemented yet.");
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
     * Helper to build an insert sql statement
     *
     * @param table Table to insert into
     * @param columns Columns of values to insert
     * @return A SQL insert statement
     */
    @NonNull
    protected String buildInsertStatement(@NonNull String table, @NonNull String... columns) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("INSERT INTO ");
        sb.append(table);
        sb.append(" (");

        StringBuilder sbv = new StringBuilder(128);
        sbv.append("VALUES (");

        for (int i = 0; i < columns.length; i++) {
            sb.append("'");
            sb.append(columns[i]);
            sb.append("'");

            sbv.append("?");

            sb.append(i == (columns.length - 1) ? ") " : ", ");
            sbv.append(i == (columns.length - 1) ? ");" : ", ");
        }

        sb.append(sbv);
        return sb.toString();
    }


    /**
     * Helper to bind an int to a SQLiteStatement
     *
     * @param statement The SQLiteStatement to bind to
     * @param index Index of the value to bind
     * @param value The value to bind
     */
    protected void bind(@NonNull SQLiteStatement statement, int index, int value) {
        statement.bindLong(index, value);
    }

    /**
     * Helper to bind an boolean to a SQLiteStatement
     *
     * @param statement The SQLiteStatement to bind to
     * @param index Index of the value to bind
     * @param value The value to bind
     */
    protected void bind(@NonNull SQLiteStatement statement, int index, Boolean value) {
        if (value == null) {
            statement.bindNull(index);
        } else {
            statement.bindLong(index, value ? 1 : 0);
        }
    }

    /**
     * Helper to bind a boolean to a SQLiteStatement
     *
     * @param statement The SQLiteStatement to bind to
     * @param index Index of the value to bind
     * @param value The value to bind
     * @param defaultValue The value to use if value is null
     */
    protected void bind(@NonNull SQLiteStatement statement, int index, Boolean value, Boolean defaultValue) {
        if (value == null) {
            bind(statement, index, defaultValue);
        } else {
            bind(statement, index, value);
        }
    }

    /**
     * Helper to bind an string to a SQLiteStatement
     *
     * @param statement The SQLiteStatement to bind to
     * @param index Index of the value to bind
     * @param value The value to bind
     */
    protected void bind(@NonNull SQLiteStatement statement, int index, String value) {
        if (value == null) {
            statement.bindNull(index);
        } else {
            statement.bindString(index, value);
        }
    }

    /**
     * Helper to bind a string to a SQLiteStatement
     *
     * @param statement The SQLiteStatement to bind to
     * @param index Index of the value to bind
     * @param value The value to bind
     * @param defaultValue The value to use if value is null
     */
    protected void bind(@NonNull SQLiteStatement statement, int index, String value, String defaultValue) {
        if (value == null) {
            bind(statement, index, defaultValue);
        } else {
            bind(statement, index, value);
        }
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
                Logger.error("Unable to delete item from a database", ex);
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
    public List<ContentValues> bulkInsert(@NonNull String table, @NonNull ContentValues[] values) {
        SQLiteDatabase db = getWritableDatabase();
        List<ContentValues> inserted = new ArrayList<>();
        if (db == null) {
            return inserted;
        }

        db.beginTransaction();
        SQLiteStatement statement = getInsertStatement(table, db);

        try {
            for (ContentValues value : values) {
                if (tryExecuteStatement(statement, value)) {
                    inserted.add(value);
                }
            }

            if (!inserted.isEmpty()) {
                db.setTransactionSuccessful();
            }

            return inserted;
        } catch (Exception ex) {
            Logger.error("Unable to insert into database", ex);
        } finally {
            db.endTransaction();
        }

        return inserted;
    }

    /**
     * Inserts an item into the data
     *
     * @param table Name of the table to insert the item into
     * @param values The values to insert into the database
     * @return Row id of the inserted values
     */
    public long insert(@NonNull String table, ContentValues values) {
        SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            return -1;
        }

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return getWritableDatabase().replaceOrThrow(table, null, values);
            } catch (Exception ex) {
                Logger.error("Unable to insert into database", ex);
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
    public int update(@NonNull String table, ContentValues values, String selection,
                      String[] selectionArgs) {
        SQLiteDatabase db = getWritableDatabase();
        if (db == null) {
            return -1;
        }

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return db.update(table, values, selection, selectionArgs);
            } catch (SQLException e) {
                Logger.error("Update Failed", e);
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
    public Cursor query(@NonNull String table, String[] columns, String selection, String[] selectionArgs, String sortOrder) {
        return query(table, columns, selection, selectionArgs, sortOrder, null);
    }

    public Cursor query(@NonNull String table, String[] columns, String selection, String[] selectionArgs, String sortOrder, String limit) {
        SQLiteDatabase db = getReadableDatabase();
        if (db == null) {
            return null;
        }

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return db.query(table, columns, selection,
                        selectionArgs, null, null, sortOrder, limit);
            } catch (SQLException e) {
                Logger.error("Query Failed", e);
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
            Logger.error("Failed to close the database.", ex);
        }
    }

    /**
     * Tries to execute a SQLiteStatement. If fails, it will try again till MAX_ATTEMPTS is reached.
     * <p/>
     * Each try, it will clear the bindings of the statement, apply the values and try to execute the
     * statement.
     *
     * @param statement Statement to execute
     * @param values ContentValues to bind to the statement
     * @return <code>true</code> if successful, otherwise <code>false</code>
     */
    private boolean tryExecuteStatement(@NonNull SQLiteStatement statement, @NonNull ContentValues values) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                statement.clearBindings();
                bindValuesToSqliteStatement(statement, values);
                statement.execute();
                return true;
            } catch (Exception ex) {
                Logger.error("Unable to insert into database", ex);
            }
        }

        return false;
    }
}

