package com.urbanairship.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class RecoverableMigrationTest {
    // Valid, but garbage SQL commands, used to signal what happened in the test migration.
    private static final String PRAGMA_MIGRATE = "PRAGMA migrate;";
    private static final String PRAGMA_RECOVER = "PRAGMA recover;";
    private static final Exception ERROR = new SQLiteException("oops");

    private SupportSQLiteDatabase mockDb = mock(SupportSQLiteDatabase.class);
    private Cursor mockCursor = mock(Cursor.class);

    private TestRecoverableMigration migration = new TestRecoverableMigration();

    @Test
    public void testSuccessfulMigration() {
        migration.migrate(mockDb);
        // No errors during migration, so we shouldn't have called tryRecover()
        verify(mockDb, times(1)).query(eq(PRAGMA_MIGRATE));
        verify(mockDb, never()).query(eq(PRAGMA_RECOVER));
    }

    @Test
    public void testRecoverFromMigrationError() {
        when(mockDb.query(PRAGMA_MIGRATE)).thenThrow(ERROR);
        when(mockDb.query(PRAGMA_RECOVER)).thenReturn(mockCursor);

        migration.migrate(mockDb);
        // Error during migration! Ensure we got into tryRecover()
        verify(mockDb, times(1)).query(eq(PRAGMA_MIGRATE));
        verify(mockDb, times(1)).query(eq(PRAGMA_RECOVER));
    }

    @Test(expected = SQLiteException.class)
    public void testThrowsOnRecoverError() {
        when(mockDb.query(PRAGMA_MIGRATE)).thenThrow(ERROR);
        when(mockDb.query(PRAGMA_RECOVER)).thenThrow(ERROR);

        migration.migrate(mockDb);
        // Error during migration! Ensure we got into tryRecover(), where there'll also be an error.
        verify(mockDb, times(1)).query(eq(PRAGMA_MIGRATE));
        verify(mockDb, times(1)).query(eq(PRAGMA_RECOVER));
    }

    private static class TestRecoverableMigration extends RecoverableMigration {
        TestRecoverableMigration() {
            super(1, 2);
        }

        @Override
        public void tryMigrate(@NonNull SupportSQLiteDatabase db) {
            db.query(PRAGMA_MIGRATE);
        }

        @Override
        public void tryRecover(@NonNull SupportSQLiteDatabase db, @NonNull Exception err) {
            db.query(PRAGMA_RECOVER);
        }
    }
}
