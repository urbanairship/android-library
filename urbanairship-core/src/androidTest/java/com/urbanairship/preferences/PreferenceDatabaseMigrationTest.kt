/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceDatabaseMigrationTest {

    @Rule
    var helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), PreferenceDatabase::class.java
    )

    /**
     * The 2→3 migration adds a `lazy` column with `DEFAULT 0`. Existing rows must survive and
     * be readable as eager (`lazy = 0`).
     */
    @Test
    fun migrate2to3_preservesRowsAndDefaultsLazyToFalse() {
        var db = helper.createDatabase(TEST_DB, 2)
        insertV2Row(db, key = "preserved.key", value = "preserved.value")
        insertV2Row(db, key = "another.key", value = "another.value")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, PreferenceDatabase.MIGRATION_2_3)

        val rows = readAllRows(db)
        Assert.assertEquals(2, rows.size)

        val preserved = rows.single { it.key == "preserved.key" }
        Assert.assertEquals("preserved.value", preserved.value)
        Assert.assertFalse("migration default should be lazy=false", preserved.lazy)

        val another = rows.single { it.key == "another.key" }
        Assert.assertEquals("another.value", another.value)
        Assert.assertFalse(another.lazy)
    }

    private data class Row(val key: String, val value: String?, val lazy: Boolean)

    companion object {

        private const val TEST_DB = "ua_preferences.db"

        private fun insertV2Row(db: SupportSQLiteDatabase, key: String, value: String?) {
            val values = ContentValues().apply {
                put("_id", key)
                put("value", value)
            }
            db.insert("preferences", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
        }

        private fun readAllRows(db: SupportSQLiteDatabase): List<Row> {
            val out = mutableListOf<Row>()
            db.query("SELECT _id, value, lazy FROM preferences").use { cursor ->
                while (cursor.moveToNext()) {
                    out += Row(
                        key = cursor.getString(0),
                        value = if (cursor.isNull(1)) null else cursor.getString(1),
                        lazy = cursor.getInt(2) != 0
                    )
                }
            }
            return out
        }
    }
}
