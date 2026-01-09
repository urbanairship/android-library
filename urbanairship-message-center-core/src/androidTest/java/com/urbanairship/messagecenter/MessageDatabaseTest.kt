package com.urbanairship.messagecenter

import android.app.Instrumentation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageDatabaseTest {

    @Rule
    public fun getHelper(): MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), MessageDatabase::class.java
    )

    @Test
    fun migrate2to5() {
        var db = getHelper().createDatabase(TEST_DB, 2)

        // Insert some dupes that should be cleaned up by the migration.
        for (i in 0..4) {
            val msgId = "msg-$i"
            insertMessage(db, msgId)
            insertMessage(db, msgId)
        }

        // Sanity check.
        Assert.assertEquals(10, messageCount(db).toLong())
        Assert.assertTrue(hasDuplicates(db))

        // Prepare for migration and run it.
        db.close()
        db = getHelper().runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_2_5)

        // Validate migrated data has no dupes.
        Assert.assertEquals(5, messageCount(db).toLong())
        Assert.assertFalse(hasDuplicates(db))

        db.close()
    }

    @Test
    fun migrate3to5() {
        var db = getHelper().createDatabase(TEST_DB, 3)

        // Insert some messages.
        for (i in 0..4) {
            insertMessage(db, "msg-$i")
        }

        // Sanity check.
        Assert.assertEquals(5, messageCount(db).toLong())

        val initialMessages = getMessages(db)

        // Prepare for migration and run it.
        db.close()
        db = getHelper().runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_3_5)

        // Validate migrated table still has 5 messages.
        Assert.assertEquals(5, messageCount(db).toLong())
        Assert.assertEquals(initialMessages, getMessages(db))

        db.close()
    }

    @Test
    fun migrate4to5() {
        var db = getHelper().createDatabase(TEST_DB, 4)

        // Insert some messages.
        for (i in 0..4) {
            insertMessage(db, "msg-$i")
        }

        // Sanity check.
        Assert.assertEquals(5, messageCount(db).toLong())

        val initialMessages = getMessages(db)

        // Prepare for migration and run it.
        db.close()
        db = getHelper().runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_4_5)

        // Validate migrated table now has 5 de-duped messages.
        Assert.assertEquals(5, messageCount(db).toLong())
        Assert.assertEquals(initialMessages, getMessages(db))

        db.close()
    }

    @Test
    fun migrateAll() {
        // Skipping 1_2 because we didn't have an initial schema as that was the migration to Room.
        val db = getHelper().createDatabase(TEST_DB, 2)
        db.close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val messageDb = databaseBuilder(context, MessageDatabase::class.java, TEST_DB)
            .addMigrations(MessageDatabase.Companion.MIGRATION_2_5)
            .build()

        messageDb.openHelper.writableDatabase
        messageDb.close()
    }

    /**
     * Mocks a v1 pre-room DB and attempts to migrate.
     * Version 1 didn't have RAW_MESSAGE or EXPIRATION columns, so the migration will fail and
     * recover by dropping and recreating the table.
     */
    @Test
    fun migratePreRoom1To5() {
        var db = mockPreRoomDatabase(TEST_DB, 1)

        // Insert some dupes that should be cleaned up by the migration.
        for (i in 0..4) {
            val msgId = "msg-$i"
            insertPreRoomMessage(db, 1, msgId)
            insertPreRoomMessage(db, 1, msgId)
        }

        // Sanity check.
        Assert.assertEquals(10, messageCount(db).toLong())
        Assert.assertTrue(hasDuplicates(db))

        // Prep for migration.
        db.close()

        // Attempt to run the migration.
        db = getHelper().runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_1_5)

        // DB was recreated and should be empty.
        Assert.assertEquals(0, messageCount(db).toLong())
        Assert.assertEquals(5, db.version.toLong())

        db.close()
    }

    /**
     * Mocks a v2 pre-room DB and attempts to migrate.
     *
     * Version 2 didn't have the EXPIRATION column, so the migration will fail and recover by
     * dropping and recreating the table.
     */
    @Test
    fun migratePreRoom2To5() {
        var db = mockPreRoomDatabase(TEST_DB, 2)

        // Insert some dupes that should be cleaned up by the migration.
        for (i in 0..4) {
            val msgId = "msg-$i"
            insertPreRoomMessage(db, 2, msgId)
            insertPreRoomMessage(db, 2, msgId)
        }

        // Sanity check.
        Assert.assertEquals(10, messageCount(db).toLong())
        Assert.assertTrue(hasDuplicates(db))

        // Prep for migration
        db.close()

        // Attempt to run the migration.
        db = getHelper().runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_2_5)

        // DB was recreated and should be empty.
        Assert.assertEquals(0, messageCount(db).toLong())
        Assert.assertEquals(5, db.version.toLong())
    }

    /**
     * Mocks a v3 pre-room DB and attempts to migrate.
     *
     * Version 3 had a similar schema to the current Room schemas, so the migration should succeed.
     */
    @Test
    fun migratePreRoom3To5() {
        var db = mockPreRoomDatabase(TEST_DB, 3)

        // Insert some dupes that should be cleaned up by the migration.
        for (i in 0..4) {
            val msgId = "msg-$i"
            insertPreRoomMessage(db, 3, msgId)
            insertPreRoomMessage(db, 3, msgId)
        }

        // Sanity check.
        Assert.assertEquals(10, messageCount(db).toLong())
        Assert.assertTrue(hasDuplicates(db))
        // Prep for migration
        db.close()

        // Attempt to run the migration.
        db = getHelper().runMigrationsAndValidate(TEST_DB, 5, true, MessageDatabase.MIGRATION_3_5)

        // DB should end up with 5 messages in it and no dupes.
        Assert.assertEquals(5, messageCount(db).toLong())
        Assert.assertEquals(5, db.version.toLong())
        Assert.assertFalse(hasDuplicates(db))

        db.close()
    }

    /**
     * Approximates the schema of the message db prior to the Room migration.
     * The only difference in the mock db is that it doesn't set a UNIQUE constraint on MESSAGE_ID,
     * to allow duplicate messages to be inserted in tests.
     */
    private fun mockPreRoomDatabase(name: String, version: Int): SupportSQLiteDatabase {
        val roomDb: RoomDatabase = createPreRoomDatabase(
            ApplicationProvider.getApplicationContext(),
            InstrumentationRegistry.getInstrumentation(),
            name
        )
        val db = roomDb.openHelper.writableDatabase

        db.execSQL("DROP TABLE " + MessageDatabase.Companion.TABLE_NAME)
        db.execSQL(
            ("CREATE TABLE " + MessageDatabase.Companion.TABLE_NAME + " (" + MessageDatabase.Companion.KEY + " INTEGER PRIMARY KEY AUTOINCREMENT, " + MessageDatabase.Companion.MESSAGE_ID + " TEXT, " + MessageDatabase.Companion.MESSAGE_URL + " TEXT, " + MessageDatabase.Companion.BODY_URL + " TEXT, " + MessageDatabase.Companion.READ_URL + " TEXT, " + MessageDatabase.Companion.TITLE + " TEXT, " + MessageDatabase.Companion.EXTRA + " TEXT, " + MessageDatabase.Companion.UNREAD + " INTEGER, " + MessageDatabase.Companion.UNREAD_ORIG + " INTEGER, " + MessageDatabase.Companion.DELETED + " INTEGER, " + MessageDatabase.Companion.TIMESTAMP + " TEXT " + ");")
        )

        // Alter the table to match the given pre-Room db version.
        when (version) {
            3 -> {
                db.execSQL("ALTER TABLE " + MessageDatabase.Companion.TABLE_NAME + " ADD COLUMN " + MessageDatabase.Companion.EXPIRATION + " TEXT;")
                db.execSQL("ALTER TABLE " + MessageDatabase.Companion.TABLE_NAME + " ADD COLUMN " + MessageDatabase.Companion.RAW_MESSAGE + " TEXT;")
            }

            2 -> db.execSQL("ALTER TABLE " + MessageDatabase.Companion.TABLE_NAME + " ADD COLUMN " + MessageDatabase.Companion.RAW_MESSAGE + " TEXT;")
            1 -> {}
        }

        db.version = version
        return db
    }

    companion object {

        private const val TEST_DB = "ua_richpush.db"

        private fun insertMessage(db: SupportSQLiteDatabase, messageId: String?) {
            val cv = ContentValues()
            cv.put(MessageDatabase.Companion.MESSAGE_ID, messageId)
            cv.put(MessageDatabase.Companion.MESSAGE_URL, "message_url")
            cv.put(MessageDatabase.Companion.BODY_URL, "message_body_url")
            cv.put(MessageDatabase.Companion.READ_URL, "message_read_url")
            cv.put(MessageDatabase.Companion.TITLE, "title")
            cv.put(MessageDatabase.Companion.EXTRA, "extra")
            cv.put(MessageDatabase.Companion.UNREAD, 1)
            cv.put(MessageDatabase.Companion.UNREAD_ORIG, 1)
            cv.put(MessageDatabase.Companion.DELETED, 0)
            cv.put(MessageDatabase.Companion.TIMESTAMP, "0")
            cv.put(MessageDatabase.Companion.RAW_MESSAGE, "raw_message_object")
            cv.put(MessageDatabase.Companion.EXPIRATION, "0")

            db.insert(MessageDatabase.Companion.TABLE_NAME, SQLiteDatabase.CONFLICT_NONE, cv)
        }

        private fun hasDuplicates(db: SupportSQLiteDatabase): Boolean {
            val cursor = db.query(
                "SELECT COUNT(" + MessageDatabase.Companion.MESSAGE_ID + ") AS c " + "FROM " + MessageDatabase.Companion.TABLE_NAME + " " + "GROUP BY " + MessageDatabase.Companion.MESSAGE_ID + " " + "ORDER BY c DESC " + "LIMIT 1"
            )
            cursor.moveToFirst()
            val count = cursor.getInt(0)
            cursor.close()

            return count > 1
        }

        private fun messageCount(db: SupportSQLiteDatabase): Int {
            val cursor = db.query("SELECT * FROM " + MessageDatabase.Companion.TABLE_NAME)
            val count = cursor.count
            cursor.close()

            return count
        }

        /** Query all messages in the database.  */
        private fun getMessages(db: SupportSQLiteDatabase): List<Map<String, String>> {
            val cursor = db.query(
                "SELECT * FROM " + MessageDatabase.Companion.TABLE_NAME + " " + "ORDER BY " + MessageDatabase.Companion.TIMESTAMP + " DESC"
            )
            cursor.moveToFirst()
            val messages = mutableListOf<Map<String, String>>()
            for (i in 0..<cursor.count) {
                messages.add(cursorToMap(cursor))
                cursor.moveToNext()
            }
            cursor.close()

            return messages
        }

        private fun cursorToMap(cursor: Cursor): Map<String, String> {
            return cursor.columnNames.associateWith {
                cursor.getString(cursor.getColumnIndex(it))
            }
        }

        private fun createPreRoomDatabase(
            context: Context, instrumentation: Instrumentation, name: String
        ): MessageDatabase {
            val path = instrumentation.targetContext.getDatabasePath(name)
            if (path.exists()) {
                check(path.delete()) {
                    "Failed to create pre-Room database. " + "Couldn't delete existing test db file!"
                }
            }
            return databaseBuilder(context, MessageDatabase::class.java, path.absolutePath)
                .build()
        }

        private fun insertPreRoomMessage(
            db: SupportSQLiteDatabase, dbVersion: Int, messageId: String
        ) {
            val cv = ContentValues()
            cv.put(MessageDatabase.Companion.MESSAGE_ID, messageId)
            cv.put(MessageDatabase.Companion.MESSAGE_URL, "message_url")
            cv.put(MessageDatabase.Companion.BODY_URL, "message_body_url")
            cv.put(MessageDatabase.Companion.READ_URL, "message_read_url")
            cv.put(MessageDatabase.Companion.TITLE, "title")
            cv.put(MessageDatabase.Companion.EXTRA, "extra")
            cv.put(MessageDatabase.Companion.UNREAD, 1)
            cv.put(MessageDatabase.Companion.UNREAD_ORIG, 1)
            cv.put(MessageDatabase.Companion.DELETED, 0)
            cv.put(MessageDatabase.Companion.TIMESTAMP, "0")

            // Include additional columns, based on the given version.
            when (dbVersion) {
                3 -> {
                    cv.put(MessageDatabase.Companion.EXPIRATION, "0")
                    cv.put(MessageDatabase.Companion.RAW_MESSAGE, "raw_message_object")
                }

                2 -> cv.put(MessageDatabase.Companion.RAW_MESSAGE, "raw_message_object")
                1 -> {}
            }

            db.insert(MessageDatabase.Companion.TABLE_NAME, SQLiteDatabase.CONFLICT_NONE, cv)
        }
    }
}
