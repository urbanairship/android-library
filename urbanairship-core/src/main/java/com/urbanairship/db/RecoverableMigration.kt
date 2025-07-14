/* Copyright Airship and Contributors */
package com.urbanairship.db

import androidx.annotation.RestrictTo
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.urbanairship.UALog

/**
 * Base class for a database migration that adds the ability to recover from errors that may
 * be thrown during execution.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class RecoverableMigration
/**
 * Creates a new migration between `startVersion` and `endVersion`.
 *
 * @param startVersion The start version of the database.
 * @param endVersion The end version of the database after this migration is applied.
 */
public constructor(startVersion: Int, endVersion: Int) : Migration(startVersion, endVersion) {

    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            tryMigrate(db)
        } catch (e: Exception) {
            UALog.d(e, "Migration ($startVersion to $endVersion) failed!")
            UALog.d("Attempting to recover ($startVersion to $endVersion) migration!")
            tryRecover(db, e)
        }
    }

    /**
     * Run the necessary migrations inside a try/catch block with a transaction. If any
     * Exceptions are thrown during execution, the transaction will be rolled back and the
     * caught error will be passed to
     * [tryRecover] for fallback handling.
     *
     *
     * This class cannot access any generated Dao in this method.
     *
     *
     * This method is already called inside a transaction and that transaction might actually be a
     * composite transaction of all necessary [Migration]'s.
     *
     * @param db The database instance
     */
    public abstract fun tryMigrate(db: SupportSQLiteDatabase)

    /**
     * Fallback handler that will be invoked if an [android.database.sqlite.SQLiteException] is thrown from
     * [tryMigrate]. Implementations should attempt to recover in the
     * most graceful way possible.
     *
     * @param db The database instance.
     * @param err The error that caused the migration to fail.
     */
    public abstract fun tryRecover(db: SupportSQLiteDatabase, err: Exception)
}
