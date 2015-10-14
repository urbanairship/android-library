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

import android.preference.PreferenceScreen;

/**
 * An adapter to set Urban Airship preferences from Android preference screens without
 * saving values to the shared preferences.
 * @deprecated Marked to be removed in 7.0.0. This class is no longer necessary. Urban Airship preferences
 * will now apply on their own.
 */
@Deprecated
public class UAPreferenceAdapter {

    /**
     * UAPreferenceAdapter constructor
     *
     * @param screen PreferenceScreen that contains any UAPreferences.  Only UAPreferences will be affected.
     */
    public UAPreferenceAdapter(PreferenceScreen screen) {
        // No longer used
    }

    /**
     * Applies any preferences to UAirship preferences.
     * <p/>
     * This should be called on the onStop() method of a preference activity.
     */
    public void applyUrbanAirshipPreferences() {
        // No longer used
    }
}
