/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import androidx.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;

class AppBackgroundEvent extends Event {

    static final String TYPE = "app_background";

    /**
     * Default constructor for AppBackgroundEvent.
     *
     * @param timeMS The time the app was backgrounded in milliseconds.
     */
    AppBackgroundEvent(long timeMS) {
        super(timeMS);
    }

    @Override
    @NonNull
    public final String getType() {
        return TYPE;
    }

    @Override
    @NonNull
    protected final JsonMap getEventData() {
        return JsonMap.newBuilder()
                      .put(CONNECTION_TYPE_KEY, getConnectionType())
                      .put(CONNECTION_SUBTYPE_KEY, getConnectionSubType())
                      .put(PUSH_ID_KEY, UAirship.shared().getAnalytics().getConversionSendId())
                      .put(METADATA_KEY, UAirship.shared().getAnalytics().getConversionMetadata())
                      .build();
    }

}
