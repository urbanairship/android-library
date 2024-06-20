/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

class AppBackgroundEvent extends Event {


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
    public final EventType getType() {
        return EventType.APP_BACKGROUND;
    }

    /**
     * @hide
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public JsonMap getEventData(@NonNull ConversionData conversionData) {
        return JsonMap.newBuilder()
                      .put(CONNECTION_TYPE_KEY, getConnectionType())
                      .put(CONNECTION_SUBTYPE_KEY, getConnectionSubType())
                      .put(PUSH_ID_KEY, conversionData.getConversionSendId())
                      .put(METADATA_KEY, conversionData.getConversionMetadata())
                      .build();
    }
}
