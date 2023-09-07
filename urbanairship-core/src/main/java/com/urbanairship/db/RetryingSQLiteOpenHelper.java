package com.urbanairship.db;

import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteException;
import android.os.SystemClock;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

/**
 * {@code SupportSQLiteOpenHelper} implementation that wraps a delegate open helper and tries to
 * work around database open errors by retrying after a short delay. If the database was not
 * successfully opened after 5 attempts, the helper can be configured to delete the db file as
 * a last-ditch recovery option.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RetryingSQLiteOpenHelper implements SupportSQLiteOpenHelper {
    private static final int MAX_ATTEMPTS = 5;
    private static final long REOPEN_DELAY = 350L;

    /** Lock to synchronize access to db open methods. */
    private final Object lock = new Object();

    @NonNull
    private final SupportSQLiteOpenHelper delegateOpenHelper;
    private final boolean allowDataLoss;

    public RetryingSQLiteOpenHelper(
            @NonNull SupportSQLiteOpenHelper supportSQLiteOpenHelper,
            boolean allowDataLoss
    ) {
        this.delegateOpenHelper = supportSQLiteOpenHelper;
        this.allowDataLoss = allowDataLoss;
    }

    public static class Factory implements SupportSQLiteOpenHelper.Factory {
        @NonNull
        private final SupportSQLiteOpenHelper.Factory factoryDelegate;
        private final boolean allowDataLoss;

        public Factory(@NonNull SupportSQLiteOpenHelper.Factory factory, boolean allowDataLoss) {
            this.factoryDelegate = factory;
            this.allowDataLoss = allowDataLoss;
        }

        @NonNull
        @Override
        public SupportSQLiteOpenHelper create(@NonNull Configuration configuration) {
            return new RetryingSQLiteOpenHelper(factoryDelegate.create(configuration), allowDataLoss);
        }
    }

    @Nullable
    @Override
    public String getDatabaseName() {
        return delegateOpenHelper.getDatabaseName();
    }

    @Override
    public void setWriteAheadLoggingEnabled(boolean enabled) {
        delegateOpenHelper.setWriteAheadLoggingEnabled(enabled);
    }

    @NonNull
    @Override
    public SupportSQLiteDatabase getWritableDatabase() {
        synchronized (lock) {
            return getDatabaseWithRetries(true);
        }
    }

    @NonNull
    @Override
    public SupportSQLiteDatabase getReadableDatabase() {
        synchronized (lock) {
            return getDatabaseWithRetries(false);
        }
    }

    @Override
    public void close() {
        delegateOpenHelper.close();
    }

    private SupportSQLiteDatabase getDatabaseWithRetries(boolean writable) {
        synchronized (lock) {
            String name = getDatabaseName();
            // Ensure the DB dir exists on disk.
            if (name != null) {
                File dbFile = new File(getDatabaseName());
                File parentFile = dbFile.getParentFile();
                if (parentFile != null && !parentFile.exists()) {
                    parentFile.mkdirs();
                }
            }

            // Retry up to the last attempt.
            for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
                try {
                    return getDatabase(writable);
                } catch (Exception e) {
                    // Clean up, so we can try again.
                    tryClose();
                }
                // Wait (hopefully) long enough for any locks or slow I/O issues to resolve.
                SystemClock.sleep(REOPEN_DELAY);
            }

            // Last attempt before we consider attempting recovery.
            final Exception error;
            try {
                return getDatabase(writable);
            } catch (Exception e) {
                // Clean up and hang onto the error.
                tryClose();
                error = e;
            }

            // If we're creating an in-memory DB (null name) or don't want to lose data, re-throw.
            if (name == null || !allowDataLoss) {
                // This mimics the behavior of Room's SneakyThrow.
                throw new RuntimeException(error);
            }

            if (getOpenException(error) != null) {
                // If we suspect that the db file is corrupt, delete it.
                tryDeleteDatabase();
            }

            // Try one final time, letting any exceptions be thrown.
            return getDatabase(writable);
        }
    }

    /** Returns the SQLiteCantOpenDatabaseException or SQLiteDatabaseLockedException if present. */
    private static SQLiteException getOpenException(Exception error) {
        SQLiteException openException = null;
        if (error.getCause() instanceof SQLiteCantOpenDatabaseException) {
            openException = (SQLiteCantOpenDatabaseException) error.getCause();
        } else if (error instanceof SQLiteCantOpenDatabaseException) {
            openException = (SQLiteCantOpenDatabaseException) error;
        } else if (error.getCause() instanceof SQLiteDatabaseLockedException) {
            openException = (SQLiteDatabaseLockedException) error.getCause();
        } else if (error instanceof SQLiteDatabaseLockedException) {
            openException = (SQLiteDatabaseLockedException) error;
        }
        return openException;
    }

    private SupportSQLiteDatabase getDatabase(boolean writable) {
        if (writable) {
            return delegateOpenHelper.getWritableDatabase();
        } else {
            return delegateOpenHelper.getReadableDatabase();
        }
    }

    private void tryClose() {
        try {
            close();
        } catch (Exception e) {
            // Ignored
        }
    }

    private void tryDeleteDatabase() {
        String name = getDatabaseName();
        if (name != null) {
            File dbFile = new File(name);
            try {
                dbFile.delete();
            } catch (Exception e) {
                // Ignored
            }
        }
    }
}
