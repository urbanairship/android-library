/* Copyright Airship and Contributors */

package com.urbanairship.db;

import android.database.sqlite.SQLiteException;

import com.urbanairship.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Base class for a database migration that adds the ability to recover from errors that may
 * be thrown during execution.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class RecoverableMigration extends Migration {
    /**
     * Creates a new migration between {@code startVersion} and {@code endVersion}.
     *
     * @param startVersion The start version of the database.
     * @param endVersion The end version of the database after this migration is applied.
     */
    public RecoverableMigration(int startVersion, int endVersion) {
        super(startVersion, endVersion);
    }

    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
        Exception error = null;
        try {
            tryMigrate(db);
        } catch (Exception e) {
            Logger.debug(e, "Migration (%d to %d) failed!", startVersion, endVersion);
            error = e;
        }

        if (error != null) {
            Logger.debug("Attempting to recover (%d to %d) migration!", startVersion, endVersion);
            tryRecover(db, error);
        }
    }

    /**
     * Run the necessary migrations inside a try/catch block with a transaction. If any
     * Exceptions are thrown during execution, the transaction will be rolled back and the
     * caught error will be passed to
     * {@link #tryRecover(SupportSQLiteDatabase, Exception)} for fallback handling.
     * <p>
     * This class cannot access any generated Dao in this method.
     * <p>
     * This method is already called inside a transaction and that transaction might actually be a
     * composite transaction of all necessary {@code Migration}s.
     *
     * @param db The database instance
     */
    public abstract void tryMigrate(@NonNull SupportSQLiteDatabase db);

    /**
     * Fallback handler that will be invoked if an SQLiteException is thrown from
     * {@link #tryMigrate(SupportSQLiteDatabase)}. Implementations should attempt to recover in the
     * most graceful way possible.
     *
     * @param db The database instance.
     * @param err The error that caused the migration to fail.
     */
    public abstract void tryRecover(@NonNull SupportSQLiteDatabase db, @NonNull Exception err);
}
