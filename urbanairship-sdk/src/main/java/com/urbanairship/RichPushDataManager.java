package com.urbanairship;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;

import com.urbanairship.util.DataManager;

/**
 * A database manager to help create, open, and modify the rich push
 * database
 */
class RichPushDataManager extends DataManager {


    public static final String TABLE_NAME = "richpush";

    private static final String DATABASE_NAME = "ua_richpush.db";
    private static final int DATABASE_VERSION = 3;

    RichPushDataManager(Context context, String appKey) {
        super(context, appKey, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + RichPushTable.COLUMN_NAME_KEY + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + RichPushTable.COLUMN_NAME_MESSAGE_ID + " TEXT UNIQUE, "
                + RichPushTable.COLUMN_NAME_MESSAGE_URL + " TEXT, "
                + RichPushTable.COLUMN_NAME_MESSAGE_BODY_URL + " TEXT, "
                + RichPushTable.COLUMN_NAME_MESSAGE_READ_URL + " TEXT, "
                + RichPushTable.COLUMN_NAME_TITLE + " TEXT, "
                + RichPushTable.COLUMN_NAME_EXTRA + " TEXT, "
                + RichPushTable.COLUMN_NAME_UNREAD + " INTEGER, "
                + RichPushTable.COLUMN_NAME_UNREAD_ORIG + " INTEGER, "
                + RichPushTable.COLUMN_NAME_DELETED + " INTEGER, "
                + RichPushTable.COLUMN_NAME_TIMESTAMP + " TEXT, "
                + RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT + " TEXT,"
                + RichPushTable.COLUMN_NAME_EXPIRATION_TIMESTAMP + " TEXT);");
    }

    @Override
    protected void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1:
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT + " TEXT;");
            case 2:
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + RichPushTable.COLUMN_NAME_EXPIRATION_TIMESTAMP + " TEXT;");
                break;
            default:
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        }
    }

    @Override
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop the table and recreate it
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

}
