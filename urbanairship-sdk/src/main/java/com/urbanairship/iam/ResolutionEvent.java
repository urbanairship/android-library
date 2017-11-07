/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.UAirship;
import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.DateUtils;

/**
 * Resolution event.
 */
class ResolutionEvent extends Event {

    private static final String TYPE = "in_app_resolution";

    // Top level keys
    private static final String ID = "id";
    private static final String RESOLUTION = "resolution";
    private static final String CONVERSION_SEND_ID = "conversion_send_id";
    private static final String CONVERSION_METADATA = "conversion_metadata";

    // Resolution types
    private static final String RESOLUTION_TYPE = "type";
    static final String RESOLUTION_BUTTON_CLICK = "button_click";
    static final String RESOLUTION_REPLACED = "replaced";
    static final String RESOLUTION_MESSAGE_CLICK = "message_click";
    static final String RESOLUTION_DIRECT_OPEN = "direct_open";
    static final String RESOLUTION_EXPIRED = "expired";
    static final String RESOLUTION_USER_DISMISSED = "user_dismissed";
    static final String RESOLUTION_TIMED_OUT = "timed_out";

    // Resolution fields
    private static final String DISPLAY_TIME = "display_time";
    private static final String BUTTON_ID = "button_id";
    private static final String BUTTON_DESCRIPTION = "button_description";
    private static final String REPLACEMENT_ID = "replacement_id";
    private static final String EXPIRY = "expiry";

    private final String id;
    private final JsonMap resolutionData;

    /**
     * Creates a ResolutionEvent.
     *
     * @param id The in-app message ID.
     * @param resolutionData The resolution data.
     */
    private ResolutionEvent(@Nullable String id, @NonNull JsonMap resolutionData) {
        this.id = id;
        this.resolutionData = resolutionData;
    }

    /**
     * Creates a resolution event for when a legacy in-app message is replaced.
     *
     * @param oldId The replaced in-app message.
     * @param newId The new in-app message.
     * @return The ResolutionEvent.
     */
    static ResolutionEvent legacyMessageReplaced(@NonNull String oldId, @NonNull String newId) {
        JsonMap resolutionData = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, RESOLUTION_REPLACED)
                                        .put(REPLACEMENT_ID, newId)
                                        .build();

        return new ResolutionEvent(oldId, resolutionData);
    }

    /**
     * Creates a resolution event for when the Push Notification that delivered the legacy in-app message is
     * opened directly.
     *
     * @param messageId The in-app message ID.
     * @return The ResolutionEvent.
     */
    static ResolutionEvent legacyMessagePushOpened(@NonNull String messageId) {
        JsonMap resolutionData = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, RESOLUTION_DIRECT_OPEN)
                                        .build();
        return new ResolutionEvent(messageId, resolutionData);
    }

    /**
     * Creates a resolution event for when an in-app message expires.
     *
     * @param messageId The in-app message ID.
     * @param expiry The message expiration.
     * @return The ResolutionEvent.
     */
    static ResolutionEvent messageExpired(@NonNull String messageId, long expiry) {
        JsonMap resolutionData = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, RESOLUTION_EXPIRED)
                                        .put(EXPIRY, DateUtils.createIso8601TimeStamp(expiry))
                                        .build();

        return new ResolutionEvent(messageId, resolutionData);
    }

    /**
     * Creates a resolution event from a {@link ResolutionInfo}.
     *
     * @param messageId The in-app message ID.
     * @param resolutionInfo The resolution info.
     * @return The ResolutionEvent.
     */
    static ResolutionEvent messageResolution(@NonNull String messageId, ResolutionInfo resolutionInfo) {
        JsonMap.Builder resolutionDataBuilder = JsonMap.newBuilder()
                                                       .put(RESOLUTION_TYPE, resolutionInfo.type)
                                                       .put(DISPLAY_TIME, millisecondsToSecondsString(resolutionInfo.displayMilliseconds));


        if (RESOLUTION_BUTTON_CLICK.equals(resolutionInfo.type) && resolutionInfo.buttonInfo != null) {
            resolutionDataBuilder.put(BUTTON_ID, resolutionInfo.buttonInfo.getId())
                                 .put(BUTTON_DESCRIPTION, resolutionInfo.buttonInfo.getLabel().getText());
        }

        return new ResolutionEvent(messageId, resolutionDataBuilder.build());
    }


    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JsonMap getEventData() {
        return JsonMap.newBuilder()
                      .put(ID, id)
                      .putOpt(RESOLUTION, resolutionData)
                      .put(CONVERSION_SEND_ID, UAirship.shared().getAnalytics().getConversionSendId())
                      .put(CONVERSION_METADATA, UAirship.shared().getAnalytics().getConversionMetadata())
                      .build();
    }
}
