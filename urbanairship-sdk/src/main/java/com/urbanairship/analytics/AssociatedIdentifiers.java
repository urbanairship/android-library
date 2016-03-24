/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.analytics;

import android.support.annotation.NonNull;
import android.support.annotation.Size;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates a map of associated identifier. Used to associate identifiers with
 * {@link Analytics#associateIdentifiers(AssociatedIdentifiers)}.
 */
public class AssociatedIdentifiers {
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
     * Creates an AssociatedIdentifiers object from a
     * {@link com.urbanairship.analytics.AssociatedIdentifiers.Builder}.
     *
     * @param builder The AssociatedIdentifiers builder.
     * @deprecated Marked to be removed in 8.0.0. Use Editor instead.
     */
    @Deprecated
    private AssociatedIdentifiers(Builder builder) {
        this.ids = new HashMap<>(builder.ids);
    }

    /**
     * Modifies an AssociatedIdentifiers object from a
     * {@link com.urbanairship.analytics.AssociatedIdentifiers.Editor}.
     *
     * @param editor The AssociatedIdentifiers editor.
     */
    private AssociatedIdentifiers(Editor editor) {
        this.ids = new HashMap<>(editor.ids);
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
     *  Builder to construct AssociatedIdentifiers.
     *  @deprecated Marked to be removed in 8.0.0. Use Editor instead.
     */
    @Deprecated
    public static class Builder {
        private final Map<String, String> ids = new HashMap<>();

        /**
         * Sets the Android advertising ID.
         *
         * @param adId The Android advertising ID.
         * @return The builder object.
         * @deprecated Marked to be removed in 8.0.0.
         */
        @Deprecated
        public Builder setAdvertisingId(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String adId) {
            ids.put(ADVERTISING_ID_KEY, adId);
            return this;
        }

        /**
         * Sets the limit ad tracking enabled value.
         * @param enabled A boolean indicating whether the user has limit ad tracking enabled or not.
         * @return The builder object.
         * @deprecated Marked to be removed in 8.0.0.
         */
        @Deprecated
        public Builder setLimitedAdTrackingEnabled(boolean enabled) {
            ids.put(LIMITED_AD_TRACKING_ENABLED_KEY, (enabled ? "true" : "false"));
            return this;
        }

        /**
         * Sets an identifier.
         *
         * @param key The custom ID's key.
         * @param value The custom ID's value.
         * @return The builder object.
         * @deprecated Marked to be removed in 8.0.0.
         */
        @Deprecated
        public Builder setIdentifier(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String key,
                                     @NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String value) {
            ids.put(key, value);
            return this;
        }

        /**
         * Creates the AssociatedIdentifiers instance.
         *
         * @return The AssociatedIdentifiers instance.
         * @deprecated Marked to be removed in 8.0.0.
         */
        @Deprecated
        public AssociatedIdentifiers create() {
            return new AssociatedIdentifiers(this);
        }
    }

    /**
     * Editor to modify AssociatedIdentifiers.
     */
    public static class Editor {
        private Map<String, String> ids = new HashMap<>();

        /**
         * Editor constructor
         */
        public Editor() {
        }

        /**
         * Editor constructor
         *
         * @param identifiers The associated identifiers map.
         */
        public Editor(Map<String, String> identifiers) {
            if (identifiers != null) {
                ids = identifiers;
            }
        }

        /**
         * Sets the Android advertising ID and the limit ad tracking enabled value.
         * @param adId The Android advertising ID.
         * @param limitedAdTrackingEnabled A boolean indicating whether the user has limit ad tracking enabled or not.
         * @return The editor object.
         */
        public Editor setAdvertisingId(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String adId,
                                       boolean limitedAdTrackingEnabled) {
            ids.put(ADVERTISING_ID_KEY, adId);
            ids.put(LIMITED_AD_TRACKING_ENABLED_KEY, (limitedAdTrackingEnabled ? "true" : "false"));
            return this;
        }

        /**
         * Removes both the Android advertising ID and the limit ad tracking enabled value.
         * @return The editor object.
         */
        public Editor removeAdvertisingId() {
            ids.remove(ADVERTISING_ID_KEY);
            ids.remove(LIMITED_AD_TRACKING_ENABLED_KEY);
            return this;
        }

        /**
         * Adds an identifier.
         * @param key The custom ID's key.
         * @param value The custom ID's value.
         * @return The editor object.
         */
        public Editor addIdentifier(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String key,
                                    @NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String value) {
            ids.put(key, value);
            return this;
        }

        /**
         * Removes the identifier.
         * @param key The custom ID's key.
         * @return The editor object.
         */
        public Editor removeIdentifier(@NonNull @Size(min = 1, max = MAX_CHARACTER_COUNT) String key) {
            ids.remove(key);
            return this;
        }

        /**
         * Removes all the identifiers.
         * @return The editor object.
         */
        public Editor clearAll() {
            ids.clear();
            return this;
        }

        /**
         * Returns the modified AssociatedIdentifiers object.
         *
         * @return The AssociatedIdentifiers object.
         */
        public AssociatedIdentifiers apply() {
            return new AssociatedIdentifiers(this);
        }
    }
}
