/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PreferenceDataStore stores and retrieves all the Airship preferences scoped at the app key.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceDataStore internal constructor(
    private val db: PreferenceDataDatabase,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {

    private val scope = CoroutineScope(dispatcher)
    private val preferences = MutableStateFlow<Map<String, Preference>>(emptyMap())

    private val dao = db.dao

    /**
     * Listener for when preferences changes either by the
     * current process or a different process.
     */
    public fun interface PreferenceChangeListener {

        /**
         * Called when a preference changes.
         *
         * @param key The key of the preference.
         */
        public fun onPreferenceChange(key: String)
    }

    private fun loadPreferences() {
        try {
            val preferencesFromDao = dao.getPreferences()
            val fromStore = preferencesFromDao.map { Preference(it.key, it.value) }
            finishLoad(fromStore)
        } catch (e: Exception) {
            UALog.e(e, "Failed to load preferences. Retrying with fallback loading.")
            fallbackLoad()
        }
    }

    private fun fallbackLoad() {
        val keys = try {
            dao.queryKeys()
        } catch (e: Exception) {
            UALog.e(e, "Failed to load keys.")
            null
        }

        if (keys.isNullOrEmpty()) {
            UALog.e("Unable to load keys, deleting preference store.")
            try {
                dao.deleteAll()
            } catch (e: Exception) {
                UALog.e(e, "Failed to delete preferences.")
            }
            return
        }

        val fromStore = keys
            .map { dao.queryValue(it) }
            .mapNotNull {
                if (it.value == null) {
                    UALog.e("Unable to fetch preference value. Deleting: %s", it.key)
                    try {
                        dao.delete(it.key)
                    } catch (ex: Exception) {
                        UALog.e(ex, "Failed to delete preference %s", it.key)
                    }
                    null
                } else {
                    Preference(it.key, it.value)
                }
            }

        finishLoad(fromStore)
    }

    private fun finishLoad(preferences: List<Preference>) {
        this.preferences.update { current ->
            val mutable = current.toMutableMap()
            preferences.forEach { mutable[it.key] = it }
            mutable.toMap()
        }

        for (key in OBSOLETE_KEYS) {
            remove(key)
        }
    }

    /**
     * Unregisters any observers and closes the db connection.
     */
    public fun tearDown() {
        db.close()
    }

    /**
     * Checks if the value is set.
     *
     * @param key The key.
     * @return `true` if the value is set, otherwise `false`.
     */
    public fun isSet(key: String): Boolean {
        return getPreference(key).get() != null
    }

    /**
     * Get the boolean preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist.
     * @return The boolean value for the preference or defaultValue if it doesn't exist.
     */
    public fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return getPreference(key).get()?.toBoolean() ?: defaultValue
    }

    /**
     * Get the String preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist.
     * @return The String value for the preference or defaultValue if it doesn't exist.
     */
    public fun getString(key: String, defaultValue: String?): String? {
        return getPreference(key).get() ?: defaultValue
    }

    /**
     * Get the long preference.
     *
     * @param key The preference name.
     * @param defaultValue The value to return if the preference doesn't exist or
     * cannot be coerced into a long.
     * @return The long value for the preference or defaultValue if it doesn't exist.
     */
    public fun getLong(key: String, defaultValue: Long): Long {
        val value = getPreference(key).get() ?: return defaultValue

        return try {
            value.toLong()
        } catch (e: NumberFormatException) {
            defaultValue
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
    public fun getInt(key: String, defaultValue: Int): Int {
        val value = getPreference(key).get() ?: return defaultValue

        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    /**
     * Get the preference value as a [JsonValue].
     *
     * @param key The preference name.
     * @return The value for the preference if available or [JsonValue.NULL] if it doesn't exist.
     */
    public fun getJsonValue(key: String): JsonValue {
        try {
            return JsonValue.parseString(getPreference(key).get())
        } catch (e: JsonException) {
            // Should never happen
            UALog.d(e, "Unable to parse preference value: %s", key)
            return JsonValue.NULL
        }
    }

    public fun optJsonValue(key: String): JsonValue? {
        try {
            return JsonValue.parseString(getPreference(key).get())
        } catch (e: JsonException) {
            // Should never happen
            UALog.d(e, "Unable to parse preference value: %s", key)
            return null
        }
    }

    /**
     * Delete a key/value pair.
     *
     * @param key The preference name.
     */
    public fun remove(key: String) {
        preferences.value[key]?.put(null)
    }

    /**
     * Stores a String value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public fun put(key: String, value: String?) {
        if (value == null) {
            remove(key)
        } else {
            getPreference(key).put(value)
        }
    }

    /**
     * Stores a long value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public fun put(key: String, value: Long) {
        getPreference(key).put(value.toString())
    }

    /**
     * Stores an int value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public fun put(key: String, value: Int) {
        getPreference(key).put(value.toString())
    }

    /**
     * Stores a boolean value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public fun put(key: String, value: Boolean) {
        getPreference(key).put(value.toString())
    }

    /**
     * Stores a [JsonValue] value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public fun put(key: String, value: JsonValue?) {
        if (value == null) {
            remove(key)
        } else {
            getPreference(key).put(value.toString())
        }
    }

    /**
     * Stores a [JsonSerializable] value in the preferences.
     *
     * @param key The preference name.
     * @param value The preference value.
     */
    public fun put(key: String, value: JsonSerializable?) {
        if (value == null) {
            remove(key)
        } else {
            put(key, value.toJsonValue())
        }
    }

    /**
     * Put new or replace an existing preference. This method will block on the
     * database write.
     *
     * @param key The preference name.
     * @param value The preference value.
     * @return `true` if the preference was successfully saved to
     * the database, otherwise `false`
     */
    public fun putSync(key: String, value: String?): Boolean {
        return getPreference(key).putSync(value)
    }

    /**
     * Gets the Preference for the key.
     *
     * @param key The preference key.
     * @return A preference for the key.
     */
    private fun getPreference(key: String): Preference {
        var result = Preference(key, null)

        preferences.update { current ->
            val mutable = current.toMutableMap()
            result = mutable.getOrPut(key) { Preference(key, null) }
            mutable.toMap()
        }

        return result
    }

    /**
     * A helper class that handles fetching, writing, and syncing with the
     * preference provider.
     */
    private inner class Preference(
        val key: String,
        private var value: String?
    ) {

        /**
         * Get the current value of the preference
         *
         * @return The value of the preference.
         */
        fun get(): String? {
            synchronized(this) {
                return value
            }
        }

        /**
         * Put a new value for the preference.
         *
         * @param value Value of the preference.
         */
        fun put(value: String?) {
            if (setValue(value)) {
                scope.launch { writeValue(value) }
            }
        }

        /**
         * Puts the preferences value.
         *
         *
         * The preference will save its value with the Airship provider.
         *
         * @param value Value of the preference.
         * @return `true` if the preference was successfully saved to
         * the database, otherwise `false`
         */
        fun putSync(value: String?): Boolean {
            synchronized(this) {
                if (writeValue(value)) {
                    setValue(value)
                    return true
                }
                return false
            }
        }

        /**
         * Sets the value. If the value is different it calls the onPreferenceChanged
         * method.
         *
         * @param value The value of the preference.
         * @return `true` if the value changed, otherwise `false`.
         */
        fun setValue(value: String?): Boolean {
            synchronized(this) {
                if (value == this.value) {
                    return false
                }
                this.value = value
            }
            UALog.v("Preference updated: %s", key)
            return true
        }

        /**
         * Actually writes the value to the database.
         *
         * @param value The value to write
         * @return `true` if the preference was successfully written to
         * the database, otherwise `false`
         */
        fun writeValue(value: String?): Boolean {
            synchronized(this) {
                try {
                    if (value == null) {
                        UALog.v("Removing preference: %s", key)
                        dao.delete(key)
                    } else {
                        UALog.v("Saving preference: %s value: %s", key, value)
                        dao.upsert(PreferenceData(key, value))
                    }
                    return true
                } catch (e: Exception) {
                    UALog.e(e, "Failed to write preference %s:%s", key, value)
                    return false
                }
            }
        }
    }

    public companion object {

        private val OBSOLETE_KEYS = arrayOf(
            "com.urbanairship.TAG_GROUP_HISTORIAN_RECORDS",
            "com.urbanairship.push.iam.PENDING_IN_APP_MESSAGE",
            "com.urbanairship.push.iam.AUTO_DISPLAY_ENABLED",
            "com.urbanairship.push.iam.LAST_DISPLAYED_ID",
            "com.urbanairship.nameduser.CHANGE_TOKEN_KEY",
            "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY",
            "com.urbanairship.iam.tags.TAG_PREFER_LOCAL_DATA_TIME",
            "com.urbanairship.chat.CHAT",
            "com.urbanairship.user.LAST_MESSAGE_REFRESH_TIME",
            "com.urbanairship.push.LAST_REGISTRATION_TIME",
            "com.urbanairship.push.LAST_REGISTRATION_PAYLOAD",
            "com.urbanairship.remotedata.LAST_REFRESH_APP_VERSION",
            "com.urbanairship.remotedata.LAST_MODIFIED",
            "com.urbanairship.remotedata.LAST_REFRESH_TIME",
            "com.urbanairship.iam.data.last_payload_info",
            "com.urbanairship.iam.data.LAST_PAYLOAD_METADATA",
            "com.urbanairship.iam.data.contact_last_payload_info"
        )

        /** @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun loadDataStore(
            context: Context, configOptions: AirshipConfigOptions
        ): PreferenceDataStore {
            val db = PreferenceDataDatabase.createDatabase(context, configOptions)
            val dataStore = PreferenceDataStore(db)
            if (db.exists(context)) {
                dataStore.loadPreferences()
            }

            return dataStore
        }

        /** @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public fun inMemoryStore(context: Context): PreferenceDataStore {
            val db = PreferenceDataDatabase.createInMemoryDatabase(context)
            val dataStore = PreferenceDataStore(db)
            return dataStore
        }
    }
}
