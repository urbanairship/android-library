/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.Size;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a map of associated identifier. Used to associate identifiers with
 * {@link Analytics#editAssociatedIdentifiers()}.
 */
public class AssociatedIdentifiers implements JsonSerializable {

    /**
     * Max character count for an ID or key.
     */
    public static final int MAX_CHARACTER_COUNT = 255;

    /**
     * Max number of associated identifiers that can be set at a time.
     */
    public static final int MAX_IDS = 100;

    private static final String ADVERTISING_ID_KEY = "com.urbanairship.aaid";
    private static final String LIMITED_AD_TRACKING_ENABLED_KEY = "com.urbanairship.limited_ad_tracking_enabled";

    @NonNull
    private final Map<String, String> ids;

    /**
     * Creates an AssociatedIdentifiers object.
     */
    AssociatedIdentifiers() {
        this.ids = new HashMap<>();
    }

    /**
     * Creates an AssociatedIdentifiers object from a map of identifiers.
     *
     * @param ids The map of identifiers.
     */
    AssociatedIdentifiers(Map<String, String> ids) {
        this.ids = new HashMap<>(ids);
    }

    /**
     * Gets the associated identifiers as a unmodifiable map.
     *
     * @return The associated identifiers.
     */
    @NonNull
    public Map<String, String> getIds() {
        return Collections.unmodifiableMap(ids);
    }

    /**
     * Get the advertising ID.
     *
     * @return The advertising ID or null if not found.
     */
    @Nullable
    public String getAdvertisingId() {
        return ids.get(ADVERTISING_ID_KEY);
    }

    /**
     * Retrieves whether the user has limit ad tracking enabled or not.
     *
     * @return <code>true</code> if user limit ad tracking enabled, <code>false</code> otherwise.
     */
    public boolean isLimitAdTrackingEnabled() {
        String enabled = ids.get(LIMITED_AD_TRACKING_ENABLED_KEY);
        return enabled != null && enabled.equalsIgnoreCase("true");
    }

    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonValue toJsonValue() {
        return JsonValue.wrapOpt(ids);
    }

    /**
     * See {@link #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed AssociatedIdentifiers.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static AssociatedIdentifiers fromJson(@Nullable String json) throws JsonException {
        return fromJson(JsonValue.parseString(json));
    }

    /**
     * Parses associated identifiers from JSON.
     *
     * @param value The value.
     * @return The associated identifiers.
     * @throws JsonException
     * @hide
     */
    @NonNull
    public static AssociatedIdentifiers fromJson(@NonNull JsonValue value) throws JsonException {

        Map<String, String> ids = new HashMap<>();

        if (value.isJsonMap()) {
            for (Map.Entry<String, JsonValue> entry : value.optMap()) {
                ids.put(entry.getKey(), entry.getValue().optString());
            }
        } else {
            throw new JsonException("Associated identifiers not found in JsonValue: " + value);
        }

        return new AssociatedIdentifiers(ids);
    }

    /**
     * Interface use to modify identifiers in the AssociatedIdentifiers object. All changes you make
     * in the editor are batched, and not saved until you call apply().
     */
    public static abstract class Editor {

        private boolean clear = false;
        private final Map<String, String> idsToAdd = new HashMap<>();
        private final List<String> idsToRemove = new ArrayList<>();

        /**
         * Sets the Android advertising ID and the limit ad tracking enabled value.
         *
         * @param adId The Android advertising ID.
         * @param limitAdTrackingEnabled A boolean indicating whether the user has limit ad tracking enabled or not.
         * @return The editor object.
         */
        @NonNull
        public Editor setAdvertisingId(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String adId,
                                       boolean limitAdTrackingEnabled) {
            addIdentifier(ADVERTISING_ID_KEY, adId);
            addIdentifier(LIMITED_AD_TRACKING_ENABLED_KEY, (limitAdTrackingEnabled ? "true" : "false"));
            return this;
        }

        /**
         * Removes both the Android advertising ID and the limit ad tracking enabled value.
         *
         * @return The editor object.
         */
        @NonNull
        public Editor removeAdvertisingId() {
            removeIdentifier(ADVERTISING_ID_KEY);
            removeIdentifier(LIMITED_AD_TRACKING_ENABLED_KEY);
            return this;
        }

        /**
         * Adds an identifier.
         *
         * @param key The custom ID's key.
         * @param value The custom ID's value.
         * @return The editor object.
         */
        @NonNull
        public Editor addIdentifier(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String key,
                                    @NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String value) {
            idsToRemove.remove(key);
            idsToAdd.put(key, value);
            return this;
        }

        /**
         * Removes the identifier.
         *
         * @param key The custom ID's key.
         * @return The editor object.
         */
        @NonNull
        public Editor removeIdentifier(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String key) {
            idsToAdd.remove(key);
            idsToRemove.add(key);
            return this;
        }

        /**
         * Clears all the identifiers.
         * <p>
         * Identifiers will be cleared first during apply, then the other operations will be applied.
         *
         * @return The editor object.
         */
        @NonNull
        public Editor clear() {
            clear = true;
            return this;
        }

        /**
         * Applies the identifiers changes.
         */
        public void apply() {
            onApply(clear, idsToAdd, idsToRemove);
        }

        /**
         * Called when apply is called.
         *
         * @param clear {@code true} to clear all identifiers, otherwise {@code false}.
         * @param idsToAdd Identifiers to add.
         * @param idsToRemove Identifiers to remove.
         */
        abstract void onApply(boolean clear, @NonNull Map<String, String> idsToAdd, @NonNull List<String> idsToRemove);

    }

}
