package com.urbanairship.preferencecenter.data

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.util.DateUtils

/**
 * Configuration for a Preference Center.
 */
data class PreferenceForm(
    val id: String,
    val created: Long,
    val lastUpdated: Long,
    // TODO: update this to a data class once pref centers are included in remote data
    val data: JsonMap
) {
    companion object {
        private const val KEY_CREATED = "created"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val KEY_FORM_ID = "form_id"
        private const val KEY_FORM = "form"

        /**
         * Creates a `PreferenceForm` from a [JsonMap].
         *
         * @param json A Remote Data payload containing configuration for a Preference Center.
         * @throws JsonException
         *
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        internal fun fromJson(json: JsonMap): PreferenceForm =
            PreferenceForm(
                created = DateUtils.parseIso8601(json.requireField(KEY_CREATED), 0),
                lastUpdated = DateUtils.parseIso8601(json.requireField(KEY_LAST_UPDATED), 0),
                id = json.requireField(KEY_FORM_ID),
                data = json.requireField(KEY_FORM)
            )
    }
    /**
     * Serializes a `PreferenceForm` into a `JsonMap`.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun toJson(): JsonMap = JsonMap.newBuilder()
            .put(KEY_FORM_ID, id)
            .put(KEY_CREATED, DateUtils.createIso8601TimeStamp(created))
            .put(KEY_LAST_UPDATED, DateUtils.createIso8601TimeStamp(lastUpdated))
            .put(KEY_FORM, data)
            .build()
}

private inline fun <reified T> JsonMap.requireField(key: String): T {
    val field = requireNotNull(get(key)) { "Missing required field: '$key'" }
    return when (T::class) {
        String::class -> field.optString() as T
        Boolean::class -> field.getBoolean(false) as T
        Long::class -> field.getLong(0) as T
        Double::class -> field.getDouble(0.0) as T
        Integer::class -> field.getInt(0) as T
        JsonList::class -> field.optList() as T
        JsonMap::class -> field.optMap() as T
        else -> throw JsonException("Invalid type '${T::class.java.simpleName}' for field '$key'")
    }
}
