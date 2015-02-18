/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.push.ian;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Event;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.util.DateUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * An event that is sent when a {@link com.urbanairship.push.ian.InAppNotification} is finished.
 *
 * @hide
 */
public class ResolutionEvent extends Event {

    private static final String TYPE = "ian_resolution";


    // Top level keys
    private static final String ID = "id";
    private static final String RESOLUTION = "resolution";
    private static final String CONVERSION_SEND_ID = "conversion_send_id";

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
     * @param id The InAppNotification ID.
     * @param resolutionData The resolution data.
     */
    private ResolutionEvent(String id, Map<String, Object> resolutionData) {
        this.id = id;
        this.resolutionData = resolutionData;
    }

    /**
     * Creates a resolution event for when an InAppNotification interactive button is clicked.
     *
     * @param context The application context.
     * @param notification The InAppNotification.
     * @param button The button that was clicked.
     * @param displayMs How long the InAppNotification was displayed in milliseconds.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createButtonClickedResolutionEvent(Context context, InAppNotification notification, NotificationActionButton button, long displayMs) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_BUTTON_CLICK);
        resolutionData.put(BUTTON_ID, button.getId());
        resolutionData.put(BUTTON_GROUP, notification.getButtonGroupId());
        resolutionData.put(DISPLAY_TIME, millisecondsToSecondsString(displayMs));

        if (button.getDescription() != null) {
            resolutionData.put(BUTTON_DESCRIPTION, button.getDescription());
        } else if (button.getLabel() > 0) {
            resolutionData.put(BUTTON_DESCRIPTION, context.getString(button.getLabel()));
        }

        return new ResolutionEvent(notification.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an InAppNotification is clicked.
     *
     * @param notification The InAppNotification.
     * @param displayMs How long the InAppNotification was displayed in milliseconds.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createClickedResolutionEvent(InAppNotification notification, long displayMs) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_MESSAGE_CLICK);
        resolutionData.put(DISPLAY_TIME, millisecondsToSecondsString(displayMs));

        return new ResolutionEvent(notification.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an InAppNotification is replaced.
     *
     * @param replaced The replaced InAppNotification.
     * @param replacement The new InAppNotification.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createReplacedResolutionEvent(InAppNotification replaced, InAppNotification replacement) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_REPLACED);
        resolutionData.put(REPLACEMENT_ID, replacement.getId());
        return new ResolutionEvent(replaced.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when the Push Notification that delivered InAppNotification is
     * opened directly.
     *
     * @param notification The InAppNotification.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createDirectOpenResolutionEvent(InAppNotification notification) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_DIRECT_OPEN);
        return new ResolutionEvent(notification.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an InAppNotification expires.
     *
     * @param notification The InAppNotification.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createExpiredResolutionEvent(InAppNotification notification) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_EXPIRED);
        resolutionData.put(EXPIRY, DateUtils.createIso8601TimeStamp(notification.getExpiry()));

        return new ResolutionEvent(notification.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an InAppNotification is dismissed by the end user.
     *
     * @param notification The InAppNotification.
     * @param displayMs How long the InAppNotification was displayed in milliseconds.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createUserDismissedResolutionEvent(InAppNotification notification, long displayMs) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_USER_DISMISSED);
        resolutionData.put(DISPLAY_TIME, millisecondsToSecondsString(displayMs));

        return new ResolutionEvent(notification.getId(), resolutionData);
    }

    /**
     * Creates a resolution event for when an InAppNotification displays for the full duration and times out.
     *
     * @param notification The InAppNotification.
     * @param displayMs How long the InAppNotification was displayed in milliseconds.
     * @return The ResolutionEvent.
     */
    public static ResolutionEvent createTimedOutResolutionEvent(InAppNotification notification, long displayMs) {
        Map<String, Object> resolutionData = new HashMap<>();
        resolutionData.put(RESOLUTION_TYPE, RESOLUTION_TIMED_OUT);
        resolutionData.put(DISPLAY_TIME, millisecondsToSecondsString(displayMs));

        return new ResolutionEvent(notification.getId(), resolutionData);
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JSONObject getEventData() {
        JSONObject data = new JSONObject();

        try {
            data.putOpt(ID, id);
            data.putOpt(RESOLUTION, new JSONObject(resolutionData));
            data.putOpt(CONVERSION_SEND_ID, UAirship.shared().getAnalytics().getConversionSendId());
        } catch (JSONException e) {
            Logger.error("InAppNotificationDisplayEvent - Error constructing JSON data.", e);
        }

        return data;
    }
}
