/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;


import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.UAirship;
import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

/**
 * Base event class for in-app messages.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class InAppMessageEvent extends Event {
    private static final String ID = "id";
    private static final String CONVERSION_SEND_ID = "conversion_send_id";
    private static final String CONVERSION_METADATA = "conversion_metadata";
    private static final String SOURCE = "source";

    // ID keys
    private static final String MESSAGE_ID = "message_id";
    private static final String CAMPAIGNS = "campaigns";


    private static final String SOURCE_URBAN_AIRSHIP = "urban-airship";
    private static final String SOURCE_APP_DEFINED = "app-defined";

    private final JsonValue eventId;
    private final String source;


    InAppMessageEvent(InAppMessage message) {
        this(createEventId(message), message.getSource());
    }

    InAppMessageEvent(@NonNull JsonValue eventId, @NonNull @InAppMessage.Source String source) {
        this.eventId = eventId;
        this.source = source;
    }

    @Override
    protected JsonMap getEventData() {
        boolean isAppDefined = InAppMessage.SOURCE_APP_DEFINED.equals(source);

        return JsonMap.newBuilder()
                      .put(ID, eventId)
                      .put(SOURCE, isAppDefined ? SOURCE_APP_DEFINED : SOURCE_URBAN_AIRSHIP)
                      .put(CONVERSION_SEND_ID, UAirship.shared().getAnalytics().getConversionSendId())
                      .put(CONVERSION_METADATA, UAirship.shared().getAnalytics().getConversionMetadata())
                      .build();
    }

    @Override
    public boolean isValid() {
        return !eventId.isNull();
    }

    static JsonValue createEventId(InAppMessage message) {
        switch (message.getSource()) {
            case InAppMessage.SOURCE_LEGACY_PUSH:
                return JsonValue.wrap(message.getId());

            case InAppMessage.SOURCE_REMOTE_DATA:
                return JsonMap.newBuilder()
                              .put(MESSAGE_ID, message.getId())
                              .put(CAMPAIGNS, message.getCampaigns())
                              .build()
                              .toJsonValue();

            case InAppMessage.SOURCE_APP_DEFINED:
                return JsonMap.newBuilder()
                              .put(MESSAGE_ID, message.getId())
                              .build()
                              .toJsonValue();
        }

        return JsonValue.NULL;
    }
}
