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

import android.os.Build;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class AppForegroundEvent extends Event {

    static final String TYPE = "app_foreground";

    static final String NOTIFICATION_TYPES_KEY = "notification_types";


    /**
     * Default constructor for AppForegroundEvent.
     *
     * @param timeMS The time the app was foregrounded in milliseconds.
     */
    AppForegroundEvent(long timeMS) {
        super(timeMS);
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JSONObject getEventData() {

        JSONObject data = new JSONObject();

        try {
            //connection info
            data.put(CONNECTION_TYPE_KEY, getConnectionType());

            String subtype = getConnectionSubType();
            if (!UAStringUtil.isEmpty(subtype)) {
                data.put(CONNECTION_SUBTYPE_KEY, subtype);
            }

            data.put(CARRIER_KEY, getCarrier());
            data.put(TIME_ZONE_KEY, getTimezone());
            data.put(DAYLIGHT_SAVINGS_KEY, isDaylightSavingsTime());
            data.put(NOTIFICATION_TYPES_KEY, new JSONArray(getNotificationTypes()));
            data.put(OS_VERSION_KEY, Build.VERSION.RELEASE);
            data.put(LIB_VERSION_KEY, UAirship.getVersion());
            data.put(PACKAGE_VERSION_KEY, UAirship.getPackageInfo().versionName);
            data.put(PUSH_ID_KEY, UAirship.shared().getAnalytics().getConversionSendId());
            data.putOpt(METADATA_KEY, UAirship.shared().getAnalytics().getConversionMetadata());
            data.put(LAST_METADATA_KEY, UAirship.shared().getPushManager().getLastReceivedMetadata());
        } catch (JSONException e) {
            Logger.error("AppForegroundEvent - Error constructing JSON data.", e);
        }

        return data;
    }

}
