/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.UAirship;
import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

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

    private final String scheduleId;
    private final String source;
    private final JsonValue campaigns;

    protected InAppMessageEvent(@NonNull String scheduleId, @NonNull @InAppMessage.Source String source, @Nullable JsonValue campaigns) {
        this.scheduleId = scheduleId;
        this.source = source;
        this.campaigns = campaigns;
    }

    @NonNull
    @Override
    public JsonMap getEventData() {
        boolean isAppDefined = InAppMessage.SOURCE_APP_DEFINED.equals(source);
        JsonMap.Builder builder = JsonMap.newBuilder()
                      .put(ID, createEventId(scheduleId, source, campaigns))
                      .put(SOURCE, isAppDefined ? SOURCE_APP_DEFINED : SOURCE_URBAN_AIRSHIP)
                      .putOpt(CONVERSION_SEND_ID, UAirship.shared().getAnalytics().getConversionSendId())
                      .putOpt(CONVERSION_METADATA, UAirship.shared().getAnalytics().getConversionMetadata());

        return extendEventDataBuilder(builder).build();
    }

    @NonNull
    protected abstract JsonMap.Builder extendEventDataBuilder(@NonNull JsonMap.Builder builder);

    @NonNull
    private static JsonValue createEventId(@NonNull String scheduleId, @NonNull @InAppMessage.Source String source, @Nullable JsonValue campaigns) {
        switch (source) {
            case InAppMessage.SOURCE_LEGACY_PUSH:
                return JsonValue.wrap(scheduleId);

            case InAppMessage.SOURCE_REMOTE_DATA:
                return JsonMap.newBuilder()
                              .put(MESSAGE_ID, scheduleId)
                              .put(CAMPAIGNS, campaigns)
                              .build()
                              .toJsonValue();

            case InAppMessage.SOURCE_APP_DEFINED:
                return JsonMap.newBuilder()
                              .put(MESSAGE_ID, scheduleId)
                              .build()
                              .toJsonValue();
        }

        return JsonValue.NULL;
    }

}
