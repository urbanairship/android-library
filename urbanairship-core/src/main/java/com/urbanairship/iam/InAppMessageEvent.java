/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

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

    private static final String LOCALE = "locale";

    private final JsonValue eventId;
    private final String source;

    @Nullable private final InAppMessage message;

    InAppMessageEvent(@NonNull InAppMessage message) {
        this.eventId = createEventId(message);
        this.source = message.getSource();
        this.message = message;
    }

    // Used for legacy in-app messages
    InAppMessageEvent(@NonNull JsonValue eventId, @NonNull @InAppMessage.Source String source) {
        this.eventId = eventId;
        this.source = source;
        this.message = null;
    }

    @NonNull
    @Override
    protected JsonMap getEventData() {
        boolean isAppDefined = InAppMessage.SOURCE_APP_DEFINED.equals(source);

        return JsonMap.newBuilder()
                      .put(ID, eventId)
                      .put(SOURCE, isAppDefined ? SOURCE_APP_DEFINED : SOURCE_URBAN_AIRSHIP)
                      .putOpt(CONVERSION_SEND_ID, UAirship.shared().getAnalytics().getConversionSendId())
                      .putOpt(CONVERSION_METADATA, UAirship.shared().getAnalytics().getConversionMetadata())
                      .putOpt(LOCALE, message != null ? message.getRenderedLocale() : null)
                      .build();
    }

    @Override
    public boolean isValid() {
        return !eventId.isNull();
    }

    @NonNull
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
