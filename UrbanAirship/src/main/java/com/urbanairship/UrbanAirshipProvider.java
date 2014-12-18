/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import com.urbanairship.util.DataManager;
import com.urbanairship.util.UAStringUtil;

import java.util.Arrays;
import java.util.List;

/**
 * Manages access to Urban Airship Preferences and Rich Push Message data.
 */
public final class UrbanAirshipProvider extends ContentProvider {

    /**
     * The key that pertains to the String array of keys in a broadcast change notification.
     */
    public static final String DB_CHANGE_KEYS_KEY = "com.urbanairship.DB_CHANGE_KEYS";

    /**
     * The key that pertains to the action that caused the DB_CHANGE notification.
     */
    public static final String DB_CHANGE_ACTION_KEY = "com.urbanairship.DB_CHANGE_ACTION";

    /**
     * The key that defines the keys delimiter.
     */
    public static final String KEYS_DELIMITER = "|";

    /**
     * Mime type suffixes for getType.
     */
    static final String SINGLE_SUFFIX = "vnd.urbanairship.cursor.item/";
    static final String MULTIPLE_SUFFIX = "vnd.urbanairship.cursor.dir/";

    static final String RICH_PUSH_CONTENT_TYPE = MULTIPLE_SUFFIX + "richpush";
    static final String RICH_PUSH_CONTENT_ITEM_TYPE = SINGLE_SUFFIX + "richpush";
    static final String PREFERENCES_CONTENT_TYPE = MULTIPLE_SUFFIX + "preference";
    static final String PREFERENCES_CONTENT_ITEM_TYPE = SINGLE_SUFFIX + "preference";


    /**
     * The database delete action that is appended to notification Uri's so we have more
     * information about what changed in the database.
     */
    public static final String DELETE_ACTION = "delete";

    /**
     * The database insert action that is appended to notification Uri's so we have more
     * information about what changed in the database.
     */
    public static final String INSERT_ACTION = "insert";

    /**
     * The database replace action that is appended to notification Uri's so we have more
     * information about what changed in the database.
     */
    public static final String REPLACE_ACTION = "replace";

    /**
     * The database update action that is appended to notification Uri's so we have more
     * information about what changed in the database.
     */
    public static final String UPDATE_ACTION = "update";

    /**
     * Used to match passed in Uris to databases.
     */
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

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


    /**
     * Location of the different parts of interest within well-formed Uris. Maybe not needed right now.
     * <p/>
     * The only required part is TABLE_LOCATION. KEYS_LOCATION and ACTION_LOCATION are mostly used for
     * change notifications.
     * <p/>
     * content://com.urbanairship/TABLE_LOCATION/KEYS_LOCATION/ACTION_LOCATION
     * <p/>
     * Examples:
     * <p/>
     * Single preference: content://com.urbanairship/preferences/com.urbanairship.analytics.LAST_SENT
     * Single richpush message: content://com.urbanairship/richpush/a13245tr
     * <p/>
     * Examples of Uris that will be broadcast instead of notified through the resolver. These are
     * necessary since Android didn't start sending the Uri back with the change notification until
     * Jelly Bean.
     * <p/>
     * Single richpush message inserted: content://com.urbanairship/richpush/a13245tr/insert
     * Multiple richpush messages isUpdated: content://com.urbanairship/richpush/a13245tr|bfjs234ffl/update
     * <p/>
     * private static final int TABLE_LOCATION = 0;
     * private static final int ACTION_LOCATION = 2;
     */
    private static final int KEYS_LOCATION = 1;


    private DatabaseModel richPushModel;
    private DatabaseModel preferencesModel;

    private static String authorityString;

    /**
     * Initializes the UrbanAirshipProvider and sets up the URI matcher.
     * This is called in UAirship.takeOff().
     */
    static void init() {
        MATCHER.addURI(getAuthorityString(), "richpush", RICHPUSH_MESSAGES_URI_TYPE);
        MATCHER.addURI(getAuthorityString(), "richpush/*", RICHPUSH_MESSAGE_URI_TYPE);
        MATCHER.addURI(getAuthorityString(), "preferences", PREFERENCES_URI_TYPE);
        MATCHER.addURI(getAuthorityString(), "preferences/*", PREFERENCE_URI_TYPE);
    }

    /**
     * Creates the rich push content URI.
     *
     * @return The rich push content URI.
     */
    public static Uri getRichPushContentUri() {
        return Uri.parse("content://" + getAuthorityString() + "/richpush");
    }

    /**
     * Creates the preferences URI.
     *
     * @return The preferences URI.
     */
    public static Uri getPreferencesContentUri() {
        return Uri.parse("content://" + getAuthorityString() + "/preferences");
    }

    /**
     * Get the package's authority string.
     *
     * @return The authority string.
     */
    public static String getAuthorityString() {
        if (authorityString == null) {
            String packageName = UAirship.getPackageName();
            authorityString = packageName + ".urbanairship.provider";
        }

        return authorityString;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        DatabaseModel model = getDatabaseModel(uri);
        DataManager manager = model.dataManager;

        int numberDeleted = manager.delete(model.table, selection, selectionArgs);

        model.notifyDatabaseChange(getContext(), getKeys(uri), DELETE_ACTION);
        return numberDeleted;
    }

    @Override
    public String getType(Uri uri) {
        int type = MATCHER.match(uri);
        switch (type) {
            case RICHPUSH_MESSAGES_URI_TYPE:
                return RICH_PUSH_CONTENT_TYPE;
            case RICHPUSH_MESSAGE_URI_TYPE:
                return RICH_PUSH_CONTENT_ITEM_TYPE;
            case PREFERENCES_URI_TYPE:
                return PREFERENCES_CONTENT_TYPE;
            case PREFERENCE_URI_TYPE:
                return PREFERENCES_CONTENT_ITEM_TYPE;
        }
        throw new IllegalArgumentException("Invalid Uri: " + uri);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        DatabaseModel model = getDatabaseModel(uri);
        DataManager manager = model.dataManager;

        List<ContentValues> insertedValues = manager.bulkInsert(model.table, values);

        String[] ids = new String[insertedValues.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = insertedValues.get(i).getAsString(model.notificationColumnId);
        }

        model.notifyDatabaseChange(getContext(), ids, INSERT_ACTION);
        return insertedValues.size();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        DatabaseModel model = getDatabaseModel(uri);
        DataManager manager = model.dataManager;

        if (manager.insert(model.table, values) != -1) {
            String uriKey = values.getAsString(model.notificationColumnId);
            model.notifyDatabaseChange(getContext(), new String[] { uriKey }, INSERT_ACTION);
            return Uri.withAppendedPath(uri, uriKey);
        }

        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        DatabaseModel model = getDatabaseModel(uri);
        DataManager manager = model.dataManager;

        Cursor cursor = manager.query(model.table, projection, selection, selectionArgs, sortOrder);

        if (cursor != null) {
            cursor.setNotificationUri(this.getContext().getContentResolver(), uri);
        }

        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        DatabaseModel model = getDatabaseModel(uri);
        DataManager manager = model.dataManager;

        int updated = manager.update(model.table, values, selection, selectionArgs);
        if (updated != -1) {
            model.notifyDatabaseChange(getContext(), this.getKeys(uri), UPDATE_ACTION);
        }

        return updated;
    }

    @Override
    public void shutdown() {
        getRichPushModel().dataManager.close();
        getPreferencesModel().dataManager.close();
    }


    // helpers

    /**
     * @return The preferences database model.
     */
    private DatabaseModel getPreferencesModel() {
        if (preferencesModel == null) {
            preferencesModel = DatabaseModel.createPreferencesModel(getContext());
        }
        return preferencesModel;
    }

    /**
     * @return The rich push database model.
     */
    private DatabaseModel getRichPushModel() {
        if (richPushModel == null) {
            richPushModel = DatabaseModel.createRichPushModel(getContext());
        }
        return richPushModel;
    }

    /**
     * Parses a URI for any keys to use to identify values in the database.
     *
     * @param uri The URI of the provider action.
     * @return An array of keys.
     */
    private String[] getKeys(Uri uri) {
        try {
            return uri.getPathSegments().get(KEYS_LOCATION).split("\\" + KEYS_DELIMITER);
        } catch (IndexOutOfBoundsException e) {
            return new String[] { };
        }
    }

    /**
     * Gets the database model according to the URI.
     *
     * @param uri URI of the provider action.
     * @return Either a preferences or rich push database model depending on the URI.
     */
    private DatabaseModel getDatabaseModel(Uri uri) {
        int type = MATCHER.match(uri);
        switch (type) {
            case RICHPUSH_MESSAGE_URI_TYPE:
            case RICHPUSH_MESSAGES_URI_TYPE:
                return getRichPushModel();

            case PREFERENCE_URI_TYPE:
            case PREFERENCES_URI_TYPE:
                return getPreferencesModel();
        }
        throw new IllegalArgumentException("Invalid URI: " + uri);
    }


    /**
     * A class that wraps the two different database sources for the content provider.
     */
    private static class DatabaseModel {
        DataManager dataManager;
        String table;
        Uri contentUri;
        String notificationColumnId;

        /**
         * Hidden DatabaseModel constructor.
         *
         * @param dataManager The database manager for the model.
         * @param table The database table to modify.
         * @param contentUri Base URI, used for notifying changes.
         * @param notificationColumnId Notification column id.
         */
        private DatabaseModel(DataManager dataManager, String table, Uri contentUri, String notificationColumnId) {

            this.dataManager = dataManager;
            this.table = table;
            this.contentUri = contentUri;
            this.notificationColumnId = notificationColumnId;
        }

        /**
         * Creates a rich push database model.
         *
         * @param context used to create or open databases.
         * @return A database model configured for rich push messages.
         */
        static DatabaseModel createRichPushModel(Context context) {
            DataManager model = new RichPushDataManager(context);
            return new DatabaseModel(model, RichPushTable.TABLE_NAME, getRichPushContentUri(),
                    RichPushTable.COLUMN_NAME_MESSAGE_ID);
        }

        /**
         * Creates a preferences database model.
         *
         * @param context used to create or open databases.
         * @return DatabaseModel.
         */
        static DatabaseModel createPreferencesModel(Context context) {
            DataManager model = new PreferencesDataManager(context);
            return new DatabaseModel(model, PreferencesDataManager.TABLE_NAME, getPreferencesContentUri(),
                    PreferencesDataManager.COLUMN_NAME_KEY);
        }

        /**
         * Notifies any changes to the database and content provider.
         *
         * @param context The context to use for notifications.
         * @param ids The ids of items that were updated.
         * @param action The type of update action.
         */
        void notifyDatabaseChange(Context context, String[] ids, String action) {
            Uri newUri = Uri.withAppendedPath(contentUri, UAStringUtil.join(Arrays.asList(ids), KEYS_DELIMITER) + "/" + action);
            Logger.verbose("UrbanAirshipProvider - Notifying of change to " + newUri.toString());
            context.getContentResolver().notifyChange(newUri, null);
        }

    }
}
