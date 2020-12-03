/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

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

    // Resolution fields
    private static final String DISPLAY_TIME = "display_time";
    private static final String BUTTON_ID = "button_id";
    private static final String BUTTON_DESCRIPTION = "button_description";
    private static final String REPLACEMENT_ID = "replacement_id";

    private final JsonMap resolutionInfo;

    private ResolutionEvent(@NonNull String scheduleId, @NonNull String source, @Nullable JsonValue campaigns, @NonNull JsonMap resolutionInfo) {
        super(scheduleId, source, campaigns);
        this.resolutionInfo = resolutionInfo;
    }

    /**
     * Creates a resolution event for when a legacy in-app message is replaced.
     *
     * @param scheduleId The schedule ID.
     * @param newId The new schedule ID.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent newLegacyMessageReplacedEvent(@NonNull String scheduleId, @NonNull String newId) {
        JsonMap resolutionInfo = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, LEGACY_MESSAGE_REPLACED)
                                        .put(REPLACEMENT_ID, newId)
                                        .build();

        return new ResolutionEvent(scheduleId, InAppMessage.SOURCE_LEGACY_PUSH, null, resolutionInfo);
    }

    /**
     * Creates a resolution event for when the Push Notification that delivered the legacy in-app message is
     * opened directly.
     *
     * @param scheduleId The in-app message ID.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent newLegacyMessagePushOpenedEvent(@NonNull String scheduleId) {
        JsonMap resolutionInfo = JsonMap.newBuilder()
                                        .put(RESOLUTION_TYPE, LEGACY_MESSAGE_DIRECT_OPEN)
                                        .build();

        return new ResolutionEvent(scheduleId, InAppMessage.SOURCE_LEGACY_PUSH, null, resolutionInfo);
    }

    /**
     * Creates a resolution event from a {@link ResolutionInfo}.
     *
     * @param scheduleId The schedule ID.
     * @param source The message source.
     * @param resolutionInfo The resolution info.
     * @param displayMilliseconds Display time in milliseconds.
     * @param campaigns The campaign info.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent newEvent(@NonNull String scheduleId,
                                           @NonNull @InAppMessage.Source String source,
                                           @NonNull ResolutionInfo resolutionInfo,
                                           long displayMilliseconds,
                                           @Nullable JsonValue campaigns) {

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

        return new ResolutionEvent(scheduleId, source, campaigns, resolutionDataBuilder.build());
    }

    @NonNull
    @Override
    protected JsonMap.Builder extendEventDataBuilder(@NonNull JsonMap.Builder builder) {
        return builder.put(RESOLUTION, resolutionInfo);
    }

    @NonNull
    @Override
    public String getType() {
        return TYPE;
    }

}
