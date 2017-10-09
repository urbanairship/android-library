/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.ColorUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-app button display info.
 */
public class ButtonInfo implements JsonSerializable {

    // JSON Keys
    private static final String LABEL_KEY = "label";
    private static final String ID_KEY = "id";
    private static final String BEHAVIOR_KEY = "behavior";
    private static final String BORDER_RADIUS_KEY = "border_radius";
    private static final String BACKGROUND_COLOR_KEY = "background_color";
    private static final String BORDER_COLOR_KEY = "border_color";
    private static final String ACTIONS_KEY = "actions";

    @StringDef({ BEHAVIOR_CANCEL, BEHAVIOR_DISMISS })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Behavior {}

    /**
     * Cancels the in-app message's schedule when clicked.
     */
    public static final String BEHAVIOR_CANCEL = "cancel";

    /**
     * Dismisses the in-app message when clicked.
     */
    public static final String BEHAVIOR_DISMISS = "dismiss";

    private final TextInfo label;
    private final String id;
    @Behavior
    private final String behavior;
    private final float borderRadius;
    private final int backgroundColor;
    private final int borderColor;
    private final Map<String, JsonValue> actions;

    /**
     * Default constructor.
     *
     * @param builder A builder.
     */
    private ButtonInfo(Builder builder) {
        this.label = builder.label;
        this.id = builder.id;
        this.behavior = builder.behavior;
        this.borderRadius = builder.borderRadius;
        this.backgroundColor = builder.backgroundColor;
        this.borderColor = builder.borderColor;
        this.actions = builder.actions;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(LABEL_KEY, label)
                      .put(ID_KEY, id)
                      .put(BEHAVIOR_KEY, behavior)
                      .put(BORDER_RADIUS_KEY, borderRadius)
                      .put(BACKGROUND_COLOR_KEY, ColorUtils.convertToString(backgroundColor))
                      .put(BORDER_COLOR_KEY, ColorUtils.convertToString(borderColor))
                      .put(ACTIONS_KEY, JsonValue.wrapOpt(actions))
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses an {@link ButtonInfo} from a {@link JsonValue}.
     *
     * @param jsonValue The json value.
     * @return The parsed button info.
     * @throws JsonException If the button info was unable to be parsed.
     */
    public static ButtonInfo parseJson(JsonValue jsonValue) throws JsonException {
        JsonMap content = jsonValue.optMap();
        Builder builder = newBuilder();

        // Label
        if (content.containsKey(LABEL_KEY)) {
            builder.setLabel(TextInfo.parseJson(content.opt(LABEL_KEY)));
        }

        // ID
        builder.setId(content.opt(ID_KEY).getString());

        // Behavior
        if (content.containsKey(BEHAVIOR_KEY)) {
            switch (content.opt(BEHAVIOR_KEY).getString("")) {
                case BEHAVIOR_CANCEL:
                    builder.setBehavior(BEHAVIOR_CANCEL);
                    break;
                case BEHAVIOR_DISMISS:
                    builder.setBehavior(BEHAVIOR_DISMISS);
                    break;
                default:
                    throw new JsonException("Unexpected behavior: " + content.opt(BEHAVIOR_KEY));
            }
        }

        // Border radius
        if (content.containsKey(BORDER_RADIUS_KEY)) {
            if (!content.opt(BORDER_RADIUS_KEY).isNumber()) {
                throw new JsonException("Border radius must be a number: " + content.opt(BORDER_RADIUS_KEY));
            }

            builder.setBorderRadius(content.opt(BORDER_RADIUS_KEY).getNumber().floatValue());
        }

        // Background color
        if (content.containsKey(BACKGROUND_COLOR_KEY)) {
            try {
                builder.setBackgroundColor(Color.parseColor(content.opt(BACKGROUND_COLOR_KEY).getString("")));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid background button color: " + content.opt(BACKGROUND_COLOR_KEY), e);
            }
        }

        // Dismiss button color
        if (content.containsKey(BORDER_COLOR_KEY)) {
            try {
                builder.setBorderColor(Color.parseColor(content.opt(BORDER_COLOR_KEY).getString("")));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid border color: " + content.opt(BORDER_COLOR_KEY), e);
            }
        }

        // Actions
        if (content.containsKey(ACTIONS_KEY)) {
            JsonMap jsonMap = content.get(ACTIONS_KEY).getMap();
            if (jsonMap == null) {
                throw new JsonException("Actions must be a JSON object: " + content.opt(BORDER_RADIUS_KEY));
            }

            builder.setActions(jsonMap.getMap());
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid button JSON: " + content, e);
        }
    }

    /**
     * Parses a list of  {@link ButtonInfo} from a {@link JsonList}.
     *
     * @param jsonList The json list..
     * @return The list of parsed button info.
     * @throws JsonException If list was unable to be parsed.
     */
    public static List<ButtonInfo> parseJson(JsonList jsonList) throws JsonException {
        if (jsonList == null || jsonList.isEmpty()) {
            return Collections.emptyList();
        }

        List<ButtonInfo> buttons = new ArrayList<>();
        for (JsonValue value : jsonList) {
            buttons.add(parseJson(value));
        }

        return buttons;
    }

    /**
     * The button's ID.
     *
     * @return The button's ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the button's label.
     *
     * @return The button's label.
     */
    public TextInfo getLabel() {
        return label;
    }

    /**
     * Returns the button's click behavior.
     *
     * @return The button's click behavior.
     */
    @NonNull
    @Behavior
    public String getBehavior() {
        return behavior;
    }

    /**
     * Returns the button's background color.
     *
     * @return The button's background color.
     */
    @ColorInt
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Returns the button's border color.
     *
     * @return The button's border color.
     */
    @ColorInt
    public int getBorderColor() {
        return borderColor;
    }

    /**
     * Returns the border radius in dps.
     *
     * @return Border radius in dps.
     */
    public float getBorderRadius() {
        return borderRadius;
    }

    /**
     * Returns the action names and values to be run when the button is clicked.
     *
     * @return The action map.
     */
    @NonNull
    public Map<String, JsonValue> getActions() {
        return actions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ButtonInfo that = (ButtonInfo) o;

        if (Float.compare(that.borderRadius, borderRadius) != 0) {
            return false;
        }
        if (backgroundColor != that.backgroundColor) {
            return false;
        }
        if (borderColor != that.borderColor) {
            return false;
        }
        if (label != null ? !label.equals(that.label) : that.label != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (behavior != null ? !behavior.equals(that.behavior) : that.behavior != null) {
            return false;
        }
        return actions != null ? actions.equals(that.actions) : that.actions == null;

    }

    @Override
    public int hashCode() {
        int result = label != null ? label.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (behavior != null ? behavior.hashCode() : 0);
        result = 31 * result + (borderRadius != +0.0f ? Float.floatToIntBits(borderRadius) : 0);
        result = 31 * result + backgroundColor;
        result = 31 * result + borderColor;
        result = 31 * result + (actions != null ? actions.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return toJsonValue().toString();
    }

    /**
     * Builder factory method.
     *
     * @return A new builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Button info builder class.
     */
    public static class Builder {
        private TextInfo label;
        private String id;
        @Behavior
        private String behavior = BEHAVIOR_DISMISS;
        private float borderRadius = 0;
        private int backgroundColor = Color.TRANSPARENT;
        private int borderColor = Color.TRANSPARENT;
        private final Map<String, JsonValue> actions = new HashMap<>();

        private Builder() {}

        /**
         * Sets the button's label text info.
         *
         * @param label The button's label text info.
         * @return The builder instance.
         */
        public Builder setLabel(TextInfo label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the button's ID used for reporting events.
         *
         * @param id The button's ID.
         * @return The builder instance.
         */
        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the button behavior. Defaults to {@link #BEHAVIOR_DISMISS}.
         *
         * @param behavior The button's behavior.
         * @return The builder instance.
         */
        public Builder setBehavior(@NonNull @Behavior String behavior) {
            this.behavior = behavior;
            return this;
        }

        /**
         * Sets the border radius in dps. Defaults to 0.
         *
         * @param borderRadius The border radius.
         * @return The builder instance.
         */
        public Builder setBorderRadius(float borderRadius) {
            this.borderRadius = borderRadius;
            return this;
        }

        /**
         * Sets the button's border color. Defaults to transparent.
         *
         * @param borderColor The border color.
         * @return The builder instance.
         */
        public Builder setBorderColor(@ColorInt int borderColor) {
            this.borderColor = borderColor;
            return this;
        }

        /**
         * Sets the button's background color. Defaults to transparent.
         *
         * @param backgroundColor The background color.
         * @return The builder instance.
         */
        public Builder setBackgroundColor(@ColorInt int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        /**
         * Sets the actions to run when the button is clicked.
         *
         * @param actions The button's actions.
         * @return The builder instance.
         */
        public Builder setActions(Map<String, JsonValue> actions) {
            this.actions.clear();

            if (actions != null) {
                this.actions.putAll(actions);
            }

            return this;
        }

        /**
         * Adds a action to run when the button is clicked.
         *
         * @param actionName The action name.
         * @param actionValue The action value.
         * @return The builder instance.
         */
        public Builder addAction(@NonNull String actionName, @NonNull JsonSerializable actionValue) {
            this.actions.put(actionName, actionValue.toJsonValue());
            return this;
        }

        /**
         * Builds the button info.
         *
         * @return The button info.
         */
        public ButtonInfo build() {
            Checks.checkNotNull(label, "Missing label.");
            return new ButtonInfo(this);
        }
    }

}
