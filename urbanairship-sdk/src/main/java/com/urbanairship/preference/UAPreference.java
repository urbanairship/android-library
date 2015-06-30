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

package com.urbanairship.preference;


/**
 * UAPreference interface.
 */
public interface UAPreference {

    /**
     * An enum representing the preference type.
     */
    public enum PreferenceType {
        /**
         * User notifications enabled preference
         */
        USER_NOTIFICATIONS_ENABLED,

        /**
         * Sound enabled preference
         */
        SOUND_ENABLED,

        /**
         * Vibrate enabled preference
         */
        VIBRATE_ENABLED,

        /**
         * Quiet time enabled preference
         */
        QUIET_TIME_ENABLED,

        /**
         * Quiet time's start preference
         */
        QUIET_TIME_START,

        /**
         * Quiet time's end preference
         */
        QUIET_TIME_END,

        /**
         * Location updates enabled preference
         */
        LOCATION_UPDATES_ENABLED,

        /**
         * Location background updates allowed preference
         */
        LOCATION_BACKGROUND_UPDATES_ALLOWED,

        /**
         * Channel ID preference
         */
        CHANNEL_ID,

        /**
         * User ID
         */
        USER_ID,

        /**
         * Analytics enabled preference
         */
        ANALYTICS_ENABLED
    }

    /**
     * Gets the preference type.
     *
     * @return PreferenceType type of UAPreference.
     */
    public PreferenceType getPreferenceType();

    /**
     * Sets the current value of the preference.
     *
     * @param value The value of the preference.
     */
    public void setValue(Object value);
}
