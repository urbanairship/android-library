/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.urbanairship.UALog
import java.io.File

/**
 * An abstract class to manage a SQLiteDatabase.
 *
 * @hide
 */

internal abstract class DataManager (
    /** The context used for opening and creating databases */
    context: Context,
    /** The application key. Used to prefix the database file. */
    appKey: String,
    /** The name of the database */
    name: String,
    /** The version of the database */
    version: Int
) {

    private val openHelper: SQLiteOpenHelper
    private val path: String = migrateDatabase(context, appKey, name)

    init {
        openHelper = object : SQLiteOpenHelper(context, path, null, version) {
            override fun onCreate(db: SQLiteDatabase) {
                this@DataManager.onCreate(db)
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                UALog.d("Upgrading database %s from version %s to %s", db, oldVersion, newVersion)
                this@DataManager.onUpgrade(db, oldVersion, newVersion)
            }

            override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                UALog.d("Downgrading database %s from version %s to %s", db, oldVersion, newVersion)
                this@DataManager.onDowngrade(db, oldVersion, newVersion)
            }

            override fun onConfigure(db: SQLiteDatabase) {
                super.onConfigure(db)
                this@DataManager.onConfigure(db)
            }

            override fun onOpen(db: SQLiteDatabase) {
                super.onOpen(db)
                this@DataManager.onOpen(db)
            }
        }
    }

    /**
     * Called when the database connection is opened.
     *
     * @param db The database.
     */
    protected fun onOpen(db: SQLiteDatabase) { }

    /**
     * Called when the database connection is configured.
     *
     * @param db The database.
     */
    protected fun onConfigure(db: SQLiteDatabase) { }

    /**
     * Called when the database is created for the first time.
     *
     * @param db The newly created database
     */
    protected abstract fun onCreate(db: SQLiteDatabase)

    /**
     * Opens a writable database
     *
     * @return a writable SQLiteDatabase
     */
    protected fun getWritableDatabase(): SQLiteDatabase? {
        for (i in 0..<MAX_ATTEMPTS) {
            try {
                return openHelper.writableDatabase
            } catch (e: SQLiteException) {
                // It's very bad for the app if the DB cannot be opened, so it's worth
                // a sleep to wait for a lock to go away.

                SystemClock.sleep(100)
                UALog.e(e, "DataManager - Error opening writable database. Retrying...")
            }
        }

        return null
    }

    /**
     * Opens a readable database
     *
     * @return a readable SQLiteDatabase
     */
    protected fun getReadableDatabase(): SQLiteDatabase? {
        for (i in 0..<MAX_ATTEMPTS) {
            try {
                return openHelper.readableDatabase
            } catch (e: SQLiteException) {
                // It's very bad for the app if the DB cannot be opened, so it's worth
                // a sleep to wait for a lock to go away.

                SystemClock.sleep(100)
                UALog.e(e, "DataManager - Error opening readable database. Retrying...")
            }
        }

        return null
    }

    /**
     * Called when a database needs to be upgraded
     *
     * @param db The database to upgrade
     * @param oldVersion Version of the old database
     * @param newVersion Version of the new database
     */
    protected open fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        UALog.d("onUpgrade not implemented yet.")
    }

    /**
     * Called when a database needs to be downgraded
     *
     * @param db The database to downgrade
     * @param oldVersion Version of the old database
     * @param newVersion Version of the new database
     */
    protected open fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException("Unable to downgrade database")
    }

    /**
     * Deletes items from the database
     *
     * @param table Table to delete the value from
     * @param selection Optional WHERE statement
     * @param selectionArgs arguments to the WHERE clause
     * @return number of rows deleted, or -1 if an error occurred
     */
    fun delete(table: String, selection: String?, selectionArgs: Array<String>?): Int {
        // If the where clause is null (deletes all rows), set it to "1" so that the delete() call
        // will return the number of rows deleted rather than 0.
        val selection = selection ?: "1"

        val db = getWritableDatabase() ?: return -1

        for (i in 0..<MAX_ATTEMPTS) {
            try {
                return db.delete(table, selection, selectionArgs)
            } catch (ex: Exception) {
                UALog.e(ex, "Unable to delete item from a database")
            }
        }

        return -1
    }

    /**
     * Inserts several items into the database
     *
     * @param table Table to insert the values into
     * @param values An array of values to insert into the database
     * @return A list of the values inserted into the database
     */
    fun bulkInsert(table: String, values: Array<ContentValues>): List<ContentValues> {
        val db = getWritableDatabase() ?: return emptyList()
        val inserted = mutableListOf<ContentValues>()

        db.beginTransaction()
        for (value in values) {
            try {
                db.replaceOrThrow(table, null, value)
                inserted.add(value)
            } catch (ex: Exception) {
                UALog.e(ex, "Unable to insert into database")
                db.endTransaction()
                return emptyList()
            }
        }

        db.setTransactionSuccessful()
        db.endTransaction()

        return inserted.toList()
    }

    /**
     * Inserts an item into the data
     *
     * @param table Name of the table to insert the item into
     * @param values The values to insert into the database
     * @return Row id of the inserted values
     */
    fun insert(table: String, values: ContentValues?): Long {
        val db = getWritableDatabase() ?: return -1

        for (i in 0..<MAX_ATTEMPTS) {
            try {
                return db.replaceOrThrow(table, null, values)
            } catch (ex: Exception) {
                UALog.e(ex, "Unable to insert into database")
            }
        }

        return -1
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
    fun update(
        table: String, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int {
        val db = getWritableDatabase() ?: return -1

        for (i in 0..<MAX_ATTEMPTS) {
            try {
                return db.update(table, values, selection, selectionArgs)
            } catch (e: SQLException) {
                UALog.e(e, "Update Failed")
            }
        }

        return -1
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
    fun query(
        table: String,
        columns: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        limit: String? = null
    ): Cursor? {
        val db = getReadableDatabase() ?: return null

        for (i in 0..<MAX_ATTEMPTS) {
            try {
                return db.query(
                    table, columns, selection, selectionArgs, null, null, sortOrder, limit
                )
            } catch (e: SQLException) {
                UALog.e(e, "Query Failed")
            }
        }

        return null
    }

    /**
     * Queries the database with a raw SQL query
     *
     * @param query The SQL query
     * @param selectionArgs Arguments to the WHERE clause
     * @return A cursor with the query results, or null if anything went wrong
     */
    fun rawQuery(query: String, selectionArgs: Array<String>?): Cursor? {
        val db = getReadableDatabase() ?: return null

        for (i in 0..2) {
            try {
                return db.rawQuery(query, selectionArgs)
            } catch (e: SQLException) {
                UALog.e(e, "Query failed")
            }
        }

        return null
    }

    /**
     * Closes the connection to the database
     */
    fun close() {
        try {
            openHelper.close()
        } catch (ex: Exception) {
            UALog.e(ex, "Failed to close the database.")
        }
    }

    fun databaseExists(context: Context): Boolean {
        return context.getDatabasePath(path).exists()
    }

    fun deleteDatabase(context: Context): Boolean {
        try {
            return context.getDatabasePath(path).delete()
        } catch (e: Exception) {
            UALog.e(e, "Failed to delete database: $path")
            return false
        }
    }

    companion object {

        private const val DATABASE_DIRECTORY_NAME = "com.urbanairship.databases"

        private const val MAX_ATTEMPTS = 3

        /**
         * Tries to move the database to a prefixed name in the no backup directory.
         *
         * @param context The application context.
         * @param appKey The appKey.
         * @param name The database name.
         * @return The full path of the database.
         */
        protected fun migrateDatabase(context: Context, appKey: String, name: String): String {
            val targetName = appKey + "_" + name

            val urbanAirshipNoBackupDirectory = File(ContextCompat.getNoBackupFilesDir(context), DATABASE_DIRECTORY_NAME)
            if (!urbanAirshipNoBackupDirectory.exists() && !urbanAirshipNoBackupDirectory.mkdirs()) {
                UALog.e("Failed to create UA no backup directory.")
            }

            val target = File(urbanAirshipNoBackupDirectory, targetName)
            val sources = arrayOf( // Standard directory with the appKey prefix
                context.getDatabasePath(targetName),  // No backup directory with database name without appKey prefix

                File(
                    urbanAirshipNoBackupDirectory, name
                ),  // Standard directory without the appKey prefix

                context.getDatabasePath(name)
            )

            if (target.exists()) {
                return target.absolutePath
            }

            for (oldFile in sources) {
                if (!oldFile.exists()) {
                    continue
                }

                // Failed, we will get it next time
                if (!oldFile.renameTo(target)) {
                    return oldFile.absolutePath
                }

                // Move the journal file if it exists
                val journal = File(oldFile.absolutePath + "-journal")
                if (journal.exists()) {
                    if (!journal.renameTo(File(target.absolutePath + "-journal"))) {
                        UALog.e("Failed to move the journal file: $journal")
                    }
                }
            }

            return target.absolutePath
        }
    }
}
