/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.location;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonException;

/**
 * Location service preferences.
 */
class LocationPreferences implements PreferenceDataStore.PreferenceChangeListener {

    static final String KEY_PREFIX = "com.urbanairship.location";
    static final String LOCATION_UPDATES_ENABLED = KEY_PREFIX + ".LOCATION_UPDATES_ENABLED";
    static final String BACKGROUND_UPDATES_ALLOWED = KEY_PREFIX + ".BACKGROUND_UPDATES_ALLOWED";
    static final String LOCATION_OPTIONS = KEY_PREFIX + ".LOCATION_OPTIONS";
    private final PreferenceDataStore preferenceDataStore;

    private PreferenceDataStore.PreferenceChangeListener preferenceChangeListener;

    /**
     * Location preferences constructor.
     */
    LocationPreferences(PreferenceDataStore preferenceDataStore) {
        this.preferenceDataStore = preferenceDataStore;
        preferenceDataStore.addListener(this);
    }

    /**
     * Sets the listener for changes in preferences.
     *
     * @param listener The preference change listener.
     */
    void setListener(PreferenceDataStore.PreferenceChangeListener listener) {
        synchronized (this) {
            this.preferenceChangeListener = listener;
        }
    }

    @Override
    public void onPreferenceChange(String key) {
        if (key.startsWith(KEY_PREFIX)) {
            synchronized (this)  {
                if (preferenceChangeListener != null) {
                    preferenceChangeListener.onPreferenceChange(key);
                }
            }
        }
    }

    /**
     * Gets the current value of the location updates enabled preference.
     *
     * @return <code>true</code> if enabled, <code>false</code> otherwise.
     */
    boolean isLocationUpdatesEnabled() {
        return preferenceDataStore.getBoolean(LOCATION_UPDATES_ENABLED, false);
    }

    /**
     * Sets the value of the location updates enabled preference.
     *
     * @param enabled The value of the preference.
     */
    void setLocationUpdatesEnabled(boolean enabled) {
        preferenceDataStore.put(LOCATION_UPDATES_ENABLED, enabled);
    }

    /**
     * Background location allowed preference.
     *
     * @return <code>true</code> if allowed, <code>false</code> otherwise.
     */
    boolean isBackgroundLocationAllowed() {
        return preferenceDataStore.getBoolean(BACKGROUND_UPDATES_ALLOWED, false);
    }

    /**
     * Sets the value of the background location allowed preference.
     *
     * @param enabled The value of the preference.
     */
    void setBackgroundLocationAllowed(boolean enabled) {
        preferenceDataStore.put(BACKGROUND_UPDATES_ALLOWED, enabled);
    }

    /**
     * Sets the value of location request options preference.
     *
     * @param options The LocationRequestOptions to save.
     */
    void setLocationRequestOptions(LocationRequestOptions options) {
        preferenceDataStore.put(LOCATION_OPTIONS, options.toJsonValue().toString());
    }

    /**
     * Gets the value of location request options preference.
     *
     * @return The current location request options.
     */
    LocationRequestOptions getLocationRequestOptions() {
        String jsonString = preferenceDataStore.getString(LOCATION_OPTIONS, null);

        if (jsonString != null) {
            try {
                return LocationRequestOptions.parseJson(jsonString);
            } catch (JsonException e) {
                Logger.error("LocationPreferences - Failed parsing LocationRequestOptions from JSON: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                Logger.error("LocationPreferences - Invalid LocationRequestOptions from JSON: " + e.getMessage());
            }
        }

        return null;
    }


}
