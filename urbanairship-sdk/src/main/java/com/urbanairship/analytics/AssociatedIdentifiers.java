/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.support.annotation.NonNull;
import android.support.annotation.Size;

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
    public Map<String, String> getIds() {
        return Collections.unmodifiableMap(ids);
    }

    /**
     * Get the advertising ID.
     *
     * @return The advertising ID or null if not found.
     */
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

    @Override
    public JsonValue toJsonValue() {
        return JsonValue.wrapOpt(ids);
    }

    public static AssociatedIdentifiers fromJson(String json) throws JsonException {

        Map<String, String> ids = new HashMap<>();

        JsonValue idsJasonValue = JsonValue.parseString(json);
        if (idsJasonValue != null && idsJasonValue.isJsonMap()) {
            for (Map.Entry<String, JsonValue> entry : idsJasonValue.getMap()) {
                ids.put(entry.getKey(), entry.getValue().getString());
            }
        }

        return new AssociatedIdentifiers(ids);
    }

    /**
     * Interface use to modify identifiers in the AssociatedIdentifiers object. All changes you make
     * in the editor are batched, and not saved until you call apply().
     */
    public static abstract class Editor {

        private boolean clear = false;
        private Map<String, String> idsToAdd = new HashMap<>();
        private List<String> idsToRemove = new ArrayList<>();

        /**
         * Editor constructor
         */
        Editor() {
        }

        /**
         * Sets the Android advertising ID and the limit ad tracking enabled value.
         *
         * @param adId The Android advertising ID.
         * @param limitAdTrackingEnabled A boolean indicating whether the user has limit ad tracking enabled or not.
         * @return The editor object.
         */
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
        public Editor removeIdentifier(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String key) {
            idsToAdd.remove(key);
            idsToRemove.add(key);
            return this;
        }

        /**
         * Clears all the identifiers.
         * </p>
         * Identifiers will be cleared first during apply, then the other operations will be applied.
         *
         * @return The editor object.
         */
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
        abstract void onApply(boolean clear, Map<String, String> idsToAdd, List<String> idsToRemove);
    }
}
