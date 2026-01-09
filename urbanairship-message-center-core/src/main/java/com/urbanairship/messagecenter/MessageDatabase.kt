/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.Room.inMemoryDatabaseBuilder
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.db.RetryingSQLiteOpenHelper
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

/**
 * Message database
 * @hide
 */
@Database(
    version = 7,
    entities = [MessageEntity::class],
    autoMigrations = [AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7)]
)
internal abstract class MessageDatabase () : RoomDatabase() {

    internal abstract val dao: MessageDao

    companion object {

        const val DB_NAME: String = "ua_richpush.db"
        const val TABLE_NAME: String = "richpush"
        const val KEY: String = "_id"
        const val MESSAGE_ID: String = "message_id"
        const val MESSAGE_URL: String = "message_url"
        const val BODY_URL: String = "message_body_url"
        const val READ_URL: String = "message_read_url"
        const val TITLE: String = "title"
        const val EXTRA: String = "extra"
        const val UNREAD: String = "unread"
        const val UNREAD_ORIG: String = "unread_orig"
        const val DELETED: String = "deleted"
        const val TIMESTAMP: String = "timestamp"
        const val RAW_MESSAGE: String = "raw_message_object"
        const val EXPIRATION: String = "expiration_timestamp"

        private const val DB_DIR = "com.urbanairship.databases"

        val MIGRATION_1_5: Migration = MessageDatabaseMultiMigration(1, 5)
        val MIGRATION_2_5: Migration = MessageDatabaseMultiMigration(2, 5)
        val MIGRATION_3_5: Migration = MessageDatabaseMultiMigration(3, 5)
        val MIGRATION_4_5: Migration = MessageDatabaseMultiMigration(4, 5)

        fun createDatabase(context: Context, config: AirshipConfigOptions): MessageDatabase {
            val name = config.appKey + "_" + DB_NAME
            val urbanAirshipNoBackupDirectory =
                File(ContextCompat.getNoBackupFilesDir(context), DB_DIR)
            val path = File(urbanAirshipNoBackupDirectory, name).absolutePath
            val retryingOpenHelperFactory =
                RetryingSQLiteOpenHelper.Factory(FrameworkSQLiteOpenHelperFactory(), true)

            return databaseBuilder(context, MessageDatabase::class.java, path)
                .openHelperFactory(retryingOpenHelperFactory)
                .addMigrations(MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5, MIGRATION_4_5)
                .fallbackToDestructiveMigration(true)
                .build()
        }

        @VisibleForTesting
        fun createInMemoryDatabase(context: Context, dispatcher: CoroutineDispatcher): MessageDatabase {
            return inMemoryDatabaseBuilder(context, MessageDatabase::class.java)
                .allowMainThreadQueries()
                .setTransactionExecutor(dispatcher.asExecutor())
                .setQueryExecutor(dispatcher.asExecutor())
                .build()
        }
    }
}
