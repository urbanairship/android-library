/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.content.Context;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * PreferenceDataStore stores and retrieves all the Airship preferences scoped at the app key.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class PreferenceDataStore {

    private static final String[] OBSOLETE_KEYS = new String[] {
            "com.urbanairship.TAG_GROUP_HISTORIAN_RECORDS",
            "com.urbanairship.push.iam.PENDING_IN_APP_MESSAGE",
            "com.urbanairship.push.iam.AUTO_DISPLAY_ENABLED",
            "com.urbanairship.push.iam.LAST_DISPLAYED_ID",
            "com.urbanairship.nameduser.CHANGE_TOKEN_KEY",
            "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY",
            "com.urbanairship.iam.tags.TAG_PREFER_LOCAL_DATA_TIME"
    };

    Executor executor = AirshipExecutors.newSerialExecutor();
    private final Map<String, Preference> preferences = new HashMap<>();

    private final PreferenceDataDao dao;
    private final PreferenceDataDatabase db;

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
        void onPreferenceChange(@NonNull String key);

    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static PreferenceDataStore loadDataStore(@NonNull Context context, @NonNull AirshipConfigOptions configOptions) {
        PreferenceDataDatabase db = PreferenceDataDatabase.createDatabase(context, configOptions);
        PreferenceDataStore dataStore = new PreferenceDataStore(db);
        if (db.exists(context)) {
            dataStore.loadPreferences();
        };
        return dataStore;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public static PreferenceDataStore inMemoryStore(@NonNull Context context) {
        PreferenceDataDatabase db = PreferenceDataDatabase.createInMemoryDatabase(context);
        PreferenceDataStore dataStore = new PreferenceDataStore(db);
        return dataStore;
    }

    @VisibleForTesting
    PreferenceDataStore(@NonNull PreferenceDataDatabase dataDatabase) {
        this.db = dataDatabase;
        this.dao = dataDatabase.getDao();
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

    private void loadPreferences() {
        try {
            List<PreferenceData> preferencesFromDao = dao.getPreferences();

            List<Preference> fromStore = new ArrayList<>();
            for (PreferenceData preferenceData : preferencesFromDao) {
                fromStore.add(new Preference(preferenceData.getKey(), preferenceData.getValue()));
            }

            finishLoad(fromStore);
        } catch (Exception e) {
            Logger.error(e, "Failed to load preferences. Retrying with fallback loading.");
            fallbackLoad();
        }
    }

    private void fallbackLoad() {
        List<String> keys = null;
        try {
            keys = dao.queryKeys();
        } catch (Exception e) {
            Logger.error(e, "Failed to load keys.");
        }

        if (keys == null || keys.isEmpty()) {
            Logger.error("Unable to load keys, deleting preference store.");
            try {
                dao.deleteAll();
            } catch (Exception e) {
                Logger.error(e,"Failed to delete preferences.");
            }
            return;
        }

        List<Preference> fromStore = new ArrayList<>();

        for (String key : keys) {
            try {
                PreferenceData preferenceData = dao.queryValue(key);
                if (preferenceData.value == null) {
                    Logger.error("Unable to fetch preference value. Deleting: %s", key);
                    dao.delete(key);
                } else {
                    fromStore.add(new Preference(preferenceData.getKey(), preferenceData.getValue()));
                }
            } catch (Exception e) {
                Logger.error(e, "Failed to delete preference %s", key);
            }
        }
        finishLoad(fromStore);
    }

    private void finishLoad(@NonNull final List<Preference> preferences) {
        for (Preference preference : preferences) {
            this.preferences.put(preference.key, preference);
        }

        for (String key : OBSOLETE_KEYS) {
            remove(key);
        }
    }

    /**
     * Unregisters any observers and closes the db connection.
     */
    public void tearDown() {
        listeners.clear();
        db.close();
    }

    /**
     * Checks if the value is set.
     *
     * @param key The key.
     * @return {@code true} if the value is set, otherwise {@code false}.
     */
    public boolean isSet(@NonNull String key) {
        return getPreference(key).get() != null;
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
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * Get the String preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist.
     * @return The String value for the preference or defaultValue if it doesn't exist.
     */
    @SuppressLint("UnknownNullness")
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
    @NonNull
    public JsonValue getJsonValue(@NonNull String key) {
        try {
            return JsonValue.parseString(getPreference(key).get());
        } catch (JsonException e) {
            // Should never happen
            Logger.debug(e, "Unable to parse preference value: %s", key);
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
    public void put(@NonNull String key, @Nullable String value) {
        if (value == null) {
            remove(key);
        } else {
            getPreference(key).put(value);
        }
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
    public void put(@NonNull String key, @Nullable JsonValue value) {
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
    public void put(@NonNull String key, @Nullable JsonSerializable value) {
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
    public boolean putSync(@NonNull String key, @Nullable String value) {
        return getPreference(key).putSync(value);
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
    @NonNull
    private Preference getPreference(@NonNull String key) {
        synchronized (preferences) {
            Preference preference = preferences.get(key);
            if (preference == null) {
                preference = new Preference(key, null);
                preferences.put(key, preference);
            }
            return preference;
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
         * <p>
         * The preference will save its value with the Airship provider.
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
            Logger.verbose("Preference updated: %s", key);
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
        private boolean writeValue(@Nullable final String value) {
            synchronized (this) {
                try {
                    if (value == null) {
                        Logger.verbose("Removing preference: %s", key);
                        dao.delete(key);
                    } else {
                        Logger.verbose("Saving preference: %s value: %s", key, value);
                        dao.upsert(new PreferenceData(key, value));
                    }
                    return true;
                } catch (Exception e) {
                    Logger.error(e, "Failed to write preference %s:%s", key, value);
                    return false;
                }

            }
        }

    }
}
