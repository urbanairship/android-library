package com.urbanairship.iam;
/* Copyright Airship and Contributors */

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

/**
 * Info used to generate an event when a message is finished.
 */
public final class ResolutionInfo implements JsonSerializable {

    @StringDef({ RESOLUTION_BUTTON_CLICK, RESOLUTION_MESSAGE_CLICK, RESOLUTION_USER_DISMISSED, RESOLUTION_TIMED_OUT })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    /**
     * Button click resolution.
     */
    @NonNull
    public static final String RESOLUTION_BUTTON_CLICK = "button_click";

    /**
     * Message click resolution
     */
    @NonNull
    public static final String RESOLUTION_MESSAGE_CLICK = "message_click";

    /**
     * User dismissed resolution.
     */
    @NonNull
    public static final String RESOLUTION_USER_DISMISSED = "user_dismissed";

    /**
     * Timed out resolution.
     */
    @NonNull
    public static final String RESOLUTION_TIMED_OUT = "timed_out";

    /**
     * Type key.
     */
    @NonNull
    public static final String TYPE_KEY = "type";

    /**
     * Button info key.
     */
    @NonNull
    public static final String BUTTON_INFO_KEY = "button_info";

    @NonNull
    private final String type;
    @Nullable
    private final ButtonInfo buttonInfo;

    /**
     * Default constructor.
     *
     * @param type The resolution type.
     */
    private ResolutionInfo(@Type @NonNull String type) {
        this.type = type;
        this.buttonInfo = null;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(TYPE_KEY, getType())
                      .putOpt(BUTTON_INFO_KEY, getButtonInfo())
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses an {@link ResolutionInfo} from a {@link JsonValue}.
     *
     * @param value The json value.
     * @return The parsed resolution info.
     * @throws JsonException If the resolution info was unable to be parsed.
     */
    @NonNull
    public static ResolutionInfo fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap content = value.optMap();

        // Type
        String type = content.opt(TYPE_KEY).getString();
        if (type == null) {
            throw new JsonException("ResolutionInfo must contain a type");
        }

        // Button Info
        ButtonInfo buttonInfo = null;
        if (content.opt(BUTTON_INFO_KEY).isJsonMap()) {
            buttonInfo = ButtonInfo.fromJson(content.opt(BUTTON_INFO_KEY));
        }

        return new ResolutionInfo(type, buttonInfo);
    }

    /**
     * Default constructor.
     *
     * @param type The resolution type.
     * @param buttonInfo The optional button info.
     */
    private ResolutionInfo(@Type @NonNull String type, @Nullable ButtonInfo buttonInfo) {
        this.type = type;
        this.buttonInfo = buttonInfo;
    }

    /**
     * Factory method to create a resolution info for a button press.
     *
     * @param buttonInfo The button info.
     * @return The resolution info.
     */
    @NonNull
    public static ResolutionInfo buttonPressed(@NonNull ButtonInfo buttonInfo) {
        return new ResolutionInfo(RESOLUTION_BUTTON_CLICK, buttonInfo);
    }


    /**
     * Factory method to create a resolution info for a button press.
     *
     * @param buttonId The button id.
     * @param buttonDescription The button description.
     * @param cancel If the button should cancel or not.
     * @return The resolution info.
     */
    @NonNull
    public static ResolutionInfo buttonPressed(@NonNull String buttonId, @Nullable String buttonDescription, boolean cancel) {
        ButtonInfo buttonInfo = ButtonInfo.newBuilder()
                                          .setBehavior(cancel ? ButtonInfo.BEHAVIOR_CANCEL : ButtonInfo.BEHAVIOR_DISMISS)
                                          .setId(buttonId)
                                          .setLabel(TextInfo.newBuilder()
                                                            .setText(buttonDescription == null ? buttonId : buttonDescription)
                                                            .build())
                                          .build();
        return new ResolutionInfo(RESOLUTION_BUTTON_CLICK, buttonInfo);
    }
    /**
     * Factory method to create a resolution info for when a clickable in-app message was clicked.
     *
     * @return The resolution info.
     */
    @NonNull
    public static ResolutionInfo messageClicked() {
        return new ResolutionInfo(RESOLUTION_MESSAGE_CLICK);
    }

    /**
     * Factory method to create a resolution info for when the user dismissed the in-app message.
     *
     * @return The resolution info.
     */
    @NonNull
    public static ResolutionInfo dismissed() {
        return new ResolutionInfo(RESOLUTION_USER_DISMISSED);
    }

    /**
     * Factory method to create a resolution info for when the in-app message times out and auto dismisses.
     *
     * @return The resolution info.
     */
    @NonNull
    public static ResolutionInfo timedOut() {
        return new ResolutionInfo(RESOLUTION_TIMED_OUT);
    }

    /**
     * The resolution type.
     *
     * @return The resolution type.
     */
    @NonNull
    @Type
    public String getType() {
        return type;
    }

    /**
     * The button info. Only available if the type is {@link #RESOLUTION_BUTTON_CLICK}.
     *
     * @return The button info.
     */
    @Nullable
    public ButtonInfo getButtonInfo() {
        return buttonInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ResolutionInfo that = (ResolutionInfo) o;

        if (!type.equals(that.type)) {
            return false;
        }
        return buttonInfo != null ? buttonInfo.equals(that.buttonInfo) : that.buttonInfo == null;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (buttonInfo != null ? buttonInfo.hashCode() : 0);
        return result;
    }

}
