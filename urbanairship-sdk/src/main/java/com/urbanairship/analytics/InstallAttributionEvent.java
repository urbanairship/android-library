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

package com.urbanairship.analytics;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Event to track Google Play Store referrals
 */
class InstallAttributionEvent extends Event {

    private static final String TYPE = "install_attribution";
    private static final String PLAY_STORE_REFERRER = "google_play_referrer";

    private final String referrer;

    /**
     * Default constructor.
     *
     * @param referrer The Play Store install referrer.
     */
    public InstallAttributionEvent(@NonNull String referrer) {
        this.referrer = referrer;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected JSONObject getEventData() {
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.putOpt(PLAY_STORE_REFERRER, referrer);
        } catch (JSONException e) {
            Logger.error("InstallAttributionEvent - Error constructing JSON data.", e);
        }

        return jsonObject;
    }
}
