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

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;

import com.urbanairship.Logger;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * In app notification model object.
 */
public class InAppNotification implements Parcelable, JsonSerializable {

    private static final long DEFAULT_EXPIRY_MS = 2592000000l; // 30 days

    /**
     * Display notification on top of the screen.
     */
    public static final int POSITION_TOP = 1;

    /**
     * Display notification on bottom of the screen.
     */
    public static final int POSITION_BOTTOM = 0;

    private long expiryMS;
    private String id;
    private String alert;
    private JsonMap extras;
    private Long durationMilliseconds;
    private Integer primaryColor;
    private Integer secondaryColor;
    private int position;

    private Map<String, ActionValue> clickActionValues;

    private String buttonGroupId;
    private Map<String, Map<String, ActionValue>> buttonActionValues;


    /**
     * Default constructor
     */
    InAppNotification() {}

    /**
     * Returns the expiration time in milliseconds since Jan. 1, 1970, midnight GMT.
     *
     * @return The expiration time in milliseconds since Jan. 1, 1970, midnight GMT.
     */
    public long getExpiry() {
        return expiryMS;
    }

    /**
     * Tests if the notification is expired or not.
     *
     * @return {@code true} if the notification is expired, otherwise {@code false}.
     */
    public boolean isExpired() {
        return expiryMS > 0 && System.currentTimeMillis() > expiryMS;
    }

    /**
     * Returns the notification's ID
     *
     * @return The notification's ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns extras map.
     *
     * @return The extras map.
     */
    public JsonMap getExtras() {
        return extras;
    }

    /**
     * Returns the notification's alert.
     *
     * @return The notification's alert.
     */
    public String getAlert() {
        return alert;
    }

    /**
     * Returns the on click action name to action value map.
     *
     * @return The on click action values.
     */
    public Map<String, ActionValue> getClickActionValues() {
        return Collections.unmodifiableMap(clickActionValues);
    }

    /**
     * Returns the specified button's action name to action value map.
     *
     * @return The button's action values.
     */
    public Map<String, ActionValue> getButtonActionValues(String buttonId) {
        if (buttonActionValues.containsKey(buttonId)) {
            return Collections.unmodifiableMap(buttonActionValues.get(buttonId));
        } else {
            return null;
        }
    }

    /**
     * Returns the button group ID. The button group can be fetched from {@link com.urbanairship.push.PushManager#getNotificationActionGroup(String)}
     *
     * @return The button group ID.
     */
    public String getButtonGroupId() {
        return buttonGroupId;
    }

    /**
     * Returns the duration in milliseconds for how long the notification should be shown.
     *
     * @return The duration of the notification in milliseconds.
     */
    public Long getDuration() {
        return durationMilliseconds;
    }

    /**
     * Returns the position of the in app notification. Either {@link #POSITION_BOTTOM} or {@link #POSITION_TOP}.
     *
     * @return The notification's position.
     */
    public int getPosition() {
        return position;
    }

    /**
     * Returns the notification's primary color.
     *
     * @return The notification's primary color.
     */
    public Integer getPrimaryColor() {
        return primaryColor;
    }

    /**
     * Returns the notification's secondary color.
     *
     * @return The notification's secondary color.
     */
    public Integer getSecondaryColor() {
        return secondaryColor;
    }

    /**
     * InAppNotification parcel creator.
     */
    public static final Parcelable.Creator<InAppNotification> CREATOR = new Parcelable.Creator<InAppNotification>() {

        @Override
        public InAppNotification createFromParcel(Parcel in) {
            InAppNotification inAppNotification = new InAppNotification();
            inAppNotification.id = in.readString();
            inAppNotification.alert = in.readString();
            inAppNotification.expiryMS = in.readLong();
            inAppNotification.position = in.readInt();

            if (in.readByte() == 1) {
                inAppNotification.durationMilliseconds = in.readLong();
            }
            if (in.readByte() == 1) {
                inAppNotification.primaryColor = in.readInt();
            }
            if (in.readByte() == 1) {
                inAppNotification.secondaryColor = in.readInt();
            }

            try {
                inAppNotification.extras = JsonValue.parseString(in.readString()).getMap();
            } catch (JsonException e) {
                Logger.error("InAppNotification - unable to parse extras from parcel.");
                inAppNotification.extras = new JsonMap(null);
            }

            inAppNotification.buttonGroupId = in.readString();
            inAppNotification.buttonActionValues = new HashMap<>();
            in.readMap(inAppNotification.buttonActionValues, ActionValue.class.getClassLoader());

            inAppNotification.clickActionValues = new HashMap<>();
            in.readMap(inAppNotification.clickActionValues, ActionValue.class.getClassLoader());
            return inAppNotification;
        }

        @Override
        public InAppNotification[] newArray(int size) {
            return new InAppNotification[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(alert);
        dest.writeLong(expiryMS);
        dest.writeInt(position);

        if (durationMilliseconds == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeLong(durationMilliseconds);
        }

        if (primaryColor == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeInt(primaryColor);
        }

        if (secondaryColor == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeInt(secondaryColor);
        }

        dest.writeString(extras.toString());

        dest.writeString(buttonGroupId);
        dest.writeMap(buttonActionValues);

        dest.writeMap(clickActionValues);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Creates an InAppNotification from a JSON payload.
     *
     * @param json The json payload.
     * @return The in app notification, or null if the payload defines an invalid in app notification.
     * @throws JsonException If the JSON payload is unable to parsed.
     */
    public static InAppNotification parseJson(String json) throws JsonException {
        JsonMap inAppJson = JsonValue.parseString(json).getMap();

        if (inAppJson == null) {
            return null;
        }

        JsonMap displayJson = inAppJson.opt("display").getMap();
        JsonMap actionsJson = inAppJson.opt("actions").getMap();

        if (displayJson == null || !"banner".equals(displayJson.opt("type").getString())) {
            Logger.error("InAppNotification - Unable to parse json: " + json);
            return null;
        }

        Builder builder = new Builder();

        builder.setId(inAppJson.opt("id").getString())
               .setExtras(inAppJson.opt("extra").getMap())
               .setAlert(displayJson.opt("alert").getString())
               .setPrimaryColor(parseColor(displayJson.opt("primary_color").getString()))
               .setSecondaryColor(parseColor(displayJson.opt("secondary_color").getString()));

        long duration = displayJson.opt("duration").getLong(0);
        if (duration > 0) {
            builder.setDuration(TimeUnit.SECONDS.toMillis(duration));
        }

        if (inAppJson.containsKey("expiry")) {
            builder.setExpiry(DateUtils.parseIso8601(inAppJson.opt("expiry").getString(), System.currentTimeMillis() + DEFAULT_EXPIRY_MS));
        }

        if ("top".equalsIgnoreCase(displayJson.opt("position").getString())) {
            builder.setPosition(POSITION_TOP);
        } else {
            builder.setPosition(POSITION_BOTTOM);
        }

        if (actionsJson != null) {
            // On click actions
            JsonMap clickActionsJson = actionsJson.opt("on_click").getMap();
            if (clickActionsJson != null) {
                Map<String, ActionValue> clickActions = new HashMap<>();
                for (Map.Entry<String, JsonValue> entry : clickActionsJson.entrySet()) {
                    clickActions.put(entry.getKey(), new ActionValue(entry.getValue()));
                }
                builder.setClickActionValues(clickActions);
            }

            // Button actions
            JsonMap buttonActionsJson = actionsJson.opt("button_actions").getMap();
            if (buttonActionsJson != null) {
                builder.setButtonGroupId(actionsJson.opt("button_group").getString());

                for (Map.Entry<String, JsonValue> entry : buttonActionsJson.entrySet()) {
                    String buttonId = entry.getKey();
                    JsonMap actionJson = buttonActionsJson.opt(buttonId).getMap();

                    Map<String, ActionValue> actions = new HashMap<>();
                    for (Map.Entry<String, JsonValue> buttonEntry : actionJson.entrySet()) {
                        actions.put(buttonEntry.getKey(), new ActionValue(buttonEntry.getValue()));
                    }

                    builder.setButtonActionValues(buttonId, actions);
                }
            }
        }

        return builder.create();
    }

    /**
     * Helper method to parse a color string.
     *
     * @param colorString The color as a #RRGGBB string.
     */
    private static Integer parseColor(String colorString) {
        if (UAStringUtil.isEmpty(colorString)) {
            return null;
        }

        try {
            return Color.parseColor(colorString);
        } catch (IllegalArgumentException e) {
            Logger.warn("InAppNotification - Unable to parse color: " + colorString, e);
            return null;
        }
    }

    @Override
    public JsonValue toJsonValue() {
        // Top Level
        Map<String, Object> inApp = new HashMap<>();
        inApp.put("id", id);
        inApp.put("expiry", DateUtils.createIso8601TimeStamp(expiryMS));

        // Extras
        inApp.put("extra", extras);

        // Display
        Map<String, Object> display = new HashMap<>();
        inApp.put("display", display);
        display.put("type", "banner");
        display.put("alert", alert);
        display.put("position", position == POSITION_TOP ? "top" : "bottom");

        if (durationMilliseconds != null) {
            display.put("duration", TimeUnit.MILLISECONDS.toSeconds(durationMilliseconds));
        }

        if (primaryColor != null) {
            display.put("primary_color", String.format(Locale.US, "#%06X", (0xFFFFFF & primaryColor)));
        }

        if (secondaryColor != null) {
            display.put("secondary_color", String.format(Locale.US, "#%06X", (0xFFFFFF & secondaryColor)));
        }

        // Actions
        Map<String, Object> actions = new HashMap<>();
        inApp.put("actions", actions);
        actions.put("on_click", clickActionValues);
        actions.put("button_group", buttonGroupId);
        actions.put("button_actions", buttonActionValues);

        return JsonValue.wrap(inApp, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof InAppNotification)) {
            return false;
        }

        InAppNotification other = (InAppNotification) o;

        return (id == null ? other.id == null : id.equals(other.id)) &&
                (alert == null ? other.alert == null : alert.equals(other.alert)) &&
                (buttonGroupId == null ? other.buttonGroupId == null : buttonGroupId.equals(other.buttonGroupId)) &&
                (extras == null ? other.extras == null : extras.equals(other.extras)) &&
                (clickActionValues == null ? other.clickActionValues == null : clickActionValues.equals(other.clickActionValues)) &&
                (buttonActionValues == null ? other.buttonActionValues == null : buttonActionValues.equals(other.buttonActionValues)) &&
                (primaryColor == null ? other.primaryColor == null : primaryColor.equals(other.primaryColor)) &&
                (secondaryColor == null ? other.secondaryColor == null : secondaryColor.equals(other.secondaryColor)) &&
                (durationMilliseconds == null ? other.durationMilliseconds == null : durationMilliseconds.equals(other.durationMilliseconds)) &&
                (position == other.position) &&
                (expiryMS == other.expiryMS);
    }

    @Override
    public int hashCode() {
        // Start with a non-zero constant.
        int result = 13;

        // Include a hash for each field.
        result = 31 * result + (id == null ? 0 : id.hashCode());
        result = 31 * result + (alert == null ? 0 : alert.hashCode());
        result = 31 * result + (buttonGroupId == null ? 0 : buttonGroupId.hashCode());
        result = 31 * result + (extras == null ? 0 : extras.hashCode());
        result = 31 * result + (clickActionValues == null ? 0 : clickActionValues.hashCode());
        result = 31 * result + (buttonActionValues == null ? 0 : buttonActionValues.hashCode());
        result = 31 * result + (secondaryColor == null ? 0 : secondaryColor);
        result = 31 * result + (primaryColor == null ? 0 : primaryColor);
        result = 31 * result + (durationMilliseconds == null ? 0 : Long.valueOf(durationMilliseconds).hashCode());
        result = 31 * result + position;
        result = 31 * result + Long.valueOf(expiryMS).hashCode();

        return result;
    }

    /**
     * InAppNotification Builder.
     */
    public static class Builder {

        private Map<String, ActionValue> clickActionValues;
        private Map<String, Map<String, ActionValue>> buttonActionValues = new HashMap<>();
        private JsonMap extras;

        private String buttonGroupId;
        private String alert;
        private String id;
        private Long expiryMS;
        private Long durationMilliseconds;

        private int position = POSITION_BOTTOM;
        private Integer primaryColor;
        private Integer secondaryColor;

        /**
         * Default constructor.
         */
        public Builder() {
        }

        /**
         * Creates a new Builder with the values of the specified notification.
         *
         * @param notification The notification.
         */
        public Builder(InAppNotification notification) {
            this.id = notification.id;
            this.buttonGroupId = notification.buttonGroupId;
            this.alert = notification.alert;
            this.expiryMS = notification.expiryMS;
            this.durationMilliseconds = notification.durationMilliseconds;
            this.position = notification.position;
            this.clickActionValues = new HashMap<>(notification.clickActionValues);
            this.buttonActionValues = new HashMap<>(notification.buttonActionValues);
            this.extras = notification.extras;
            this.primaryColor = notification.primaryColor;
            this.secondaryColor = notification.secondaryColor;
        }

        /**
         * Sets the notification's ID.
         *
         * @param id The notification's ID.
         * @return The builder.
         */
        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the notification's expiry in milliseconds. If no expiry is set, it will default
         * to 30 days from the creation date.
         *
         * @param milliseconds The expiry date in milliseconds.
         * @return The builder.
         */
        public Builder setExpiry(Long milliseconds) {
            this.expiryMS = milliseconds;
            return this;
        }


        /**
         * Sets the notification's extras.
         *
         * @param extras The notification's extras.
         * @return The builder.
         */
        public Builder setExtras(JsonMap extras) {
            this.extras = extras;
            return this;
        }

        /**
         * Sets the notification's on click action values.
         *
         * @param actionValues The notification's on click action values.
         * @return The builder.
         */
        public Builder setClickActionValues(Map<String, ActionValue> actionValues) {
            if (actionValues == null) {
                this.clickActionValues = null;
            } else {
                this.clickActionValues = new HashMap<>(actionValues);
            }

            return this;
        }

        /**
         * Sets the notification's button actions for a given button ID.
         *
         * @param buttonId The button's ID.
         * @param actionValues The button's action values.
         * @return The builder.
         */
        public Builder setButtonActionValues(String buttonId, Map<String, ActionValue> actionValues) {
            if (actionValues == null) {
                buttonActionValues.remove(buttonId);
            } else {
                buttonActionValues.put(buttonId, new HashMap<>(actionValues));
            }
            return this;
        }

        /**
         * Sets the notification's button group ID.
         *
         * @param buttonGroupId The notification's button group ID.
         * @return The builder.
         */
        public Builder setButtonGroupId(String buttonGroupId) {
            this.buttonGroupId = buttonGroupId;
            return this;
        }

        /**
         * Sets the notification's alert.
         *
         * @param alert The notification's alert.
         * @return The builder.
         */
        public Builder setAlert(String alert) {
            this.alert = alert;
            return this;
        }

        /**
         * Sets the duration to show the notification for.
         *
         * @param milliseconds The duration in milliseconds.
         * @return The builder.
         * @throws IllegalArgumentException if the duration is less than or equal to 0.
         */
        public Builder setDuration(Long milliseconds) {
            if (milliseconds != null && milliseconds <= 0) {
                throw new IllegalArgumentException("Duration must be greater than 0 milliseconds");
            }

            this.durationMilliseconds = milliseconds;
            return this;
        }

        /**
         * Sets the notification's position. Either {@link #POSITION_BOTTOM} or {@link #POSITION_TOP}
         * are acceptable values. Any other value will result in an illegal argument exception.
         *
         * @param position The notification's position.
         * @return The builder.
         * @throws IllegalArgumentException If the position is not {@link #POSITION_BOTTOM} nor {@link #POSITION_TOP}.
         */
        public Builder setPosition(int position) {
            if (position != POSITION_TOP && position != POSITION_BOTTOM) {
                throw new IllegalArgumentException("Position must be either InAppNotification.POSITION_BOTTOM or InAppNotification.POSITION_TOP.");
            }

            this.position = position;
            return this;
        }

        /**
         * Sets the notification's primary color.
         *
         * @param color The notification's primary color.
         * @return The builder.
         */
        public Builder setPrimaryColor(Integer color) {
            this.primaryColor = color;
            return this;
        }

        /**
         * Sets the notification's secondary color.
         *
         * @param color The notification's secondary color.
         * @return The builder.
         */
        public Builder setSecondaryColor(Integer color) {
            this.secondaryColor = color;
            return this;
        }

        /**
         * Creates the notification.
         *
         * @return The created in app notification.
         */
        public InAppNotification create() {
            InAppNotification notification = new InAppNotification();

            notification.expiryMS = expiryMS == null ? System.currentTimeMillis() + DEFAULT_EXPIRY_MS : expiryMS;
            notification.id = id;
            notification.extras = extras == null ? new JsonMap(null) : extras;
            notification.alert = alert;
            notification.durationMilliseconds = durationMilliseconds;
            notification.buttonGroupId = buttonGroupId;
            notification.buttonActionValues = buttonActionValues == null ? new HashMap<String, Map<String, ActionValue>>() : buttonActionValues;
            notification.clickActionValues = clickActionValues == null ? new HashMap<String, ActionValue>() : clickActionValues;
            notification.position = position;
            notification.primaryColor = primaryColor;
            notification.secondaryColor = secondaryColor;

            return notification;
        }
    }
}
