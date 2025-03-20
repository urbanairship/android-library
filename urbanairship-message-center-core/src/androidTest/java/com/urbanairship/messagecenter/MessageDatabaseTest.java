package com.urbanairship.messagecenter;

import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.urbanairship.messagecenter.core.MessageDatabase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.urbanairship.messagecenter.core.MessageDatabase.BODY_URL;
import static com.urbanairship.messagecenter.core.MessageDatabase.DELETED;
import static com.urbanairship.messagecenter.core.MessageDatabase.EXPIRATION;
import static com.urbanairship.messagecenter.core.MessageDatabase.EXTRA;
import static com.urbanairship.messagecenter.core.MessageDatabase.KEY;
import static com.urbanairship.messagecenter.core.MessageDatabase.MESSAGE_ID;
import static com.urbanairship.messagecenter.core.MessageDatabase.MESSAGE_URL;
import static com.urbanairship.messagecenter.core.MessageDatabase.MIGRATION_2_5;
import static com.urbanairship.messagecenter.core.MessageDatabase.RAW_MESSAGE;
import static com.urbanairship.messagecenter.core.MessageDatabase.READ_URL;
import static com.urbanairship.messagecenter.core.MessageDatabase.TABLE_NAME;
import static com.urbanairship.messagecenter.core.MessageDatabase.TIMESTAMP;
import static com.urbanairship.messagecenter.core.MessageDatabase.TITLE;
import static com.urbanairship.messagecenter.core.MessageDatabase.UNREAD;
import static com.urbanairship.messagecenter.core.MessageDatabase.UNREAD_ORIG;
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
    public void migrate2to5() throws IOException {
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
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_2_5);

        // Validate migrated data has no dupes.
        assertEquals(5, messageCount(db));
        assertFalse(hasDuplicates(db));

        db.close();
    }

    @Test
    public void migrate3to5() throws IOException {
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
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_3_5);

        // Validate migrated table still has 5 messages.
        assertEquals(5, messageCount(db));
        List<Map<String, String>> migratedMessages = getMessages(db);

        assertEquals(initialMessages, migratedMessages);

        db.close();
    }

    @Test
    public void migrate4to5() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 4);

        // Insert some messages.
        for (int i = 0; i < 5; i++) {
            insertMessage(db, "msg-" + i);
        }

        // Sanity check.
        assertEquals(5, messageCount(db));

        List<Map<String, String>> initialMessages = getMessages(db);

        // Prepare for migration and run it.
        db.close();
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_4_5);

        // Validate migrated table now has 5 de-duped messages.
        assertEquals(5, messageCount(db));
        List<Map<String, String>> migratedMessages = getMessages(db);

        assertEquals(initialMessages, migratedMessages);

        db.close();
    }

    @Test
    public void migrateAll() throws IOException {
        // Skipping 1_2 because we didn't have an initial schema as that was the migration to Room.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 2);
        db.close();

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MessageDatabase messageDb = Room.databaseBuilder(context, MessageDatabase.class, TEST_DB)
            .addMigrations(MIGRATION_2_5)
            .build();

        messageDb.getOpenHelper().getWritableDatabase();
        messageDb.close();
    }

    /**
     * Mocks a v1 pre-room DB and attempts to migrate.
     * Version 1 didn't have RAW_MESSAGE or EXPIRATION columns, so the migration will fail and
     * recover by dropping and recreating the table.
     */
    @Test
    public void migratePreRoom1To5() throws IOException {
        SupportSQLiteDatabase db = mockPreRoomDatabase(TEST_DB, 1);

        // Insert some dupes that should be cleaned up by the migration.
        for (int i = 0; i < 5; i++) {
            String msgId = "msg-" + i;
            insertPreRoomMessage(db, 1, msgId);
            insertPreRoomMessage(db, 1, msgId);
        }

        // Sanity check.
        assertEquals(10, messageCount(db));
        assertTrue(hasDuplicates(db));

        // Prep for migration.
        db.close();

        // Attempt to run the migration.
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_1_5);

        // DB was recreated and should be empty.
        assertEquals(0, messageCount(db));
        assertEquals(5, db.getVersion());

        db.close();
    }

    /**
     * Mocks a v2 pre-room DB and attempts to migrate.
     *
     * Version 2 didn't have the EXPIRATION column, so the migration will fail and recover by
     * dropping and recreating the table.
     */
    @Test
    public void migratePreRoom2To5() throws IOException {
        SupportSQLiteDatabase db = mockPreRoomDatabase(TEST_DB, 2);

        // Insert some dupes that should be cleaned up by the migration.
        for (int i = 0; i < 5; i++) {
            String msgId = "msg-" + i;
            insertPreRoomMessage(db, 2, msgId);
            insertPreRoomMessage(db, 2, msgId);
        }

        // Sanity check.
        assertEquals(10, messageCount(db));
        assertTrue(hasDuplicates(db));

        // Prep for migration
        db.close();

        // Attempt to run the migration.
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_2_5);

        // DB was recreated and should be empty.
        assertEquals(0, messageCount(db));
        assertEquals(5, db.getVersion());
    }

    /**
     * Mocks a v3 pre-room DB and attempts to migrate.
     *
     * Version 3 had a similar schema to the current Room schemas, so the migration should succeed.
     */
    @Test
    public void migratePreRoom3To5() throws IOException {
        SupportSQLiteDatabase db = mockPreRoomDatabase(TEST_DB, 3);

        // Insert some dupes that should be cleaned up by the migration.
        for (int i = 0; i < 5; i++) {
            String msgId = "msg-" + i;
            insertPreRoomMessage(db, 3, msgId);
            insertPreRoomMessage(db, 3, msgId);
        }

        // Sanity check.
        assertEquals(10, messageCount(db));
        assertTrue(hasDuplicates(db));
        // Prep for migration
        db.close();

        // Attempt to run the migration.
        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_3_5);

        // DB should end up with 5 messages in it and no dupes.
        assertEquals(5, messageCount(db));
        assertEquals(5, db.getVersion());
        assertFalse(hasDuplicates(db));

        db.close();
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

        db.insert(TABLE_NAME, SQLiteDatabase.CONFLICT_NONE, cv);
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

    /**
     * Approximates the schema of the message db prior to the Room migration.
     * The only difference in the mock db is that it doesn't set a UNIQUE constraint on MESSAGE_ID,
     * to allow duplicate messages to be inserted in tests.
     */
    private SupportSQLiteDatabase mockPreRoomDatabase(String name, int version) {
        RoomDatabase roomDb = createPreRoomDatabase(
                ApplicationProvider.getApplicationContext(),
                InstrumentationRegistry.getInstrumentation(),
                name);
        SupportSQLiteDatabase db = roomDb.getOpenHelper().getWritableDatabase();

        db.execSQL("DROP TABLE " + TABLE_NAME);
        db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                + KEY + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + MESSAGE_ID + " TEXT, "
                + MESSAGE_URL + " TEXT, "
                + BODY_URL + " TEXT, "
                + READ_URL + " TEXT, "
                + TITLE + " TEXT, "
                + EXTRA + " TEXT, "
                + UNREAD + " INTEGER, "
                + UNREAD_ORIG + " INTEGER, "
                + DELETED + " INTEGER, "
                + TIMESTAMP + " TEXT " + ");"
        );

        // Alter the table to match the given pre-Room db version.
        switch (version) {
            case 3:
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + EXPIRATION + " TEXT;");
            case 2:
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + RAW_MESSAGE + " TEXT;");
            case 1:
                break;
        }

        db.setVersion(version);
        return db;
    }

    private static MessageDatabase createPreRoomDatabase(
            Context context,
            Instrumentation instrumentation,
            String name
    ) {
        File path = instrumentation.getTargetContext().getDatabasePath(name);
        if (path.exists()) {
            if (!path.delete()) {
                throw new IllegalStateException("Failed to create pre-Room database. " +
                        "Couldn't delete existing test db file!");
            }
        }
        return Room.databaseBuilder(context, MessageDatabase.class, path.getAbsolutePath()).build();
    }

    private static void insertPreRoomMessage(SupportSQLiteDatabase db, int dbVersion, String messageId) {
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

        // Include additional columns, based on the given version.
        switch (dbVersion) {
            case 3:
                cv.put(EXPIRATION, "0");
            case 2:
                cv.put(RAW_MESSAGE, "raw_message_object");
            case 1:
                break;
        }

        db.insert(TABLE_NAME, SQLiteDatabase.CONFLICT_NONE, cv);
    }
}
