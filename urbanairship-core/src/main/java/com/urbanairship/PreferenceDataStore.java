/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.Observer;

/**
 * PreferenceDataStore stores and retrieves all the Airship preferences through the
 * {@link UrbanAirshipProvider}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class PreferenceDataStore {


    static final String COLUMN_NAME_KEY = "_id";

    private static final String[] OBSOLETE_KEYS = new String[] {
            "com.urbanairship.TAG_GROUP_HISTORIAN_RECORDS"
    };


    Executor executor = AirshipExecutors.newSerialExecutor();

    private final Map<String, Preference> preferences = new HashMap<>();
    private final List<Preference> fromStore = new ArrayList<>();
    @NonNull
    private final Context context;
    private PreferenceDataDao dao;
    private Observer<PreferenceData> observer = new Observer<PreferenceData>() {
        @Override
        public void onChanged(final PreferenceData preference) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (preference != null) {
                        preferences.get(preference.getKey()).syncValue();
                    }
                }
            });
        }
    };

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

    /**
     * Preferences constructor.
     *
     * @param context The application context.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public PreferenceDataStore(@NonNull Context context) {
        this.context = context;
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
    protected void init(AirshipConfigOptions airshipConfigOptions) {
        loadPreferences(airshipConfigOptions);
    }

    private void loadPreferences(AirshipConfigOptions airshipConfigOptions) {
        dao = PreferenceDataDatabase.createDatabase(context, airshipConfigOptions).getDao();

        try {
            List<PreferenceData> preferencesFromDao;

            preferencesFromDao = dao.getPreferences();

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
        List<String> keys = queryKeys();
        if (keys.isEmpty()) {
            Logger.error("Unable to load keys, deleting preference store.");
            dao.deleteAll();
            return;
        }

        List<Preference> fromStore = new ArrayList<>();

        for (String key : keys) {
            String value = queryValue(key);
            if (value == null) {
                Logger.error("Unable to fetch preference value. Deleting: %s", key);
                PreferenceData preferenceToDelete = new PreferenceData(key, null);
                dao.delete(preferenceToDelete);
            } else {
                fromStore.add(new Preference(key, value));
            }
        }
        finishLoad(fromStore);
    }

    private String queryValue(String key) {
        return dao.queryValue(key).value;
    }

    @NonNull
    private List<String> queryKeys() {
        List<String> preferences = dao.queryKeys();

        if (preferences == null) {
            return Collections.emptyList();
        } else {
            return preferences;
        }
    }

    private void finishLoad(@NonNull final List<Preference> preferences) {
        Logger.debug("finishLoad!");
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                observeLiveDataPreferences(preferences);
            }
        });
        for (Preference preference : preferences) {
            this.preferences.put(preference.key, preference);
        }
        for (String key : OBSOLETE_KEYS) {
            remove(key);
        }
    }

    @MainThread
    private void observeLiveDataPreferences(List<Preference> preferences) {
        for (Preference preference : preferences) {
            dao.queryLiveDataValue(preference.key).observeForever(observer);
        }
    }

    /**
     * Unregisters any observers.
     */
    protected void tearDown() {
        for (Preference preference : fromStore) {
            dao.queryLiveDataValue(preference.key).removeObserver(observer);
        }
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
        private boolean writeValue(@Nullable String value) {
            synchronized (this) {
                if (value == null) {
                    Logger.verbose("Removing preference: %s", key);
                    dao.delete(new PreferenceData(key, value));
                    return true;

                } else {
                    Logger.verbose("Saving preference: %s value: %s", key, value);
                    dao.insert(new PreferenceData(key, value));
                    return true;
                }
            }
        }

        /**
         * Syncs the value from the database to the preference.
         */
        void syncValue() {
            PreferenceData preferenceData;
            synchronized (this) {
                preferenceData = dao.queryValue(key);
            }

            if (preferenceData != null) {
                try {
                    setValue(preferenceData.value);
                } catch (Exception e) {
                    Logger.error(e, "Unable to sync preference %s from database", key);
                }
            } else {
                Logger.debug("Unable to get preference %s from database. Falling back to cached value.", key);
            }
        }
    }
}
