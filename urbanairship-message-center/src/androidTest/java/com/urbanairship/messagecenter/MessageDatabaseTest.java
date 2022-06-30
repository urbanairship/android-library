package com.urbanairship.messagecenter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.urbanairship.messagecenter.MessageDatabase.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MessageDatabaseTest {
    private static final String TEST_DB = "ua_richpush.db";

    @Rule
    public MigrationTestHelper helper =
            new MigrationTestHelper(getInstrumentation(), MessageDatabase.class);

    @Test
    public void migrate2to3() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);

        // Insert some dupes that should be cleaned up by the migration.
        for (int i = 0; i < 5; i++) {
            String msgId = "msg-" + i;
            insertMessage(db, msgId);
            insertMessage(db, msgId);
        }

        // Sanity check.
        assertEquals(10, messageCount(db));
        assertTrue(hasDuplicates(db));

        // Prepare for migration and run it.
        db.close();
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MessageDatabase.MIGRATION_2_3);

        // Validate migrated data has no dupes.
        assertEquals(5, messageCount(db));
        assertFalse(hasDuplicates(db));

        db.close();
    }

    @Test
    public void migrate3to4() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 3);

        // Insert some messages.
        for (int i = 0; i < 5; i++) {
            insertMessage(db, "msg-" + i);
        }

        // Sanity check.
        assertEquals(5, messageCount(db));

        List<Map<String, String>> initialMessages = getMessages(db);

        // Prepare for migration and run it.
        db.close();
        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MessageDatabase.MIGRATION_3_4);

        // Validate migrated table still has 5 messages.
        assertEquals(5, messageCount(db));
        List<Map<String, String>> migratedMessages = getMessages(db);

        assertEquals(initialMessages, migratedMessages);

        db.close();
    }

    @Test
    public void migrateAll() throws IOException {
        // Skipping 1_2 because we don't have an initial schema as that was the migration to Room.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        db.close();

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MessageDatabase messageDb = Room.databaseBuilder(context, MessageDatabase.class, TEST_DB)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .build();

        messageDb.getOpenHelper().getWritableDatabase();
        messageDb.close();
    }

    private static void insertMessage(SupportSQLiteDatabase db, String messageId) {
        ContentValues cv = new ContentValues();
        cv.put(MESSAGE_ID, messageId);
        cv.put(MESSAGE_URL, "message_url");
        cv.put(BODY_URL, "message_body_url");
        cv.put(READ_URL, "message_read_url");
        cv.put(TITLE, "title");
        cv.put(EXTRA, "extra");
        cv.put(UNREAD, 1);
        cv.put(UNREAD_ORIG, 1);
        cv.put(DELETED, 0);
        cv.put(TIMESTAMP, "0");
        cv.put(RAW_MESSAGE, "raw_message_object");
        cv.put(EXPIRATION, "0");

        db.insert(TABLE_NAME, SQLiteDatabase.CONFLICT_FAIL, cv);
    }

    private static boolean hasDuplicates(SupportSQLiteDatabase db) {
        Cursor cursor = db.query(
            "SELECT COUNT(" + MESSAGE_ID + ") AS c " +
            "FROM " + TABLE_NAME + " " +
            "GROUP BY " + MESSAGE_ID + " " +
            "ORDER BY c DESC " +
            "LIMIT 1"
        );
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();

        return count > 1;
    }

    private static int messageCount(SupportSQLiteDatabase db) {
        Cursor cursor = db.query("SELECT * FROM " + TABLE_NAME);
        int count = cursor.getCount();
        cursor.close();

        return count;
    }

    /** Query all messages in the database. */
    private static List<Map<String, String>> getMessages(SupportSQLiteDatabase db) {
        Cursor cursor = db.query(
            "SELECT * FROM " + TABLE_NAME + " " +
            "ORDER BY " + TIMESTAMP + " DESC"
        );
        cursor.moveToFirst();
        List<Map<String, String>> messages = new ArrayList<>();
        for (int i = 0; i < cursor.getCount(); i++) {
            messages.add(cursorToMap(cursor));
            cursor.moveToNext();
        }
        cursor.close();

        return messages;
    }

    private static Map<String, String> cursorToMap(Cursor cursor) {
        Map<String, String> map = new HashMap<>();
        for (String column : cursor.getColumnNames()) {
            map.put(column, cursor.getString(cursor.getColumnIndex(column)));
        }
        return map;
    }
}
