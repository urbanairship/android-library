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

package com.urbanairship.preference;

import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.preference.UAPreference.PreferenceType;
import com.urbanairship.push.PushManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * An adapter to set Urban Airship preferences from Android preference screens without
 * saving values to the shared preferences.
 */
public class UAPreferenceAdapter {

    /**
     * Maximum times to check for Channel ID value when
     * it should be enabled, yet the Channel ID is not populated
     */
    private static int CHANNEL_ID_MAX_RETRIES = 4;

    /**
     * Delay in milliseconds for each Channel ID retry
     * attempts
     */
    private static int CHANNEL_ID_RETRY_DELAY = 1000;

    private Map<UAPreference.PreferenceType, Object> preferences = new HashMap<UAPreference.PreferenceType, Object>();
    private int channelIdRetryCount = 0;

    /**
     * UAPreferenceAdapter constructor
     *
     * @param screen PreferenceScreen that contains any UAPreferences.  Only UAPreferences will be affected.
     */
    public UAPreferenceAdapter(PreferenceScreen screen) {
        checkForUAPreferences(screen);
    }

    /**
     * Applies any preferences to UAirship preferences.
     * <p/>
     * This should be called on the onStop() method of a preference activity.
     */
    public void applyUrbanAirshipPreferences() {
        for (UAPreference.PreferenceType preferenceType : preferences.keySet()) {
            Object value = preferences.get(preferenceType);
            if (value == null) {
                continue;
            }

            try {
                setInternalPreference(preferenceType, value);
            } catch (Exception ex) {
                Logger.warn("Unable to set " + preferenceType + ", invalid value " + value, ex);
            }
        }
    }

    /**
     * Gets the internal UAirship preferences.
     *
     * @return Object value of the internal preference.
     */
    private Object getInternalPreference(UAPreference.PreferenceType preferenceType) {
        UAirship airship = UAirship.shared();

        Date[] quietTimes;
        Object value = null;
        switch (preferenceType) {
            case LOCATION_UPDATES_ENABLED:
                value = UALocationManager.shared().isLocationUpdatesEnabled();
                break;
            case LOCATION_BACKGROUND_UPDATES_ALLOWED:
                value = UALocationManager.shared().isBackgroundLocationAllowed();
                break;
            case USER_NOTIFICATIONS_ENABLE:
                value = airship.getPushManager().getUserNotificationsEnabled();
                break;
            case QUIET_TIME_ENABLE:
                value = airship.getPushManager().isQuietTimeEnabled();
                break;
            case QUIET_TIME_END:
                quietTimes = airship.getPushManager().getQuietTimeInterval();
                value = quietTimes != null ? quietTimes[1].getTime() : null;
                break;
            case QUIET_TIME_START:
                quietTimes = airship.getPushManager().getQuietTimeInterval();
                value = quietTimes != null ? quietTimes[0].getTime() : null;
                break;
            case SOUND_ENABLE:
                value = airship.getPushManager().isSoundEnabled();
                break;
            case VIBRATE_ENABLE:
                value = airship.getPushManager().isVibrateEnabled();
                break;
            case CHANNEL_ID:
                value = airship.getPushManager().getChannelId();
                break;
            case USER_ID:
                value = airship.getRichPushManager().getRichPushUser().getId();
                break;
        }

        return value;
    }


    /**
     * Sets the internal UAirship preferences.
     *
     * @param preferenceType UAPreference.PreferenceType type of preference to set.
     * @param value Object Value of the preference.
     */
    private void setInternalPreference(UAPreference.PreferenceType preferenceType, Object value) {
        UAirship airship = UAirship.shared();

        Date[] quietTimes;

        switch (preferenceType) {
            case LOCATION_BACKGROUND_UPDATES_ALLOWED:
                UALocationManager.shared().setBackgroundLocationAllowed((Boolean) value);
                break;
            case LOCATION_UPDATES_ENABLED:
                UALocationManager.shared().setLocationUpdatesEnabled((Boolean) value);
                break;
            case USER_NOTIFICATIONS_ENABLE:
                PushManager.shared().setUserNotificationsEnabled((Boolean) value);
                break;
            case QUIET_TIME_ENABLE:
                airship.getPushManager().setQuietTimeEnabled((Boolean) value);
                break;
            case SOUND_ENABLE:
                airship.getPushManager().setSoundEnabled((Boolean) value);
                break;
            case VIBRATE_ENABLE:
                airship.getPushManager().setVibrateEnabled((Boolean) value);
                break;
            case QUIET_TIME_END:
                quietTimes = airship.getPushManager().getQuietTimeInterval();
                Date start = quietTimes != null ? quietTimes[0] : new Date();
                airship.getPushManager().setQuietTimeInterval(start, new Date((Long) value));
                break;
            case QUIET_TIME_START:
                quietTimes = airship.getPushManager().getQuietTimeInterval();
                Date end = quietTimes != null ? quietTimes[1] : new Date();
                airship.getPushManager().setQuietTimeInterval(new Date((Long) value), end);
                break;
            case CHANNEL_ID:
            case USER_ID:
                // do nothing
                break;
            default:
                break;
        }
    }

    /**
     * Finds any UAPreference, sets its value, and listens for any
     * value changes.
     *
     * @param group PreferenceGroup to check for preferences
     */
    private void checkForUAPreferences(PreferenceGroup group) {
        if (group == null) {
            return;
        }

        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);

            if (preference instanceof PreferenceGroup) {
                checkForUAPreferences((PreferenceGroup) preference);
            } else if (preference instanceof UAPreference) {
                trackPreference((UAPreference) preference);
            }
        }
    }

    /**
     * Tries to track a UAPreference if the service it depends on is enabled,
     * it has a valid preference type, and is able to have its value set
     *
     * @param preference UAPreference to track
     */
    private void trackPreference(final UAPreference preference) {

        PushManager pushManager = UAirship.shared().getPushManager();
        final UAPreference.PreferenceType preferenceType = preference.getPreferenceType();

        if (preferenceType == null) {
            Logger.warn("Preference returned a null preference type. " + "Ignoring preference " + preference);
            return;
        }

        // Try to set the initial value if its not null
        Object defaultValue = getInternalPreference(preferenceType);
        if (defaultValue != null) {
            try {
                preference.setValue(defaultValue);
            } catch (Exception ex) {
                Logger.warn("Exception setting value " + defaultValue + ". Ignoring preference " + preference, ex);
                return;
            }
        } else if (preferenceType == PreferenceType.CHANNEL_ID) {
            //If we should have a value, try tracking the preference in a second
            if (pushManager.isPushEnabled() && channelIdRetryCount < CHANNEL_ID_MAX_RETRIES) {
                channelIdRetryCount++;
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        trackPreference(preference);
                    }
                }, CHANNEL_ID_RETRY_DELAY);

                return;
            }
        }

        // Track any changes to the preference
        ((Preference) preference).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preferences.put(preferenceType, newValue);
                return true;
            }
        });
    }
}
