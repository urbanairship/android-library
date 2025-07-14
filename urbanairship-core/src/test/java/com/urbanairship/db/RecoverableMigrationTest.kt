package com.urbanairship.db

import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RecoverableMigrationTest {

    private val mockDb: SupportSQLiteDatabase = mockk(relaxed = true)
    private val mockCursor: Cursor = mockk()
    private val migration = TestRecoverableMigration()

    @Test
    public fun testSuccessfulMigration() {
        migration.migrate(mockDb)
        // No errors during migration, so we shouldn't have called tryRecover()
        verify(exactly = 1) { mockDb.query(PRAGMA_MIGRATE) }
        verify(exactly = 0) { mockDb.query(PRAGMA_RECOVER) }
    }

    @Test
    public fun testRecoverFromMigrationError() {
        every { mockDb.query(PRAGMA_MIGRATE) } throws ERROR
        every { mockDb.query(PRAGMA_RECOVER) } returns mockCursor

        migration.migrate(mockDb)
        // Error during migration! Ensure we got into tryRecover()
        verify(exactly = 1) { mockDb.query(PRAGMA_MIGRATE) }
        verify(exactly = 1) { mockDb.query(PRAGMA_RECOVER) }
    }

    @Test(expected = SQLiteException::class)
    public fun testThrowsOnRecoverError() {
        every { mockDb.query(PRAGMA_MIGRATE) } throws ERROR
        every { mockDb.query(PRAGMA_RECOVER) } throws ERROR

        migration.migrate(mockDb)
        // Error during migration! Ensure we got into tryRecover(), where there'll also be an error.
        verify(exactly = 1) { mockDb.query(PRAGMA_MIGRATE) }
        verify(exactly = 1) { mockDb.query(PRAGMA_RECOVER) }
    }

    private class TestRecoverableMigration : RecoverableMigration(1, 2) {

        override fun tryMigrate(db: SupportSQLiteDatabase) {
            db.query(PRAGMA_MIGRATE)
        }

        override fun tryRecover(db: SupportSQLiteDatabase, err: Exception) {
            db.query(PRAGMA_RECOVER)
        }
    }

    internal companion object {
        // Valid, but garbage SQL commands, used to signal what happened in the test migration.
        private val PRAGMA_MIGRATE = "PRAGMA migrate;"
        private val PRAGMA_RECOVER = "PRAGMA recover;"
        private val ERROR: Exception = SQLiteException("oops")
    }
}
