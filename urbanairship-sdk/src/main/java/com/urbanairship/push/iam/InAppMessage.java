/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * In-app message model object.
 */
public class InAppMessage implements Parcelable, JsonSerializable {

    private static final long DEFAULT_EXPIRY_MS = 2592000000L; // 30 days

    @IntDef({POSITION_TOP, POSITION_BOTTOM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Position {}

    /**
     * Display the message on top of the screen.
     */
    public static final int POSITION_TOP = 1;

    /**
     * Display the message on bottom of the screen.
     */
    public static final int POSITION_BOTTOM = 0;

    private final long expiryMS;
    private final String id;
    private final String alert;
    private final Long durationMilliseconds;
    private final Integer primaryColor;
    private final Integer secondaryColor;
    private final int position;

    private final String buttonGroupId;

    @NonNull
    private final Map<String, ActionValue> clickActionValues;

    @NonNull
    private final JsonMap extras;

    @NonNull
    private final Map<String, Map<String, ActionValue>> buttonActionValues;

    /**
     * Creates a InAppMessage from a {@link com.urbanairship.push.iam.InAppMessage.Builder}.
     * @param builder An InAppMessage builder.
     */
    private InAppMessage(Builder builder) {
        this.expiryMS = builder.expiryMS == null ? System.currentTimeMillis() + DEFAULT_EXPIRY_MS : builder.expiryMS;
        this.id = builder.id;
        this.extras = builder.extras == null ? new JsonMap(null) : builder.extras;
        this.alert = builder.alert;
        this.durationMilliseconds = builder.durationMilliseconds;
        this.buttonGroupId = builder.buttonGroupId;
        this.buttonActionValues = builder.buttonActionValues;
        this.clickActionValues = builder.clickActionValues == null ? new HashMap<String, ActionValue>() : builder.clickActionValues;
        this.position = builder.position;
        this.primaryColor = builder.primaryColor;
        this.secondaryColor = builder.secondaryColor;
    }

    /**
     * Creates a InAppMessage from a parcel created by {@link com.urbanairship.push.iam.InAppMessage#CREATOR}.
     * @param parcel The parcel.
     */
    private InAppMessage(Parcel parcel) {
        this.id = parcel.readString();
        this.alert = parcel.readString();
        this.expiryMS = parcel.readLong();
        this.position = parcel.readInt();

        this.durationMilliseconds = parcel.readByte() == 1 ? parcel.readLong() : null;
        this.primaryColor = parcel.readByte() == 1 ? parcel.readInt() : null;
        this.secondaryColor = parcel.readByte() == 1 ? parcel.readInt() : null;

        JsonMap extras = null;
        try {
            extras = JsonValue.parseString(parcel.readString()).getMap();
        } catch (JsonException e) {
            Logger.error("InAppMessage - unable to parse extras from parcel.");
        }

        this.extras = extras == null ? new JsonMap(null) : extras;
        this.buttonGroupId = parcel.readString();
        this.buttonActionValues = new HashMap<>();
        parcel.readMap(this.buttonActionValues, ActionValue.class.getClassLoader());

        this.clickActionValues = new HashMap<>();
        parcel.readMap(this.clickActionValues, ActionValue.class.getClassLoader());
    }

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
     * Returns the expiration time in milliseconds since Jan. 1, 1970, midnight GMT.
     *
     * @return The expiration time in milliseconds since Jan. 1, 1970, midnight GMT.
     */
    public long getExpiry() {
        return expiryMS;
    }

    /**
     * Tests if the message is expired or not.
     *
     * @return {@code true} if the message is expired, otherwise {@code false}.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryMS;
    }

    /**
     * Returns the message's ID
     *
     * @return The message's ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns extras map.
     *
     * @return The extras map.
     */
    @NonNull
    public JsonMap getExtras() {
        return extras;
    }

    /**
     * Returns the message's alert.
     *
     * @return The message's alert.
     */
    public String getAlert() {
        return alert;
    }

    /**
     * Returns the on click action name to action value map.
     *
     * @return The on click action values.
     */
    @NonNull
    public Map<String, ActionValue> getClickActionValues() {
        return Collections.unmodifiableMap(clickActionValues);
    }

    /**
     * Returns the specified button's action name to action value map.
     *
     * @return The button's action values.
     */
    @Nullable
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
     * Returns the duration in milliseconds for how long the message should be shown.
     *
     * @return The duration of the message in milliseconds.
     */
    public Long getDuration() {
        return durationMilliseconds;
    }

    /**
     * Returns the position of the in-app message. Either {@link #POSITION_BOTTOM} or {@link #POSITION_TOP}.
     *
     * @return The message's position.
     */
    @Position
    public int getPosition() {
        return position;
    }

    /**
     * Returns the message's primary color.
     *
     * @return The message's primary color.
     */
    public Integer getPrimaryColor() {
        return primaryColor;
    }

    /**
     * Returns the message's secondary color.
     *
     * @return The message's secondary color.
     */
    public Integer getSecondaryColor() {
        return secondaryColor;
    }

    /**
     * Creates an in-app message from a JSON payload.
     *
     * @param json The json payload.
     * @return The in-app message, or null if the payload defines an invalid in-app message.
     * @throws JsonException If the JSON payload is unable to parsed.
     */
    @Nullable
    public static InAppMessage parseJson(String json) throws JsonException {
        JsonMap inAppJson = JsonValue.parseString(json).getMap();

        if (inAppJson == null) {
            return null;
        }

        JsonMap displayJson = inAppJson.opt("display").getMap();
        JsonMap actionsJson = inAppJson.opt("actions").getMap();

        if (displayJson == null || !"banner".equals(displayJson.opt("type").getString())) {
            Logger.error("InAppMessage - Unable to parse json: " + json);
            return null;
        }

        Builder builder = new Builder();

        builder.setId(inAppJson.opt("id").getString())
               .setExtras(inAppJson.opt("extra").getMap())
               .setAlert(displayJson.opt("alert").getString())
               .setPrimaryColor(parseColor(displayJson.opt("primary_color").getString()))
               .setSecondaryColor(parseColor(displayJson.opt("secondary_color").getString()));

        long duration;
        if (displayJson.containsKey("duration_ms")) {
            duration = displayJson.get("duration_ms").getLong(0);
            if (duration > 0) {
                builder.setDuration(duration);
            }
        } else {
            duration = displayJson.opt("duration").getLong(0);
            if (duration > 0) {
                builder.setDuration(TimeUnit.SECONDS.toMillis(duration));
            }
        }

        if (inAppJson.containsKey("expiry_ms")) {
            builder.setExpiry(inAppJson.get("expiry_ms").getLong(System.currentTimeMillis() + DEFAULT_EXPIRY_MS));
        } else  if (inAppJson.containsKey("expiry")) {
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
                for (Map.Entry<String, JsonValue> entry : clickActionsJson) {
                    clickActions.put(entry.getKey(), new ActionValue(entry.getValue()));
                }
                builder.setClickActionValues(clickActions);
            }

            // Button group
            builder.setButtonGroupId(actionsJson.opt("button_group").getString());

            // Button actions
            JsonMap buttonActionsJson = actionsJson.opt("button_actions").getMap();
            if (buttonActionsJson != null) {

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
            Logger.warn("InAppMessage - Unable to parse color: " + colorString, e);
            return null;
        }
    }

    @Override
    public JsonValue toJsonValue() {
        // Top Level
        Map<String, Object> inApp = new HashMap<>();
        inApp.put("id", id);
        inApp.put("expiry_ms", expiryMS);

        // Extras
        inApp.put("extra", extras);

        // Display
        Map<String, Object> display = new HashMap<>();
        inApp.put("display", display);
        display.put("type", "banner");
        display.put("alert", alert);
        display.put("position", position == POSITION_TOP ? "top" : "bottom");

        if (durationMilliseconds != null) {
            display.put("duration_ms", durationMilliseconds);
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

        return JsonValue.wrapOpt(inApp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InAppMessage that = (InAppMessage) o;

        if (expiryMS != that.expiryMS) {
            return false;
        }
        if (position != that.position) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (alert != null ? !alert.equals(that.alert) : that.alert != null) {
            return false;
        }
        if (durationMilliseconds != null ? !durationMilliseconds.equals(that.durationMilliseconds) : that.durationMilliseconds != null) {
            return false;
        }
        if (primaryColor != null ? !primaryColor.equals(that.primaryColor) : that.primaryColor != null) {
            return false;
        }
        if (secondaryColor != null ? !secondaryColor.equals(that.secondaryColor) : that.secondaryColor != null) {
            return false;
        }
        if (buttonGroupId != null ? !buttonGroupId.equals(that.buttonGroupId) : that.buttonGroupId != null) {
            return false;
        }
        if (!clickActionValues.equals(that.clickActionValues)) {
            return false;
        }
        if (!extras.equals(that.extras)) {
            return false;
        }
        return buttonActionValues.equals(that.buttonActionValues);

    }

    @Override
    public int hashCode() {
        int result = (int) (expiryMS ^ (expiryMS >>> 32));
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (alert != null ? alert.hashCode() : 0);
        result = 31 * result + (durationMilliseconds != null ? durationMilliseconds.hashCode() : 0);
        result = 31 * result + (primaryColor != null ? primaryColor.hashCode() : 0);
        result = 31 * result + (secondaryColor != null ? secondaryColor.hashCode() : 0);
        result = 31 * result + position;
        result = 31 * result + (buttonGroupId != null ? buttonGroupId.hashCode() : 0);
        result = 31 * result + clickActionValues.hashCode();
        result = 31 * result + extras.hashCode();
        result = 31 * result + buttonActionValues.hashCode();
        return result;
    }

    /**
     * InAppMessage parcel creator.
     */
    public static final Parcelable.Creator<InAppMessage> CREATOR = new Parcelable.Creator<InAppMessage>() {

        @Override
        public InAppMessage createFromParcel(Parcel in) {
            return new InAppMessage(in);
        }

        @Override
        public InAppMessage[] newArray(int size) {
            return new InAppMessage[size];
        }
    };

    /**
     * InAppMessage Builder.
     */
    public static class Builder {

        @Nullable
        private Map<String, ActionValue> clickActionValues;

        @Nullable
        private JsonMap extras;

        @NonNull
        private Map<String, Map<String, ActionValue>> buttonActionValues = new HashMap<>();

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
         * Creates a new Builder with the values of the specified message.
         *
         * @param message The message.
         */
        public Builder(InAppMessage message) {
            this.id = message.id;
            this.buttonGroupId = message.buttonGroupId;
            this.alert = message.alert;
            this.expiryMS = message.expiryMS;
            this.durationMilliseconds = message.durationMilliseconds;
            this.position = message.position;
            this.clickActionValues = new HashMap<>(message.clickActionValues);
            this.buttonActionValues = new HashMap<>(message.buttonActionValues);
            this.extras = message.extras;
            this.primaryColor = message.primaryColor;
            this.secondaryColor = message.secondaryColor;
        }

        /**
         * Sets the message's ID.
         *
         * @param id The message's ID.
         * @return The builder.
         * @hide
         */
        @NonNull
        public Builder setId(@Nullable String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the message's expiry in milliseconds. If no expiry is set, it will default
         * to 30 days from the creation date.
         *
         * @param milliseconds The expiry date in milliseconds.
         * @return The builder.
         */
        @NonNull
        public Builder setExpiry(@Nullable Long milliseconds) {
            this.expiryMS = milliseconds;
            return this;
        }


        /**
         * Sets the message's extras.
         *
         * @param extras The message's extras.
         * @return The builder.
         */
        @NonNull
        public Builder setExtras(@Nullable JsonMap extras) {
            this.extras = extras;
            return this;
        }

        /**
         * Sets the message's on click action values.
         *
         * @param actionValues The message's on click action values.
         * @return The builder.
         */
        @NonNull
        public Builder setClickActionValues(@Nullable Map<String, ActionValue> actionValues) {
            if (actionValues == null) {
                this.clickActionValues = null;
            } else {
                this.clickActionValues = new HashMap<>(actionValues);
            }

            return this;
        }

        /**
         * Sets the message's button actions for a given button ID.
         *
         * @param buttonId The button's ID.
         * @param actionValues The button's action values.
         * @return The builder.
         */
        @NonNull
        public Builder setButtonActionValues(@NonNull String buttonId, @Nullable Map<String, ActionValue> actionValues) {
            if (actionValues == null) {
                buttonActionValues.remove(buttonId);
            } else {
                buttonActionValues.put(buttonId, new HashMap<>(actionValues));
            }
            return this;
        }

        /**
         * Sets the message's button group ID.
         *
         * @param buttonGroupId The message's button group ID.
         * @return The builder.
         */
        @NonNull
        public Builder setButtonGroupId(@Nullable String buttonGroupId) {
            this.buttonGroupId = buttonGroupId;
            return this;
        }

        /**
         * Sets the message's alert.
         *
         * @param alert The message's alert.
         * @return The builder.
         */
        @NonNull
        public Builder setAlert(@Nullable String alert) {
            this.alert = alert;
            return this;
        }

        /**
         * Sets the duration to show the message for.
         *
         * @param milliseconds The duration in milliseconds.
         * @return The builder.
         * @throws IllegalArgumentException if the duration is less than or equal to 0.
         */
        @NonNull
        public Builder setDuration(@Nullable Long milliseconds) {
            if (milliseconds != null && milliseconds <= 0) {
                throw new IllegalArgumentException("Duration must be greater than 0 milliseconds");
            }

            this.durationMilliseconds = milliseconds;
            return this;
        }

        /**
         * Sets the message's position. Either {@link #POSITION_BOTTOM} or {@link #POSITION_TOP}
         * are acceptable values. Any other value will result in an illegal argument exception.
         *
         * @param position The message's position.
         * @return The builder.
         * @throws IllegalArgumentException If the position is not {@link #POSITION_BOTTOM} nor {@link #POSITION_TOP}.
         */
        @NonNull
        public Builder setPosition(@Position int position) {
            if (position != POSITION_TOP && position != POSITION_BOTTOM) {
                throw new IllegalArgumentException("Position must be either InAppMessage.POSITION_BOTTOM or InAppMessage.POSITION_TOP.");
            }

            this.position = position;
            return this;
        }

        /**
         * Sets the message's primary color.
         *
         * @param color The message's primary color.
         * @return The builder.
         */
        @NonNull
        public Builder setPrimaryColor(@Nullable Integer color) {
            this.primaryColor = color;
            return this;
        }

        /**
         * Sets the message's secondary color.
         *
         * @param color The message's secondary color.
         * @return The builder.
         */
        @NonNull
        public Builder setSecondaryColor(@Nullable Integer color) {
            this.secondaryColor = color;
            return this;
        }

        /**
         * Creates the message.
         *
         * @return The created in-app message.
         */
        @NonNull
        public InAppMessage create() {
            return new InAppMessage(this);
        }
    }
}
