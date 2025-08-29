/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import androidx.annotation.RestrictTo
import androidx.annotation.Size
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Creates a map of associated identifier. Used to associate identifiers with
 * [Analytics.editAssociatedIdentifiers].
 */
public class AssociatedIdentifiers (
    public val ids: Map<String, String> = emptyMap()
) : JsonSerializable {

    /**
     * Get the advertising ID.
     */
    public val advertisingId: String?
        get() = ids[ADVERTISING_ID_KEY]

    /**
     * Retrieves whether the user has limit ad tracking enabled or not.
     */
    public val isLimitAdTrackingEnabled: Boolean
        get() = "true".equals(ids[LIMITED_AD_TRACKING_ENABLED_KEY], ignoreCase = true)

    /** @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun toJsonValue(): JsonValue {
        return JsonValue.wrapOpt(ids)
    }

    /**
     * Interface use to modify identifiers in the AssociatedIdentifiers object. All changes you make
     * in the editor are batched, and not saved until you call apply().
     */
    public abstract class Editor public constructor() {

        private var clear = false
        private val idsToAdd = mutableMapOf<String, String>()
        private val idsToRemove = mutableListOf<String>()

        /**
         * Sets the Android advertising ID and the limit ad tracking enabled value.
         *
         * @param adId The Android advertising ID.
         * @param limitAdTrackingEnabled A boolean indicating whether the user has limit ad tracking enabled or not.
         * @return The editor object.
         */
        public fun setAdvertisingId(
            @Size(min = 1, max = MAX_CHARACTER_COUNT.toLong()) adId: String,
            limitAdTrackingEnabled: Boolean
        ): Editor {
            return this.apply {
                addIdentifier(ADVERTISING_ID_KEY, adId)
                addIdentifier(
                    LIMITED_AD_TRACKING_ENABLED_KEY, (if (limitAdTrackingEnabled) "true" else "false")
                )
            }
        }

        /**
         * Removes both the Android advertising ID and the limit ad tracking enabled value.
         *
         * @return The editor object.
         */
        public fun removeAdvertisingId(): Editor {
            return this.apply {
                removeIdentifier(ADVERTISING_ID_KEY)
                removeIdentifier(LIMITED_AD_TRACKING_ENABLED_KEY)
            }
        }

        /**
         * Adds an identifier.
         *
         * @param key The custom ID's key.
         * @param value The custom ID's value.
         * @return The editor object.
         */
        public fun addIdentifier(
            @Size(min = 1, max = MAX_CHARACTER_COUNT.toLong()) key: String,
            @Size(min = 1, max = MAX_CHARACTER_COUNT.toLong()) value: String
        ): Editor {
            return this.apply {
                idsToRemove.remove(key)
                idsToAdd[key] = value
            }
        }

        /**
         * Removes the identifier.
         *
         * @param key The custom ID's key.
         * @return The editor object.
         */
        public fun removeIdentifier(
            @Size(min = 1, max = MAX_CHARACTER_COUNT.toLong()) key: String
        ): Editor {
            return this.apply {
                idsToAdd.remove(key)
                idsToRemove.add(key)
            }
        }

        /**
         * Clears all the identifiers.
         *
         *
         * Identifiers will be cleared first during apply, then the other operations will be applied.
         *
         * @return The editor object.
         */
        public fun clear(): Editor {
            return this.also { it.clear = true}
        }

        /**
         * Applies the identifiers changes.
         */
        public fun apply(): Unit = onApply(clear, idsToAdd, idsToRemove)

        /**
         * Called when apply is called.
         *
         * @param clear `true` to clear all identifiers, otherwise `false`.
         * @param idsToAdd Identifiers to add.
         * @param idsToRemove Identifiers to remove.
         */
        public abstract fun onApply(
            clear: Boolean,
            idsToAdd: Map<String, String>,
            idsToRemove: List<String>
        )
    }

    public companion object {

        /**
         * Max character count for an ID or key.
         */
        public const val MAX_CHARACTER_COUNT: Int = 255

        /**
         * Max number of associated identifiers that can be set at a time.
         */
        public const val MAX_IDS: Int = 100

        private const val ADVERTISING_ID_KEY = "com.urbanairship.aaid"
        private const val LIMITED_AD_TRACKING_ENABLED_KEY = "com.urbanairship.limited_ad_tracking_enabled"

        /**
         * Parses associated identifiers from JSON.
         *
         * @param value The value.
         * @return The associated identifiers.
         * @throws JsonException
         * @hide
         */
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): AssociatedIdentifiers {
            if (!value.isJsonMap) {
                throw JsonException("Associated identifiers not found in JsonValue: $value")
            }

            val ids = value.optMap().associate { (it.key to it.value.optString()) }
            return AssociatedIdentifiers(ids)
        }
    }
}
