/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.UAirship;
import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.util.DateUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * An event that is sent when a {@link InAppMessage} is finished.
 *
 * @hide
 */
public class ResolutionEvent extends Event {

    private static final String TYPE = "in_app_resolution";


    // Top level keys
    private static final String ID = "id";
    private static final String RESOLUTION = "resolution";
    private static final String CONVERSION_SEND_ID = "conversion_send_id";
    private static final String CONVERSION_METADATA = "conversion_metadata";

    // Resolution types
    private static final String RESOLUTION_TYPE = "type";
    private static final String RESOLUTION_BUTTON_CLICK = "button_click";
    private static final String RESOLUTION_REPLACED = "replaced";
    private static final String RESOLUTION_MESSAGE_CLICK = "message_click";
    private static final String RESOLUTION_DIRECT_OPEN = "direct_open";
    private static final String RESOLUTION_EXPIRED = "expired";
    private static final String RESOLUTION_USER_DISMISSED = "user_dismissed";
    private static final String RESOLUTION_TIMED_OUT = "timed_out";

    // Resolution fields
    private static final String DISPLAY_TIME = "display_time";
    private static final String BUTTON_ID = "button_id";
    private static final String BUTTON_GROUP = "button_group";
    private static final String BUTTON_DESCRIPTION = "button_description";
    private static final String REPLACEMENT_ID = "replacement_id";
    private static final String EXPIRY = "expiry";

    private final String id;
    private final Map<String, Object> resolutionData;

    /**
     * Creates a ResolutionEvent.
     *
     * @param id The in-app message ID.
     * @param resolutionData The resolution data.
     */
    private ResolutionEvent(@Nullable String id, @NonNull Map<String, Object> resolutionData) {
        this.id = id;
        this.resolutionData = resolutionData;
    }

    /**
     * Creates a resolution event for when an in-app message interactive button is clicked.
     *
     * @param context The application context.
     * @param message The in-app message.
     * @param button The button that was clicked.
     * @param displayMs How long the in-app message was displayed in milliseconds.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createButtonClickedResolutionEvent(@NonNull Context context, @NonNull InAppMessage message, @NonNull NotificationActionButton button, long displayMs) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_BUTTON_CLICK);
        resolutionData.put(BUTTON_ID, button.getId());
        resolutionData.put(BUTTON_GROUP, message.getButtonGroupId());
        resolutionData.put(DISPLAY_TIME, millisecondsToSecondsString(displayMs));

        if (button.getDescription() != null) {
            resolutionData.put(BUTTON_DESCRIPTION, button.getDescription());
        } else if (button.getLabel() > 0) {
            resolutionData.put(BUTTON_DESCRIPTION, context.getString(button.getLabel()));
        }

        return new ResolutionEvent(message.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an in-app message is clicked.
     *
     * @param message The in-app message.
     * @param displayMs How long the in-app message was displayed in milliseconds.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createClickedResolutionEvent(@NonNull InAppMessage message, long displayMs) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_MESSAGE_CLICK);
        resolutionData.put(DISPLAY_TIME, millisecondsToSecondsString(displayMs));

        return new ResolutionEvent(message.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an in-app message is replaced.
     *
     * @param replaced The replaced in-app message.
     * @param replacement The new in-app message.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createReplacedResolutionEvent(@NonNull InAppMessage replaced, @NonNull InAppMessage replacement) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_REPLACED);
        resolutionData.put(REPLACEMENT_ID, replacement.getId());
        return new ResolutionEvent(replaced.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when the Push Notification that delivered in-app message is
     * opened directly.
     *
     * @param message The in-app message.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createDirectOpenResolutionEvent(@NonNull InAppMessage message) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_DIRECT_OPEN);
        return new ResolutionEvent(message.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an in-app message expires.
     *
     * @param message The in-app message.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createExpiredResolutionEvent(@NonNull InAppMessage message) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_EXPIRED);
        resolutionData.put(EXPIRY, DateUtils.createIso8601TimeStamp(message.getExpiry()));

        return new ResolutionEvent(message.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an in-app message is dismissed by the end user.
     *
     * @param message The in-app message.
     * @param displayMs How long the in-app message was displayed in milliseconds.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createUserDismissedResolutionEvent(@NonNull InAppMessage message, long displayMs) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_USER_DISMISSED);
        resolutionData.put(DISPLAY_TIME, millisecondsToSecondsString(displayMs));

        return new ResolutionEvent(message.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an in-app message displays for the full duration and times out.
     *
     * @param message The in-app message.
     * @param displayMs How long the in-app message was displayed in milliseconds.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createTimedOutResolutionEvent(@NonNull InAppMessage message, long displayMs) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_TIMED_OUT);
        resolutionData.put(DISPLAY_TIME, millisecondsToSecondsString(displayMs));

        return new ResolutionEvent(message.getId(), resolutionData);
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
