package com.urbanairship;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

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

    public PreferencesDataManager(@NonNull Context context, @NonNull String appKey) {
        super(context, appKey, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + COLUMN_NAME_KEY + " TEXT PRIMARY KEY, "
                + COLUMN_NAME_VALUE + " TEXT);");
    }

    @Override
    protected void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop the table and recreate it
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}
