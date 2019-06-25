/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

/**
 * Resolution event.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ResolutionEvent extends InAppMessageEvent {

    /**
     * Max button description length.
     */
    private static final int MAX_BUTTON_DESCRIPTION_LENGTH = 30;

    private static final String TYPE = "in_app_resolution";

    private static final String RESOLUTION = "resolution";

    // Resolution types
    private static final String RESOLUTION_TYPE = "type";

    private static final String LEGACY_MESSAGE_REPLACED = "replaced";
    private static final String LEGACY_MESSAGE_DIRECT_OPEN = "direct_open";
    private static final String MESSAGE_EXPIRED = "expired";

    // Resolution fields
    private static final String DISPLAY_TIME = "display_time";
    private static final String BUTTON_ID = "button_id";
    private static final String BUTTON_DESCRIPTION = "button_description";
    private static final String REPLACEMENT_ID = "replacement_id";
    private static final String EXPIRY = "expiry";

    private final JsonMap resolutionData;

    ResolutionEvent(@NonNull InAppMessage message, @NonNull JsonMap resolutionData) {
        super(message);
        this.resolutionData = resolutionData;
    }

    ResolutionEvent(@NonNull JsonValue eventId, @NonNull String source, @NonNull JsonMap resolutionData) {
        super(eventId, source);
        this.resolutionData = resolutionData;
    }

    /**
     * Creates a resolution event for when a legacy in-app message is replaced.
     *
     * @param messageId The message ID.
     * @param newId The new in-app message ID.
     * @return The ResolutionEvent.
     */
    static ResolutionEvent legacyMessageReplaced(@NonNull String messageId, @NonNull String newId) {
        JsonMap resolutionData = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, LEGACY_MESSAGE_REPLACED)
                                        .put(REPLACEMENT_ID, newId)
                                        .build();

        return new ResolutionEvent(JsonValue.wrap(messageId), InAppMessage.SOURCE_LEGACY_PUSH, resolutionData);
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
                                        .put(RESOLUTION_TYPE, LEGACY_MESSAGE_DIRECT_OPEN)
                                        .build();

        return new ResolutionEvent(JsonValue.wrap(messageId), InAppMessage.SOURCE_LEGACY_PUSH, resolutionData);
    }

    /**
     * Creates a resolution event for when an in-app message expires.
     *
     * @param message The in-app message.
     * @param expiry The message expiration.
     * @return The ResolutionEvent.
     */
    static ResolutionEvent messageExpired(@NonNull InAppMessage message, long expiry) {
        JsonMap resolutionData = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, MESSAGE_EXPIRED)
                                        .put(EXPIRY, DateUtils.createIso8601TimeStamp(expiry))
                                        .build();

        return new ResolutionEvent(message, resolutionData);
    }

    /**
     * Creates a resolution event from a {@link ResolutionInfo}.
     *
     * @param message The in-app message ].
     * @param resolutionInfo The resolution info.
     * @return The ResolutionEvent.
     */
    static ResolutionEvent messageResolution(@NonNull InAppMessage message, ResolutionInfo resolutionInfo, long displayMilliseconds) {
        displayMilliseconds = displayMilliseconds > 0 ? displayMilliseconds : 0;

        JsonMap.Builder resolutionDataBuilder = JsonMap.newBuilder()
                                                       .put(RESOLUTION_TYPE, resolutionInfo.getType())
                                                       .put(DISPLAY_TIME, millisecondsToSecondsString(displayMilliseconds));

        if (ResolutionInfo.RESOLUTION_BUTTON_CLICK.equals(resolutionInfo.getType()) && resolutionInfo.getButtonInfo() != null) {
            String description = resolutionInfo.getButtonInfo().getLabel().getText();
            if (description != null && description.length() > MAX_BUTTON_DESCRIPTION_LENGTH) {
                description = description.substring(0, MAX_BUTTON_DESCRIPTION_LENGTH);
            }
            resolutionDataBuilder.put(BUTTON_ID, resolutionInfo.getButtonInfo().getId())
                                 .put(BUTTON_DESCRIPTION, description);
        }

        return new ResolutionEvent(message, resolutionDataBuilder.build());
    }

    @NonNull
    @Override
    public final String getType() {
        return TYPE;
    }

    @NonNull
    @Override
    protected JsonMap getEventData() {
        return JsonMap.newBuilder()
                      .putAll(super.getEventData())
                      .put(RESOLUTION, resolutionData)
                      .build();
    }

}
