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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;

import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Preferences base class.
 * <p/>
 * This class should not be used directly, instead create a new class that extends
 * this one and create preference specific methods that utilize the put method
 * and all the get methods implemented here.
 *
 * @hide
 */
public final class PreferenceDataStore {

    private static final String WHERE_CLAUSE_KEY = PreferencesDataManager.COLUMN_NAME_KEY + " = ?";
    private static final int MODE_MULTI_PROCESS = 0x00000004;

    Executor executor = Executors.newSingleThreadExecutor();

    private final Map<String, Preference> preferences = new HashMap<>();
    private UrbanAirshipResolver resolver;

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
        public void onPreferenceChange(String key);
    }

    /**
     * Preferences constructor.
     *
     * @param context The application context.
     */
    PreferenceDataStore(Context context) {
        this(new UrbanAirshipResolver(context));
    }

    PreferenceDataStore(UrbanAirshipResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Adds a listener for preference changes.
     * @param listener A PreferenceChangeListener.
     */
    public void addListener(PreferenceChangeListener listener) {
        if (listener != null) {
            synchronized (listeners) {
                listeners.add(listener);
            }
        }
    }

    /**
     * Removes a listener for preference changes.
     * @param listener A PreferenceChangeListener.
     */
    public void removeListener(PreferenceChangeListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Loads all the stored preferences. This call blocks.
     */
    void loadAll() {
        Cursor cursor = resolver.query(UrbanAirshipProvider.getPreferencesContentUri(), null, null, null, null);
        if (cursor == null) {
            return;
        }

        int keyIndex = cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_KEY);
        int valueIndex = cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_VALUE);

        while (cursor.moveToNext()) {
            String key = cursor.getString(keyIndex);
            String value = cursor.getString(valueIndex);
            preferences.put(key, new Preference(key, value));
        }

        cursor.close();
    }

    /**
     * Get the boolean preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist.
     * @return The boolean value for the preference or defaultValue if it doesn't exist.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
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
    public String getString(String key, String defaultValue) {
        String value = getPreference(key).get();
        return value == null ? defaultValue : value;
    }

    /**
     * Get the float preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist or
     * cannot be coerced into a float.
     * @return The float value for the preference or defaultValue if it doesn't exist.
     */
    public float getFloat(String key, float defaultValue) {
        String value = getPreference(key).get();
        if (value == null) {
            return defaultValue;
        }

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get the long preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist or
     * cannot be coerced into a long.
     * @return The long value for the preference or defaultValue if it doesn't exist.
     */
    public long getLong(String key, long defaultValue) {
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
    public int getInt(String key, int defaultValue) {
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
     * Delete a key/value pair. This method will block on the database write.
     *
     * @param key The preference name.
     * @return <code>true</code> if the preference was successfully removed from
     * the database, otherwise <code>false</code>
     */
    public boolean removeSync(String key) {
        return putSync(key, null);
    }

    /**
     * Delete a key/value pair.
     *
     * @param key The preference name.
     */
    public void remove(String key) {
        put(key, null);
    }

    /**
     * Put new or replace an existing preference.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public void put(String key, Object value) {
        String stringValue = value == null ? null : String.valueOf(value);
        getPreference(key).put(stringValue);
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
    public boolean putSync(String key, Object value) {
        String stringValue = value == null ? null : String.valueOf(value);
        return getPreference(key).putSync(stringValue);
    }

    /**
     * Called when a preference changes in value.
     *
     * @param key The key of the preference.
     */
    private void onPreferenceChanged(String key) {
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
    private Preference getPreference(String key) {
        Preference preference;

        synchronized (preferences) {
            if (preferences.containsKey(key)) {
                preference = preferences.get(key);
            } else {
                preference = new Preference(key, null);
                preferences.put(key, preference);
            }
        }

        return preference;
    }

    /**
     * Migrates the old preference data store to the new preferences.
     * @param context The application context.
     */
    void migrateSharedPreferences(Context context) {
        migratePreferencesFromFileToDb(context, "com.urbanairship.user");
        migratePreferencesFromFileToDb(context, "com.urbanairship.push");
    }

    /**
     * Migrates a specific android shared preference to the new preferences store.
     * @param context The application context.
     * @param shareName The shared preferences share name.
     */
    private void migratePreferencesFromFileToDb(Context context, String shareName) {
        Logger.verbose("PreferenceDataStore - Migrating " + shareName);
        SharedPreferences prefs = context.getSharedPreferences(shareName, MODE_MULTI_PROCESS);

        Map<String, ?> prefsMap = prefs.getAll();
        Logger.verbose("PreferenceDataStore - Found " + prefsMap.size() + " preferences to migrate.");

        if (prefsMap.size() > 0) {
            for (Map.Entry<String, ?> entry : prefsMap.entrySet()) {
                Logger.verbose("PreferenceDataStore - Adding " + entry.getKey() + ":" + entry.getValue() + " to the insert.");

                synchronized (preferences) {
                    Preference preference = new Preference(entry.getKey(), String.valueOf(entry.getValue()));
                    preferences.put(entry.getKey(), preference);
                }
            }

            Logger.verbose("PreferenceDataStore - Migration finished, deleting " + shareName);
            prefs.edit().clear().commit();
        }
    }

    /**
     * A helper class that handles fetching, writing, and syncing with the
     * preference provider.
     */
    private class Preference {

        private final String key;
        private String value;

        Preference(String key, String value) {
            this.key = key;
            this.value = value;
            this.registerObserver();
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
            setValue(value);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    writeValue(value);
                }
            });
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
         */
        private void setValue(String value) {
            synchronized (this) {
                if (UAStringUtil.equals(value, this.value)) {
                    return;
                }

                this.value = value;
            }

            onPreferenceChanged(key);
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
                    return resolver.delete(UrbanAirshipProvider.getPreferencesContentUri(), WHERE_CLAUSE_KEY,
                            new String[] { key }) >= 0;
                } else {
                    Logger.verbose("PreferenceDataStore - Saving preference: " + key + " value: " + value);
                    ContentValues values = new ContentValues();
                    values.put(PreferencesDataManager.COLUMN_NAME_KEY, key);
                    values.put(PreferencesDataManager.COLUMN_NAME_VALUE, value);
                    return resolver.insert(UrbanAirshipProvider.getPreferencesContentUri(), values) != null;
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
                    cursor = resolver.query(UrbanAirshipProvider.getPreferencesContentUri(),
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

        /**
         * Registers a content observer to be notified when the preference
         * changes.
         */
        private void registerObserver() {
            ContentObserver observer = new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);

                    Logger.verbose("PreferenceDataStore - Preference updated:" + Preference.this.key);
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            syncValue();
                        }
                    });
                }
            };

            Uri uri = Uri.withAppendedPath(UrbanAirshipProvider.getPreferencesContentUri(), key);
            resolver.registerContentObserver(uri, true, observer);
        }
    }
}
