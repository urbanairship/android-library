package com.urbanairship;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.urbanairship.util.DataManager;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * A database manager to help create, open, and modify the message center database.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class MessageCenterDataManager extends DataManager {

    @NonNull
    private static final String DATABASE_NAME = "ua_richpush.db";

    private static final int DATABASE_VERSION = 3;

    public MessageCenterDataManager(@NonNull Context context, @NonNull String appKey) {
        super(context, appKey, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + MessageTable.TABLE_NAME + " ("
                + MessageTable.COLUMN_NAME_KEY + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + MessageTable.COLUMN_NAME_MESSAGE_ID + " TEXT UNIQUE, "
                + MessageTable.COLUMN_NAME_MESSAGE_URL + " TEXT, "
                + MessageTable.COLUMN_NAME_MESSAGE_BODY_URL + " TEXT, "
                + MessageTable.COLUMN_NAME_MESSAGE_READ_URL + " TEXT, "
                + MessageTable.COLUMN_NAME_TITLE + " TEXT, "
                + MessageTable.COLUMN_NAME_EXTRA + " TEXT, "
                + MessageTable.COLUMN_NAME_UNREAD + " INTEGER, "
                + MessageTable.COLUMN_NAME_UNREAD_ORIG + " INTEGER, "
                + MessageTable.COLUMN_NAME_DELETED + " INTEGER, "
                + MessageTable.COLUMN_NAME_TIMESTAMP + " TEXT, "
                + MessageTable.COLUMN_NAME_RAW_MESSAGE_OBJECT + " TEXT,"
                + MessageTable.COLUMN_NAME_EXPIRATION_TIMESTAMP + " TEXT);");
    }

    @Override
    protected void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1:
                db.execSQL("ALTER TABLE " + MessageTable.TABLE_NAME + " ADD COLUMN " + MessageTable.COLUMN_NAME_RAW_MESSAGE_OBJECT + " TEXT;");
            case 2:
                db.execSQL("ALTER TABLE " + MessageTable.TABLE_NAME + " ADD COLUMN " + MessageTable.COLUMN_NAME_EXPIRATION_TIMESTAMP + " TEXT;");
                break;
            default:
                db.execSQL("DROP TABLE IF EXISTS " + MessageTable.TABLE_NAME);
                onCreate(db);
        }
    }

    @Override
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop the table and recreate it
        db.execSQL("DROP TABLE IF EXISTS " + MessageTable.TABLE_NAME);
        onCreate(db);
    }

    /**
     * Rich Push Message table definition
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface MessageTable {

        @NonNull
        String COLUMN_NAME_MESSAGE_ID = "message_id";

        @NonNull
        String COLUMN_NAME_MESSAGE_URL = "message_url";

        @NonNull
        String COLUMN_NAME_MESSAGE_BODY_URL = "message_body_url";

        @NonNull
        String COLUMN_NAME_MESSAGE_READ_URL = "message_read_url";

        @NonNull
        String COLUMN_NAME_TITLE = "title";

        @NonNull
        String COLUMN_NAME_EXTRA = "extra";

        @NonNull
        String COLUMN_NAME_UNREAD = "unread";

        @NonNull
        String COLUMN_NAME_UNREAD_ORIG = "unread_orig";

        @NonNull
        String COLUMN_NAME_DELETED = "deleted";

        @NonNull
        String COLUMN_NAME_KEY = "_id";

        @NonNull
        String COLUMN_NAME_TIMESTAMP = "timestamp";

        @NonNull
        String COLUMN_NAME_RAW_MESSAGE_OBJECT = "raw_message_object";

        @NonNull
        String COLUMN_NAME_EXPIRATION_TIMESTAMP = "expiration_timestamp";

        @NonNull
        String TABLE_NAME = "richpush";

    }

}
