/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * PreferenceDataStore stores and retrieves all the Urban Airship preferences through the
 * {@link UrbanAirshipProvider}.
 *
 * @hide
 */
public final class PreferenceDataStore {

    private static final String WHERE_CLAUSE_KEY = PreferencesDataManager.COLUMN_NAME_KEY + " = ?";

    Executor executor = Executors.newSingleThreadExecutor();

    private final Map<String, Preference> preferences = new HashMap<>();
    private final UrbanAirshipResolver resolver;
    private final Context context;

    private final List<PreferenceChangeListener> listeners = new ArrayList<>();


    /**
     * Listener for when preferences changes either by the
     * current process or a different process.
     */
    public interface PreferenceChangeListener {
        /**
         * Called when a preference changes.
         *
         * @param key The key of the preference.
         */
        void onPreferenceChange(String key);
    }

    /**
     * Preferences constructor.
     *
     * @param context The application context.
     */
    PreferenceDataStore(Context context) {
        this(context, new UrbanAirshipResolver(context));
    }

    PreferenceDataStore(Context context, UrbanAirshipResolver resolver) {
        this.context = context;
        this.resolver = resolver;
    }

    /**
     * Adds a listener for preference changes.
     *
     * @param listener A PreferenceChangeListener.
     */
    public void addListener(@NonNull PreferenceChangeListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener for preference changes.
     *
     * @param listener A PreferenceChangeListener.
     */
    public void removeListener(@NonNull PreferenceChangeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Initializes the preference data store.
     */
    protected void init() {
        Cursor cursor = resolver.query(UrbanAirshipProvider.getPreferencesContentUri(context), null, null, null, null);
        if (cursor == null) {
            return;
        }

        int keyIndex = cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_KEY);
        int valueIndex = cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_VALUE);

        while (cursor.moveToNext()) {
            String key = cursor.getString(keyIndex);
            String value = cursor.getString(valueIndex);
            Preference preference = new Preference(key, value);
            preference.registerObserver();

            preferences.put(key, preference);
        }

        cursor.close();
    }

    /**
     * Unregisters any observers.
     */
    protected void tearDown() {
        for (Preference preference : preferences.values()) {
            preference.unregisterObserver();
        }
    }

    /**
     * Get the boolean preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist.
     * @return The boolean value for the preference or defaultValue if it doesn't exist.
     */
    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        String value = getPreference(key).get();
        return value == null ? defaultValue : Boolean.valueOf(value);
    }

    /**
     * Get the String preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist.
     * @return The String value for the preference or defaultValue if it doesn't exist.
     */
    public String getString(@NonNull String key, String defaultValue) {
        String value = getPreference(key).get();
        return value == null ? defaultValue : value;
    }


    /**
     * Get the long preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist or
     * cannot be coerced into a long.
     * @return The long value for the preference or defaultValue if it doesn't exist.
     */
    public long getLong(@NonNull String key, long defaultValue) {
        String value = getPreference(key).get();
        if (value == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get the integer preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist or
     * cannot be coerced into an integer.
     * @return The integer value for the preference or defaultValue if it doesn't exist.
     */
    public int getInt(@NonNull String key, int defaultValue) {
        String value = getPreference(key).get();
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get the preference value as a {@link JsonValue}.
     *
     * @param key The preference name.
     * @return The value for the preference if available or {@link JsonValue#NULL} if it doesn't exist.
     */
    public JsonValue getJsonValue(@NonNull String key) {
        try {
            return JsonValue.parseString(getPreference(key).get());
        } catch (JsonException e) {
            // Should never happen
            Logger.debug("Unable to parse preference value: " + key, e);
            return JsonValue.NULL;
        }
    }

    /**
     * Delete a key/value pair.
     *
     * @param key The preference name.
     */
    public void remove(@NonNull String key) {
        Preference preference = null;
        synchronized (preferences) {
            if (preferences.containsKey(key)) {
                preference = preferences.get(key);
            }
        }

        if (preference != null) {
            preference.put(null);
        }
    }

    /**
     * Stores a String value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public void put(@NonNull String key, String value) {
        getPreference(key).put(value);
    }

    /**
     * Stores a long value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public void put(@NonNull String key, long value) {
        getPreference(key).put(String.valueOf(value));
    }

    /**
     * Stores an int value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public void put(@NonNull String key, int value) {
        getPreference(key).put(String.valueOf(value));
    }

    /**
     * Stores a boolean value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public void put(@NonNull String key, boolean value) {
        getPreference(key).put(String.valueOf(value));
    }

    /**
     * Stores a {@link JsonValue} value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public void put(@NonNull String key, JsonValue value) {
        if (value == null) {
            remove(key);
        } else {
            getPreference(key).put(value.toString());
        }
    }

    /**
     * Stores a {@link JsonSerializable} value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public void put(@NonNull String key, JsonSerializable value) {
        if (value == null) {
            remove(key);
        } else {
            put(key, value.toJsonValue());
        }
    }

    /**
     * Put new or replace an existing preference. This method will block on the
     * database write.
     *
     * @param key The preference name.
     * @param value The preference value.
     * @return <code>true</code> if the preference was successfully saved to
     * the database, otherwise <code>false</code>
     */
    public boolean putSync(@NonNull String key, String value) {
        String stringValue = value == null ? null : String.valueOf(value);
        return getPreference(key).putSync(stringValue);
    }

    /**
     * Called when a preference changes in value.
     *
     * @param key The key of the preference.
     */
    private void onPreferenceChanged(@NonNull String key) {
        synchronized (listeners) {
            for (PreferenceChangeListener listener : listeners) {
                listener.onPreferenceChange(key);
            }
        }
    }

    /**
     * Gets the Preference for the key.
     *
     * @param key The preference key.
     * @return A preference for the key.
     */
    private Preference getPreference(@NonNull String key) {
        Preference preference;

        synchronized (preferences) {
            if (preferences.containsKey(key)) {
                preference = preferences.get(key);
            } else {
                preference = new Preference(key, null);
                preference.registerObserver();

                preferences.put(key, preference);
            }
        }

        return preference;
    }

    /**
     * A helper class that handles fetching, writing, and syncing with the
     * preference provider.
     */
    private class Preference {

        private ContentObserver observer = new ContentObserver(null) {

            @Override
            public boolean deliverSelfNotifications() {
                return false;
            }

            @Override
            public void onChange(boolean selfChange) {
                Logger.verbose("PreferenceDataStore - Preference updated: " + Preference.this.key);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        syncValue();
                    }
                });
            }
        };

        private final String key;
        private String value;
        private Uri uri;

        Preference(String key, String value) {
            this.key = key;
            this.value = value;
            this.uri = Uri.withAppendedPath(UrbanAirshipProvider.getPreferencesContentUri(context), key);
        }

        /**
         * Get the current value of the preference
         *
         * @return The value of the preference.
         */
        String get() {
            synchronized (this) {
                return value;
            }
        }

        /**
         * Put a new value for the preference.
         *
         * @param value Value of the preference.
         */
        void put(final String value) {
            if (setValue(value)) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        writeValue(value);
                    }
                });
            }
        }

        /**
         * Puts the preferences value.
         * <p/>
         * The preference will save its value with the UrbanAirship provider.
         *
         * @param value Value of the preference.
         * @return <code>true</code> if the preference was successfully saved to
         * the database, otherwise <code>false</code>
         */
        boolean putSync(String value) {
            synchronized (this) {
                if (writeValue(value)) {
                    setValue(value);
                    return true;
                }
                return false;
            }
        }

        /**
         * Sets the value. If the value is different it calls the onPreferenceChanged
         * method.
         *
         * @param value The value of the preference.
         * @return {@code true} if the value changed, otherwise {@code false}.
         */
        private boolean setValue(String value) {
            synchronized (this) {
                if (UAStringUtil.equals(value, this.value)) {
                    return false;
                }
                this.value = value;
            }

            onPreferenceChanged(key);
            return true;
        }

        /**
         * Actually writes the value to the database.
         *
         * @param value The value to write
         * @return <code>true</code> if the preference was successfully written to
         * the database, otherwise <code>false</code>
         */
        private boolean writeValue(String value) {
            synchronized (this) {
                if (value == null) {
                    Logger.verbose("PreferenceDataStore - Removing preference: " + key);

                    if (resolver.delete(UrbanAirshipProvider.getPreferencesContentUri(context), WHERE_CLAUSE_KEY, new String[] { key }) == 1) {
                        resolver.notifyChange(this.uri, observer);
                        return true;
                    }

                    return false;
                } else {
                    Logger.verbose("PreferenceDataStore - Saving preference: " + key + " value: " + value);
                    ContentValues values = new ContentValues();
                    values.put(PreferencesDataManager.COLUMN_NAME_KEY, key);
                    values.put(PreferencesDataManager.COLUMN_NAME_VALUE, value);

                    if (resolver.insert(UrbanAirshipProvider.getPreferencesContentUri(context), values) != null) {
                        resolver.notifyChange(this.uri, observer);
                        return true;
                    }

                    return false;
                }
            }
        }

        /**
         * Syncs the value from the database to the preference.
         */
        void syncValue() {
            Cursor cursor = null;
            try {
                synchronized (this) {
                    cursor = resolver.query(UrbanAirshipProvider.getPreferencesContentUri(context),
                            new String[] { PreferencesDataManager.COLUMN_NAME_VALUE }, WHERE_CLAUSE_KEY,
                            new String[] { key }, null);
                }

                if (cursor != null) {
                    setValue(cursor.moveToFirst() ? cursor.getString(0) : null);
                } else {
                    Logger.debug("PreferenceDataStore - Unable to get preference " + key + " from" +
                            " database. Falling back to cached value.");
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        void registerObserver() {
            resolver.registerContentObserver(this.uri, true, observer);
        }

        void unregisterObserver() {
            resolver.unregisterContentObserver(observer);
        }
    }
}
