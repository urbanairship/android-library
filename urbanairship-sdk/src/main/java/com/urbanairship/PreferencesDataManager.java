package com.urbanairship;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.urbanairship.util.DataManager;

/**
 * A database manager to help create, open, and modify the preferences
 * database
 */
class PreferencesDataManager extends DataManager {

    static final String COLUMN_NAME_KEY = "_id";
    static final String COLUMN_NAME_VALUE = "value";
    static final String TABLE_NAME = "preferences";
    static final String DATABASE_NAME = "ua_preferences.db";
    static final int DATABASE_VERSION = 1;

    public PreferencesDataManager(Context context) {
        super(context, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + COLUMN_NAME_KEY + " TEXT PRIMARY KEY, "
                + COLUMN_NAME_VALUE + " TEXT);");
    }

    @Override
    protected void bindValuesToSqliteStatement(SQLiteStatement statement, ContentValues values) {
        bind(statement, 1, values.getAsString(COLUMN_NAME_KEY));
        bind(statement, 2, values.getAsString(COLUMN_NAME_VALUE));
    }

    @Override
    protected SQLiteStatement getInsertStatement(String table, SQLiteDatabase db) {
        String sql = this.buildInsertStatement(table, COLUMN_NAME_KEY, COLUMN_NAME_VALUE);
        return db.compileStatement(sql);
    }

    @Override
    protected void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop the table and recreate it
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}
