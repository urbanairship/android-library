/* Copyright Airship and Contributors */
package com.urbanairship.analytics.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.urbanairship.analytics.data.AnalyticsDatabase
import com.urbanairship.json.JsonMap.Companion.newBuilder
import java.io.IOException
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsDatabaseMigrationTest {

    @Rule
    var helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AnalyticsDatabase::class.java
    )

    @Test
    fun migrate2to3() {
        var db = helper.createDatabase(TEST_DB, 2)

        // Insert duplicate events that should be cleaned up by the migration.
        for (i in 0..3) {
            insertEvent(db, "event-$i", "session-1")
            insertEvent(db, "event-$i", "session-1")
        }
        Assert.assertEquals(8, getEventCount(db).toLong())
        Assert.assertTrue(hasDuplicates(db))

        // Prepare for migration and run it.
        db.close()
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AnalyticsDatabase.MIGRATION_2_3)

        // Validate migrated data doesn't contain any duplicates.
        Assert.assertEquals(4, getEventCount(db).toLong())
        Assert.assertFalse(hasDuplicates(db))
    }

    companion object {

        private const val TEST_DB = "ua_analytics.db"

        private fun insertEvent(db: SupportSQLiteDatabase, eventId: String, sessionId: String) {
            val values = ContentValues()
            values.put("type", "test-event-type")
            values.put("eventId", eventId)
            values.put("time", "0")
            values.put("data", newBuilder().put("foo", "bar").build().toString())
            values.put("sessionId", sessionId)
            values.put("eventSize", 100)

            db.insert("events", SQLiteDatabase.CONFLICT_REPLACE, values)
        }

        /** Returns the number of rows in the events table.  */
        private fun getEventCount(db: SupportSQLiteDatabase): Int {
            val cursor = db.query("SELECT * FROM events")
            val count = cursor.count
            cursor.close()

            return count
        }

        /** Returns true if the events table contains rows with duplicate eventIds.  */
        private fun hasDuplicates(db: SupportSQLiteDatabase): Boolean {
            val cursor =
                db.query("SELECT COUNT(eventId) AS c FROM events GROUP BY eventId ORDER BY c DESC LIMIT 1")
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()

            return count > 1
        }
    }
}
