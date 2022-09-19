/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.db.RetryingSQLiteOpenHelper;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

/**
 * Message database
 */
@Database(
    version = 5,
    entities = { MessageEntity.class }
)
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class MessageDatabase extends RoomDatabase {

    static final String DB_NAME = "ua_richpush.db";
    static final String TABLE_NAME = "richpush";
    static final String KEY = "_id";
    static final String MESSAGE_ID = "message_id";
    static final String MESSAGE_URL = "message_url";
    static final String BODY_URL = "message_body_url";
    static final String READ_URL = "message_read_url";
    static final String TITLE = "title";
    static final String EXTRA = "extra";
    static final String UNREAD = "unread";
    static final String UNREAD_ORIG = "unread_orig";
    static final String DELETED = "deleted";
    static final String TIMESTAMP = "timestamp";
    static final String RAW_MESSAGE = "raw_message_object";
    static final String EXPIRATION = "expiration_timestamp";

    private static final String DB_DIR = "com.urbanairship.databases";

    public abstract MessageDao getDao();

    static final Migration MIGRATION_1_5 = new MessageDatabaseMultiMigration(1, 5);
    static final Migration MIGRATION_2_5 = new MessageDatabaseMultiMigration(2, 5);
    static final Migration MIGRATION_3_5 = new MessageDatabaseMultiMigration(3, 5);
    static final Migration MIGRATION_4_5 = new MessageDatabaseMultiMigration(4, 5);

    public static MessageDatabase createDatabase(@NonNull Context context, @NonNull AirshipConfigOptions config) {
        String name = config.appKey + "_" + DB_NAME;
        File urbanAirshipNoBackupDirectory = new File(ContextCompat.getNoBackupFilesDir(context), DB_DIR);
        String path = new File(urbanAirshipNoBackupDirectory, name).getAbsolutePath();
        RetryingSQLiteOpenHelper.Factory retryingOpenHelperFactory =
                new RetryingSQLiteOpenHelper.Factory(new FrameworkSQLiteOpenHelperFactory(), true);

        return Room.databaseBuilder(context, MessageDatabase.class, path)
            .openHelperFactory(retryingOpenHelperFactory)
            .addMigrations(MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build();
    }

    @VisibleForTesting
    public static MessageDatabase createInMemoryDatabase(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(context, MessageDatabase.class)
                   .allowMainThreadQueries()
                   .build();
    }
}
