/* Copyright Airship and Contributors */

package com.urbanairship.analytics.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.urbanairship.json.JsonMap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AnalyticsDatabaseMigrationTest {
    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AnalyticsDatabase.class
    );

    private static final String TEST_DB = "ua_analytics.db";

    @Test
    public void migrate2to3() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);

        // Insert duplicate events that should be cleaned up by the migration.
        for (int i = 0; i < 4; i++) {
            insertEvent(db, "event-" + i, "session-1");
            insertEvent(db, "event-" + i, "session-1");
        }
        assertEquals(8, getEventCount(db));
        assertTrue(hasDuplicates(db));

        // Prepare for migration and run it.
        db.close();
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AnalyticsDatabase.MIGRATION_2_3);

        // Validate migrated data doesn't contain any duplicates.
        assertEquals(4, getEventCount(db));
        assertFalse(hasDuplicates(db));
    }

    @SuppressWarnings("SameParameterValue")
    private static void insertEvent(SupportSQLiteDatabase db, String eventId, String sessionId) {
        ContentValues values = new ContentValues();
        values.put("type", "test-event-type");
        values.put("eventId", eventId);
        values.put("time", "0");
        values.put("data", JsonMap.newBuilder().put("foo", "bar").build().toString());
        values.put("sessionId", sessionId);
        values.put("eventSize", 100);

        db.insert("events", SQLiteDatabase.CONFLICT_REPLACE, values);
    }

    /** Returns the number of rows in the events table. */
    private static int getEventCount(SupportSQLiteDatabase db) {
        Cursor cursor = db.query("SELECT * FROM events");
        int count = cursor.getCount();
        cursor.close();

        return count;
    }

    /** Returns true if the events table contains rows with duplicate eventIds. */
    private static boolean hasDuplicates(SupportSQLiteDatabase db) {
        Cursor cursor = db.query("SELECT COUNT(eventId) AS c FROM events GROUP BY eventId ORDER BY c DESC LIMIT 1");
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();

        return count > 1;
    }
}
