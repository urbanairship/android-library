/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.analytics.data.EventsStorage;
import com.urbanairship.util.DataManager;

import java.util.List;

/**
 * Manages access to Urban Airship Preferences and Rich Push Message data.
 * @hide
 */
public final class UrbanAirshipProvider extends ContentProvider {

    public static final String QUERY_PARAMETER_LIMIT = "limit";

    /**
     * Mime type suffixes for getType.
     */
    static final String SINGLE_SUFFIX = "vnd.urbanairship.cursor.item/";
    static final String MULTIPLE_SUFFIX = "vnd.urbanairship.cursor.dir/";

    static final String RICH_PUSH_CONTENT_TYPE = MULTIPLE_SUFFIX + "richpush";
    static final String RICH_PUSH_CONTENT_ITEM_TYPE = SINGLE_SUFFIX + "richpush";
    static final String PREFERENCES_CONTENT_TYPE = MULTIPLE_SUFFIX + "preference";
    static final String PREFERENCES_CONTENT_ITEM_TYPE = SINGLE_SUFFIX + "preference";
    static final String EVENTS_CONTENT_TYPE = MULTIPLE_SUFFIX + "events";
    static final String EVENTS_CONTENT_ITEM_TYPE = SINGLE_SUFFIX + "events";
    /**
     * Used to match passed in Uris to databases.
     */
    private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

    /**
     * Rich Push Uri Types to give to the UriMatcher.
     */
    private static final int RICHPUSH_MESSAGES_URI_TYPE = 0;
    private static final int RICHPUSH_MESSAGE_URI_TYPE = 1;

    /**
     * Preferences Uri Types to give to the UriMatcher.
     */
    private static final int PREFERENCES_URI_TYPE = 2;
    private static final int PREFERENCE_URI_TYPE = 3;

    private static final int EVENTS_URI_TYPE = 4;
    private static final int EVENT_URI_TYPE = 5;

    private DatabaseModel richPushDataModel;
    private DatabaseModel preferencesDataModel;
    private DatabaseModel eventsDataModel;


    private static String authorityString;


    /**
     * Creates the rich push content URI.
     *
     * @return The rich push content URI.
     */
    public static Uri getRichPushContentUri(Context context) {
        return Uri.parse("content://" + getAuthorityString(context) + "/richpush");
    }

    /**
     * Creates the preferences URI.
     *
     * @return The preferences URI.
     */
    public static Uri getPreferencesContentUri(Context context) {
        return Uri.parse("content://" + getAuthorityString(context) + "/preferences");
    }

    /**
     * Creates the events URI.
     *
     * @return The events URI.
     */
    public static Uri getEventsContentUri(Context context) {
        return Uri.parse("content://" + getAuthorityString(context) + "/events");
    }

    /**
     * Get the package's authority string.
     *
     * @return The authority string.
     */
    public static String getAuthorityString(Context context) {
        if (authorityString == null) {
            String packageName = context.getPackageName();
            authorityString = packageName + ".urbanairship.provider";
        }

        return authorityString;
    }


    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }

        matcher.addURI(getAuthorityString(getContext()), "richpush", RICHPUSH_MESSAGES_URI_TYPE);
        matcher.addURI(getAuthorityString(getContext()), "richpush/*", RICHPUSH_MESSAGE_URI_TYPE);
        matcher.addURI(getAuthorityString(getContext()), "preferences", PREFERENCES_URI_TYPE);
        matcher.addURI(getAuthorityString(getContext()), "preferences/*", PREFERENCE_URI_TYPE);
        matcher.addURI(getAuthorityString(getContext()), "events", EVENT_URI_TYPE);
        matcher.addURI(getAuthorityString(getContext()), "events/*", EVENT_URI_TYPE);

        Autopilot.automaticTakeOff((Application) getContext().getApplicationContext(), true);

        UAirship.isMainProcess = true;
        ActivityMonitor.shared(getContext().getApplicationContext());
        return true;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        DatabaseModel model = getDatabaseModel(uri);
        if (model == null || getContext() == null) {
            return -1;
        }

        return model.dataManager.delete(model.table, selection, selectionArgs);
    }

    @Override
    public String getType(@NonNull Uri uri) {
        int type = matcher.match(uri);
        switch (type) {
            case RICHPUSH_MESSAGES_URI_TYPE:
                return RICH_PUSH_CONTENT_TYPE;
            case RICHPUSH_MESSAGE_URI_TYPE:
                return RICH_PUSH_CONTENT_ITEM_TYPE;
            case PREFERENCES_URI_TYPE:
                return PREFERENCES_CONTENT_TYPE;
            case PREFERENCE_URI_TYPE:
                return PREFERENCES_CONTENT_ITEM_TYPE;
            case EVENT_URI_TYPE:
                return EVENTS_CONTENT_TYPE;
            case EVENTS_URI_TYPE:
                return EVENTS_CONTENT_ITEM_TYPE;

        }
        throw new IllegalArgumentException("Invalid Uri: " + uri);
    }

    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        DatabaseModel model = getDatabaseModel(uri);
        if (model == null || getContext() == null) {
            return -1;
        }

        List<ContentValues> inserted = model.dataManager.bulkInsert(model.table, values);
        if (inserted == null) {
            return -1;
        }

        return inserted.size();
    }

    @Override
    public Uri insert(@NonNull  Uri uri, ContentValues values) {
        DatabaseModel model = getDatabaseModel(uri);
        if (model == null || getContext() == null) {
            return null;
        }

        long id = model.dataManager.insert(model.table, values);
        if (id != -1) {
            String uriKey = values.getAsString(model.notificationColumnId);
            return Uri.withAppendedPath(uri, uriKey);
        }

        return null;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        DatabaseModel model = getDatabaseModel(uri);
        if (model == null || getContext() == null) {
            return null;
        }

        Cursor cursor;

        String limit = uri.getQueryParameter(QUERY_PARAMETER_LIMIT);
        if (limit != null) {
            cursor = model.dataManager.query(model.table, projection, selection, selectionArgs, sortOrder, "0, " + limit);
        } else {
            cursor = model.dataManager.query(model.table, projection, selection, selectionArgs, sortOrder);
        }

        if (cursor != null) {
            cursor.setNotificationUri(this.getContext().getContentResolver(), uri);
        }

        return cursor;
    }

    @Override
    public int update(@NonNull  Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        DatabaseModel model = getDatabaseModel(uri);
        if (model == null || getContext() == null) {
            return -1;
        }

        return model.dataManager.update(model.table, values, selection, selectionArgs);
    }

    @Override
    public void shutdown() {
        if (richPushDataModel != null) {
            richPushDataModel.dataManager.close();
            richPushDataModel = null;
        }

        if (preferencesDataModel != null) {
            preferencesDataModel.dataManager.close();
            preferencesDataModel = null;
        }

        if (eventsDataModel != null) {
            eventsDataModel.dataManager.close();
            eventsDataModel = null;
        }
    }


    /**
     * Gets the database model according to the URI.
     *
     * @param uri URI of the provider action.
     * @return Either a preferences or rich push database model depending on the URI.
     */
    @Nullable
    private DatabaseModel getDatabaseModel(@NonNull Uri uri) {
        if (getContext() == null || (!UAirship.isFlying() && !UAirship.isTakingOff())) {
            return null;
        }

        UAirship airship = UAirship.sharedAirship;
        if (airship == null || airship.getAirshipConfigOptions() == null || airship.getAirshipConfigOptions().getAppKey() == null) {
            return null;
        }

        String appKey = airship.getAirshipConfigOptions().getAppKey();

        int type = matcher.match(uri);
        switch (type) {
            case RICHPUSH_MESSAGE_URI_TYPE:
            case RICHPUSH_MESSAGES_URI_TYPE:
                if (richPushDataModel == null) {
                    richPushDataModel = DatabaseModel.createRichPushModel(getContext(), appKey);
                }

                return richPushDataModel;

            case PREFERENCE_URI_TYPE:
            case PREFERENCES_URI_TYPE:
                if (preferencesDataModel == null) {
                    preferencesDataModel = DatabaseModel.createPreferencesModel(getContext(), appKey);
                }

                return preferencesDataModel;

            case EVENT_URI_TYPE:
            case EVENTS_URI_TYPE:
                if (eventsDataModel == null) {
                    eventsDataModel = DatabaseModel.createEventsDataModel(getContext(), appKey);
                }

                return eventsDataModel;
        }


        throw new IllegalArgumentException("Invalid URI: " + uri);
    }


    /**
     * A class that wraps the two different database sources for the content provider.
     */
    private static class DatabaseModel {
        final DataManager dataManager;
        final String table;
        private final String notificationColumnId;

        /**
         * Hidden DatabaseModel constructor.
         *
         * @param dataManager The database manager for the model.
         * @param table The database table to modify.
         * @param notificationColumnId Notification column id.
         */
        private DatabaseModel(@NonNull DataManager dataManager, @NonNull String table, @NonNull String notificationColumnId) {
            this.dataManager = dataManager;
            this.table = table;
            this.notificationColumnId = notificationColumnId;
         }

        /**
         * Creates a rich push database model.
         *
         * @param context The application context
         * @param appKey The current appKey.
         * @return A database model configured for rich push messages.
         */
        static DatabaseModel createRichPushModel(@NonNull Context context, String appKey) {
            DataManager model = new RichPushDataManager(context, appKey);
            return new DatabaseModel(model, RichPushTable.TABLE_NAME, RichPushTable.COLUMN_NAME_MESSAGE_ID);
        }

        /**
         * Creates a preferences database model.
         *
         * @param context The application context
         * @param appKey The current appKey.
         * @return DatabaseModel.
         */
        static DatabaseModel createPreferencesModel(@NonNull Context context, String appKey) {
            DataManager model = new PreferencesDataManager(context, appKey);
            return new DatabaseModel(model, PreferencesDataManager.TABLE_NAME, PreferencesDataManager.COLUMN_NAME_KEY);
        }


        static DatabaseModel createEventsDataModel(Context context, String appKey) {
            DataManager model = new EventsStorage(context, appKey);
            return new DatabaseModel(model, EventsStorage.Events.TABLE_NAME, EventsStorage.Events._ID);
        }
    }
}
