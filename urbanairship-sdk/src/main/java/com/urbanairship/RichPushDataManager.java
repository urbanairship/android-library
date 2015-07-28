package com.urbanairship;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.urbanairship.util.DataManager;

/**
 * A database manager to help create, open, and modify the rich push
 * database
 */
class RichPushDataManager extends DataManager {


    public static final String TABLE_NAME = "richpush";

    private static final String DATABASE_NAME = "ua_richpush.db";
    private static final int DATABASE_VERSION = 3;

    RichPushDataManager(Context context) {
        super(context, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected void onCreate(SQLiteDatabase db) {
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
    protected void bindValuesToSqliteStatement(SQLiteStatement statement, ContentValues values) {
        bind(statement, 1, values.getAsString(RichPushTable.COLUMN_NAME_MESSAGE_ID));
        bind(statement, 2, values.getAsString(RichPushTable.COLUMN_NAME_MESSAGE_URL));
        bind(statement, 3, values.getAsString(RichPushTable.COLUMN_NAME_MESSAGE_BODY_URL));
        bind(statement, 4, values.getAsString(RichPushTable.COLUMN_NAME_MESSAGE_READ_URL));
        bind(statement, 5, values.getAsString(RichPushTable.COLUMN_NAME_TITLE));
        bind(statement, 6, values.getAsString(RichPushTable.COLUMN_NAME_EXTRA));
        bind(statement, 7, values.getAsBoolean(RichPushTable.COLUMN_NAME_UNREAD), true);
        bind(statement, 8, values.getAsBoolean(RichPushTable.COLUMN_NAME_UNREAD_ORIG), true);
        bind(statement, 9, values.getAsBoolean(RichPushTable.COLUMN_NAME_DELETED), false);
        bind(statement, 10, values.getAsString(RichPushTable.COLUMN_NAME_TIMESTAMP));
        bind(statement, 11, values.getAsString(RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT));
        bind(statement, 12, values.getAsString(RichPushTable.COLUMN_NAME_EXPIRATION_TIMESTAMP));
    }

    @Override
    protected SQLiteStatement getInsertStatement(String table, SQLiteDatabase db) {
        String sql = this.buildInsertStatement(table, RichPushTable.COLUMN_NAME_MESSAGE_ID,
                RichPushTable.COLUMN_NAME_MESSAGE_URL, RichPushTable.COLUMN_NAME_MESSAGE_BODY_URL, RichPushTable.COLUMN_NAME_MESSAGE_READ_URL,
                RichPushTable.COLUMN_NAME_TITLE, RichPushTable.COLUMN_NAME_EXTRA, RichPushTable.COLUMN_NAME_UNREAD,
                RichPushTable.COLUMN_NAME_UNREAD_ORIG, RichPushTable.COLUMN_NAME_DELETED, RichPushTable.COLUMN_NAME_TIMESTAMP,
                RichPushTable.COLUMN_NAME_RAW_MESSAGE_OBJECT, RichPushTable.COLUMN_NAME_EXPIRATION_TIMESTAMP);

        return db.compileStatement(sql);
    }

    @Override
    protected void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
    protected void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop the table and recreate it
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

}
