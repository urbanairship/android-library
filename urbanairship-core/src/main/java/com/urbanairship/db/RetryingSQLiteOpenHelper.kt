package com.urbanairship.db

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.os.SystemClock
import androidx.annotation.RestrictTo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File

/**
 * [SupportSQLiteOpenHelper] implementation that wraps a delegate open helper and tries to
 * work around database open errors by retrying after a short delay. If the database was not
 * successfully opened after 5 attempts, the helper can be configured to delete the db file as
 * a last-ditch recovery option.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RetryingSQLiteOpenHelper public constructor(
    private val delegateOpenHelper: SupportSQLiteOpenHelper,
    private val allowDataLoss: Boolean
) : SupportSQLiteOpenHelper {

    /** Lock to synchronize access to db open methods.  */
    private val lock = Any()

    public class Factory public constructor(
        private val factoryDelegate: SupportSQLiteOpenHelper.Factory,
        private val allowDataLoss: Boolean
    ) : SupportSQLiteOpenHelper.Factory {

        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            return RetryingSQLiteOpenHelper(factoryDelegate.create(configuration), allowDataLoss)
        }
    }

    override val databaseName: String?
        get() = delegateOpenHelper.databaseName

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        delegateOpenHelper.setWriteAheadLoggingEnabled(enabled)
    }

    override val writableDatabase: SupportSQLiteDatabase
        get() {
            synchronized(lock) {
                return getDatabaseWithRetries(true)
            }
        }

    override val readableDatabase: SupportSQLiteDatabase
        get() {
            synchronized(lock) {
                return getDatabaseWithRetries(false)
            }
        }

    override fun close() {
        delegateOpenHelper.close()
    }

    private fun getDatabaseWithRetries(writable: Boolean): SupportSQLiteDatabase {
        synchronized(lock) {
            val name = databaseName
            // Ensure the DB dir exists on disk.
            if (name != null) {
                val dbFile = File(databaseName)
                val parentFile = dbFile.parentFile
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs()
                }
            }

            // Retry up to the last attempt.
            for (i in 0..<MAX_ATTEMPTS - 1) {
                try {
                    return getDatabase(writable)
                } catch (e: Exception) {
                    // Clean up, so we can try again.
                    tryClose()
                }
                // Wait (hopefully) long enough for any locks or slow I/O issues to resolve.
                SystemClock.sleep(REOPEN_DELAY)
            }

            // Last attempt before we consider attempting recovery.
            val error = try {
                return getDatabase(writable)
            } catch (e: Exception) {
                // Clean up and hang onto the error.
                tryClose()
                e
            }

            // If we're creating an in-memory DB (null name) or don't want to lose data, re-throw.
            if (name == null || !allowDataLoss) {
                // This mimics the behavior of Room's SneakyThrow.
                throw RuntimeException(error)
            }

            if (getOpenException(error) != null) {
                // If we suspect that the db file is corrupt, delete it.
                tryDeleteDatabase()
            }

            // Try one final time, letting any exceptions be thrown.
            return getDatabase(writable)
        }
    }

    private fun getDatabase(writable: Boolean): SupportSQLiteDatabase {
        return if (writable) {
            delegateOpenHelper.writableDatabase
        } else {
            delegateOpenHelper.readableDatabase
        }
    }

    private fun tryClose() {
        try {
            close()
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun tryDeleteDatabase() {
        val name = databaseName ?: return

        try {
            File(name).delete()
        } catch (e: Exception) {
            // Ignored
        }
    }

    internal companion object {

        private const val MAX_ATTEMPTS = 5
        private const val REOPEN_DELAY = 350L

        /** Returns the SQLiteCantOpenDatabaseException or SQLiteDatabaseLockedException if present.  */
        private fun getOpenException(error: Exception): SQLiteException? {
            var openException: SQLiteException? = null
            if (error.cause is SQLiteCantOpenDatabaseException) {
                openException = error.cause as SQLiteCantOpenDatabaseException?
            } else if (error is SQLiteCantOpenDatabaseException) {
                openException = error
            } else if (error.cause is SQLiteDatabaseLockedException) {
                openException = error.cause as SQLiteDatabaseLockedException?
            } else if (error is SQLiteDatabaseLockedException) {
                openException = error
            }
            return openException
        }
    }
}
