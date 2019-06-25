/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DataManager;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link DataManager} class for NotificationChannelRegistry.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationChannelRegistryDataManager extends DataManager {

    static final String TABLE_NAME = "notification_channels";

    static final String COLUMN_NAME_ID = "id";

    // The channel ID
    static final String COLUMN_NAME_CHANNEL_ID = "channel_id";

    // JSON-serialized channel data
    static final String COLUMN_NAME_DATA = "data";

    /**
     * The database version.
     */
    private static final int DATABASE_VERSION = 1;

    /**
     * NotificationChannelRegistryDataManager constructor.
     *
     * @param context The app context.
     * @param appKey The app key.
     * @param dbName The database name.
     */
    public NotificationChannelRegistryDataManager(@NonNull Context context, @NonNull String appKey, @NonNull String dbName) {
        super(context, appKey, dbName, DATABASE_VERSION);
    }

    @Override
    protected void onCreate(@NonNull SQLiteDatabase db) {
        Logger.debug("NotificationChannelRegistryDataManager - Creating database");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + COLUMN_NAME_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NAME_CHANNEL_ID + " TEXT,"
                + COLUMN_NAME_DATA + " TEXT"
                + ");");
    }

    /**
     * Creates a channel in the database. This is a no-op if a channel already exists under
     * the associated identifier.
     *
     * @param channelCompat A NotificationChannelCompat.
     */
    @WorkerThread
    public boolean createChannel(@NonNull NotificationChannelCompat channelCompat) {
        final SQLiteDatabase db = getWritableDatabase();

        if (db == null) {
            Logger.error("NotificationChannelRegistryDataManager - Unable to save notification channel.");
            return false;
        }

        saveChannel(channelCompat, db);

        return true;
    }

    /**
     * Gets all the saved notification channels.
     *
     * @return A Set of NotificationChannelCompat objects, or an empty set if none are available.
     */
    @NonNull
    @WorkerThread
    public Set<NotificationChannelCompat> getChannels() {
        Cursor cursor = query(TABLE_NAME, null, null, null, null);

        Set<NotificationChannelCompat> channels = new HashSet<>();

        if (cursor == null) {
            return channels;
        }

        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            NotificationChannelCompat channelCompat = getChannel(cursor);
            channels.add(channelCompat);
            cursor.moveToNext();
        }

        return channels;
    }

    /**
     * Gets a notification channel corresponding to the provided identifier.
     *
     * @param channelId The channel ID.
     * @return A NotificationChannelCompat instance, or <code>null</code> if one could not be found.
     */
    @Nullable
    @WorkerThread
    public NotificationChannelCompat getChannel(@NonNull String channelId) {
        String where = COLUMN_NAME_CHANNEL_ID + " = ?";
        Cursor cursor = query(TABLE_NAME, null, where, new String[]{channelId}, null);

        if (cursor == null) {
            return null;
        }

        cursor.moveToFirst();

        NotificationChannelCompat channelCompat = null;
        while (!cursor.isAfterLast()) {
            channelCompat = getChannel(cursor);
            break;
        }

        cursor.close();

        return channelCompat;
    }

    /**
     * Deletes a notification channel from disk, by identifier.
     *
     * @param channelId
     */
    @WorkerThread
    public boolean deleteChannel(@NonNull String channelId) {
        String where = COLUMN_NAME_CHANNEL_ID + " = ?";
        int result = delete(TABLE_NAME, where, new String[]{channelId});

        if (result == -1) {
            Logger.error("Unable to remove notification channel: %s", channelId);
            return false;
        }

        return true;
    }

    /**
     * Deletes all payloads.
     *
     * @return A boolean indicating success.
     */
    @WorkerThread
    boolean deleteChannels() {
        boolean success = delete(TABLE_NAME, null, null) >= 0;
        if (!success) {
            Logger.error("NotificationChannelRegistryDatamanager - failed to delete channels");
        }
        return success;
    }

    /**
     * Gets a notification channel from disk, by reading from the provided Cursor.
     *
     * @param cursor The Cursor.
     * @return A NotificationChannelCompat, or <code>null</code> if one could not be found or created.
     */
    @Nullable
    @WorkerThread
    private NotificationChannelCompat getChannel(@NonNull Cursor cursor) {
        String data = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_DATA));

        try {
            return NotificationChannelCompat.fromJson(JsonValue.parseString(data));
        } catch (JsonException e) {
            Logger.error("Unable to parse notification channel: %s", data);
            return null;
        }
    }

    @WorkerThread
    private void saveChannel(NotificationChannelCompat channelCompat, @NonNull SQLiteDatabase database) {
        ContentValues value = new ContentValues();
        value.put(COLUMN_NAME_CHANNEL_ID, channelCompat.getId());
        value.put(COLUMN_NAME_DATA, channelCompat.toJsonValue().toString());

        database.insert(TABLE_NAME, null, value);
    }
}
