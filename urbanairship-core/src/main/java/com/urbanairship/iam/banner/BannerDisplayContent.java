/* Copyright Airship and Contributors */

package com.urbanairship.iam.banner;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.StringDef;

import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayContent;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.ColorUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Display content for a {@link com.urbanairship.iam.InAppMessage#TYPE_BANNER} in-app message.
 */
public class BannerDisplayContent implements DisplayContent {

    @StringDef({ PLACEMENT_TOP, PLACEMENT_BOTTOM })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Placement {}

    /**
     * Display the message on top of the screen.
     */
    @NonNull
    public static final String PLACEMENT_TOP = "top";

    /**
     * Display the message on bottom of the screen.
     */
    @NonNull
    public static final String PLACEMENT_BOTTOM = "bottom";

    @StringDef({ TEMPLATE_LEFT_MEDIA, TEMPLATE_RIGHT_MEDIA })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Template {}

    /**
     * Template to display the optional media on the left.
     */
    @NonNull
    public static final String TEMPLATE_LEFT_MEDIA = "media_left";

    /**
     * Template to display the optional media on the right.
     */
    @NonNull
    public static final String TEMPLATE_RIGHT_MEDIA = "media_right";

    /**
     * Default duration in milliseconds.
     */
    public static final long DEFAULT_DURATION_MS = 15000;

    /**
     * Maximum number of buttons supported by a banner.
     */
    public static final int MAX_BUTTONS = 2;

    /**
     * JSON key for actions. Not supported in the API but is needed for compatibility of v1 banners.
     */
    private static final String ACTIONS_KEY = "actions";

    private final TextInfo heading;
    private final TextInfo body;
    private final MediaInfo media;
    private final List<ButtonInfo> buttons;
    @ButtonLayout
    private final String buttonLayout;
    @Placement
    private final String placement;
    @Template
    private final String template;
    private final long duration;
    private final int backgroundColor;
    private final int dismissButtonColor;
    private final float borderRadius;
    private final Map<String, JsonValue> actions;

    /**
     * Default factory method.
     *
     * @param builder The display content builder.
     */
    private BannerDisplayContent(@NonNull Builder builder) {
        this.heading = builder.heading;
        this.body = builder.body;
        this.media = builder.media;
        this.buttonLayout = builder.buttonLayout;
        this.buttons = builder.buttons;
        this.placement = builder.placement;
        this.template = builder.template;
        this.duration = builder.duration;
        this.backgroundColor = builder.backgroundColor;
        this.dismissButtonColor = builder.dismissButtonColor;
        this.borderRadius = builder.borderRadius;
        this.actions = builder.actions;
    }

    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed BannerDisplayContent.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static BannerDisplayContent parseJson(@NonNull JsonValue json) throws JsonException {
        return fromJson(json);
    }

    /**
     * Parses banner display JSON.
     *
     * @param value The json payload.
     * @return The parsed banner display content.
     * @throws JsonException If the json was unable to be parsed.
     */
    @NonNull
    public static BannerDisplayContent fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap content = value.optMap();

        Builder builder = newBuilder();

        // Heading
        if (content.containsKey(HEADING_KEY)) {
            builder.setHeading(TextInfo.fromJson(content.opt(HEADING_KEY)));
        }

        // Body
        if (content.containsKey(BODY_KEY)) {
            builder.setBody(TextInfo.fromJson(content.opt(BODY_KEY)));
        }

        // Image
        if (content.containsKey(MEDIA_KEY)) {
            builder.setMedia(MediaInfo.fromJson(content.opt(MEDIA_KEY)));
        }

        // Buttons
        if (content.containsKey(BUTTONS_KEY)) {
            JsonList buttonJsonList = content.opt(BUTTONS_KEY).getList();
            if (buttonJsonList == null) {
                throw new JsonException("Buttons must be an array of button objects.");
            }

            builder.setButtons(ButtonInfo.fromJson(buttonJsonList));
        }

        // Button Layout
        if (content.containsKey(BUTTON_LAYOUT_KEY)) {
            switch (content.opt(BUTTON_LAYOUT_KEY).optString()) {
                case BUTTON_LAYOUT_STACKED:
                    builder.setButtonLayout(BUTTON_LAYOUT_STACKED);
                    break;
                case BUTTON_LAYOUT_JOINED:
                    builder.setButtonLayout(BUTTON_LAYOUT_JOINED);
                    break;
                case BUTTON_LAYOUT_SEPARATE:
                    builder.setButtonLayout(BUTTON_LAYOUT_SEPARATE);
                    break;
                default:
                    throw new JsonException("Unexpected button layout: " + content.opt(BUTTON_LAYOUT_KEY));
            }
        }

        // Placement
        if (content.containsKey(PLACEMENT_KEY)) {
            switch (content.opt(PLACEMENT_KEY).optString()) {
                case PLACEMENT_BOTTOM:
                    builder.setPlacement(PLACEMENT_BOTTOM);
                    break;
                case PLACEMENT_TOP:
                    builder.setPlacement(PLACEMENT_TOP);
                    break;
                default:
                    throw new JsonException("Unexpected placement: " + content.opt(PLACEMENT_KEY));
            }
        }

        // Template
        if (content.containsKey(TEMPLATE_KEY)) {
            switch (content.opt(TEMPLATE_KEY).optString()) {
                case TEMPLATE_LEFT_MEDIA:
                    builder.setTemplate(TEMPLATE_LEFT_MEDIA);
                    break;
                case TEMPLATE_RIGHT_MEDIA:
                    builder.setTemplate(TEMPLATE_RIGHT_MEDIA);
                    break;
                default:
                    throw new JsonException("Unexpected template: " + content.opt(TEMPLATE_KEY));
            }
        }

        // Duration
        if (content.containsKey(DURATION_KEY)) {
            long duration = content.opt(DURATION_KEY).getLong(0);
            if (duration == 0) {
                throw new JsonException("Invalid duration: " + content.opt(DURATION_KEY));
            }

            builder.setDuration(duration, TimeUnit.SECONDS);
        }

        // Background color
        if (content.containsKey(BACKGROUND_COLOR_KEY)) {
            try {
                builder.setBackgroundColor(Color.parseColor(content.opt(BACKGROUND_COLOR_KEY).optString()));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid background color: " + content.opt(BACKGROUND_COLOR_KEY), e);
            }
        }

        // Dismiss button color
        if (content.containsKey(DISMISS_BUTTON_COLOR_KEY)) {
            try {
                builder.setDismissButtonColor(Color.parseColor(content.opt(DISMISS_BUTTON_COLOR_KEY).optString()));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid dismiss button color: " + content.opt(DISMISS_BUTTON_COLOR_KEY), e);
            }
        }

        // Border radius
        if (content.containsKey(BORDER_RADIUS_KEY)) {
            if (!content.opt(BORDER_RADIUS_KEY).isNumber()) {
                throw new JsonException("Border radius must be a number " + content.opt(BORDER_RADIUS_KEY));
            }

            builder.setBorderRadius(content.opt(BORDER_RADIUS_KEY).getFloat(0));
        }

        // Actions
        if (content.containsKey(ACTIONS_KEY)) {
            JsonMap jsonMap = content.opt(ACTIONS_KEY).getMap();
            if (jsonMap == null) {
                throw new JsonException("Actions must be a JSON object: " + content.opt(ACTIONS_KEY));
            }

            builder.setActions(jsonMap.getMap());
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid banner JSON: " + content, e);
        }
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(HEADING_KEY, heading)
                      .put(BODY_KEY, body)
                      .put(MEDIA_KEY, media)
                      .put(BUTTONS_KEY, JsonValue.wrapOpt(buttons))
                      .put(BUTTON_LAYOUT_KEY, buttonLayout)
                      .put(PLACEMENT_KEY, placement)
                      .put(TEMPLATE_KEY, template)
                      .put(DURATION_KEY, TimeUnit.MILLISECONDS.toSeconds(duration))
                      .put(BACKGROUND_COLOR_KEY, ColorUtils.convertToString(backgroundColor))
                      .put(DISMISS_BUTTON_COLOR_KEY, ColorUtils.convertToString(dismissButtonColor))
                      .put(BORDER_RADIUS_KEY, borderRadius)
                      .put(ACTIONS_KEY, JsonValue.wrapOpt(actions))
                      .build()
                      .toJsonValue();
    }

    /**
     * Returns the optional heading {@link TextInfo}.
     *
     * @return The banner heading.
     */
    @Nullable
    public TextInfo getHeading() {
        return heading;
    }

    /**
     * Returns the optional body {@link TextInfo}.
     *
     * @return The banner body.
     */
    @Nullable
    public TextInfo getBody() {
        return body;
    }

    /**
     * Returns the optional {@link MediaInfo}.
     *
     * @return The banner media.
     */
    @Nullable
    public MediaInfo getMedia() {
        return media;
    }

    /**
     * Returns the list of optional buttons.
     *
     * @return A list of buttons.
     */
    @NonNull
    public List<ButtonInfo> getButtons() {
        return buttons;
    }

    /**
     * Returns the button layout.
     *
     * @return The button layout.
     */
    @NonNull
    @ButtonLayout
    public String getButtonLayout() {
        return buttonLayout;
    }

    /**
     * Returns the banner placement.
     *
     * @return The banner placement.
     */
    @NonNull
    @Placement
    public String getPlacement() {
        return placement;
    }

    /**
     * Returns the banner template.
     *
     * @return The banner template.
     */
    @NonNull
    @Template
    public String getTemplate() {
        return template;
    }

    /**
     * Returns the banner display duration.
     *
     * @return The banner display duration.
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Returns the banner background color.
     *
     * @return The banner background color.
     */
    @ColorInt
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Returns the banner dismiss button color.
     *
     * @return The banner dismiss button color.
     */
    @ColorInt
    public int getDismissButtonColor() {
        return dismissButtonColor;
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
     * Returns the action names and values to be run when the banner is clicked.
     *
     * @return The action map.
     */
    @NonNull
    public Map<String, JsonValue> getActions() {
        return actions;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BannerDisplayContent that = (BannerDisplayContent) o;

        if (duration != that.duration) {
            return false;
        }
        if (backgroundColor != that.backgroundColor) {
            return false;
        }
        if (dismissButtonColor != that.dismissButtonColor) {
            return false;
        }
        if (Float.compare(that.borderRadius, borderRadius) != 0) {
            return false;
        }
        if (heading != null ? !heading.equals(that.heading) : that.heading != null) {
            return false;
        }
        if (body != null ? !body.equals(that.body) : that.body != null) {
            return false;
        }
        if (media != null ? !media.equals(that.media) : that.media != null) {
            return false;
        }
        if (buttons != null ? !buttons.equals(that.buttons) : that.buttons != null) {
            return false;
        }
        if (buttonLayout != null ? !buttonLayout.equals(that.buttonLayout) : that.buttonLayout != null) {
            return false;
        }
        if (placement != null ? !placement.equals(that.placement) : that.placement != null) {
            return false;
        }
        if (template != null ? !template.equals(that.template) : that.template != null) {
            return false;
        }
        return actions != null ? actions.equals(that.actions) : that.actions == null;

    }

    @Override
    public int hashCode() {
        int result = heading != null ? heading.hashCode() : 0;
        result = 31 * result + (body != null ? body.hashCode() : 0);
        result = 31 * result + (media != null ? media.hashCode() : 0);
        result = 31 * result + (buttons != null ? buttons.hashCode() : 0);
        result = 31 * result + (buttonLayout != null ? buttonLayout.hashCode() : 0);
        result = 31 * result + (placement != null ? placement.hashCode() : 0);
        result = 31 * result + (template != null ? template.hashCode() : 0);
        result = 31 * result + (int) (duration ^ (duration >>> 32));
        result = 31 * result + backgroundColor;
        result = 31 * result + dismissButtonColor;
        result = 31 * result + (borderRadius != +0.0f ? Float.floatToIntBits(borderRadius) : 0);
        result = 31 * result + (actions != null ? actions.hashCode() : 0);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return toJsonValue().toString();
    }

    /**
     * Builder factory method.
     *
     * @return A builder instance.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates a builder from existing display content.
     *
     * @param displayContent The display content.
     * @return A builder instance.
     */
    @NonNull
    public static Builder newBuilder(@NonNull BannerDisplayContent displayContent) {
        return new Builder(displayContent);
    }

    /**
     * Banner Display Content Builder.
     */
    public static class Builder {

        private TextInfo heading;
        private TextInfo body;
        private MediaInfo media;
        private List<ButtonInfo> buttons = new ArrayList<>();
        @ButtonLayout
        private String buttonLayout = BUTTON_LAYOUT_SEPARATE;
        @Placement
        private String placement = PLACEMENT_BOTTOM;
        @Template
        private String template = TEMPLATE_LEFT_MEDIA;
        private long duration = DEFAULT_DURATION_MS;
        private int backgroundColor = Color.WHITE;
        private int dismissButtonColor = Color.BLACK;
        private float borderRadius = 0;
        private final Map<String, JsonValue> actions = new HashMap<>();

        private Builder() {
        }

        private Builder(@NonNull BannerDisplayContent displayContent) {
            this.heading = displayContent.heading;
            this.body = displayContent.body;
            this.media = displayContent.media;
            this.buttonLayout = displayContent.buttonLayout;
            this.buttons = displayContent.buttons;
            this.placement = displayContent.placement;
            this.template = displayContent.template;
            this.duration = displayContent.duration;
            this.backgroundColor = displayContent.backgroundColor;
            this.dismissButtonColor = displayContent.dismissButtonColor;
            this.borderRadius = displayContent.borderRadius;
            this.actions.putAll(displayContent.actions);
        }

        /**
         * Sets the banner's heading.
         *
         * @param heading The banner's heading.
         * @return The builder instance.
         */
        @NonNull
        public Builder setHeading(@Nullable TextInfo heading) {
            this.heading = heading;
            return this;
        }

        /**
         * Sets the banner's body.
         *
         * @param body The banner's body.
         * @return The builder instance.
         */
        @NonNull
        public Builder setBody(@Nullable TextInfo body) {
            this.body = body;
            return this;
        }

        /**
         * Adds a button to the banner. Only 2 buttons are supported.
         *
         * @param buttonInfo Adds a button to the banner.
         * @return The builder instance.
         */
        @NonNull
        public Builder addButton(@NonNull ButtonInfo buttonInfo) {
            this.buttons.add(buttonInfo);
            return this;
        }

        /**
         * Sets the banner's buttons. Only 2 buttons are supported.
         *
         * @param buttons A list of button infos.
         * @return The builder instance.
         */
        @NonNull
        public Builder setButtons(@Nullable @Size(max = 2) List<ButtonInfo> buttons) {
            this.buttons.clear();
            if (buttons != null) {
                this.buttons.addAll(buttons);
            }

            return this;
        }

        /**
         * Sets the banner media. Only {@link MediaInfo#TYPE_IMAGE} is supported.
         *
         * @param media The media info.
         * @return The builder instance.
         */
        @NonNull
        public Builder setMedia(@Nullable MediaInfo media) {
            this.media = media;
            return this;
        }

        /**
         * Sets the button layout. Defaults to {@link #BUTTON_LAYOUT_SEPARATE}.
         *
         * @param buttonLayout The button layout.
         * @return The builder instance.
         */
        @NonNull
        public Builder setButtonLayout(@NonNull @ButtonLayout String buttonLayout) {
            this.buttonLayout = buttonLayout;
            return this;
        }

        /**
         * Sets the banner's placement. Defaults to {@link #PLACEMENT_BOTTOM}.
         *
         * @param placement The banner's placement.
         * @return The builder instance.
         */
        @NonNull
        public Builder setPlacement(@NonNull @Placement String placement) {
            this.placement = placement;
            return this;
        }

        /**
         * Sets the banner's template. Defaults to {@link #TEMPLATE_LEFT_MEDIA}.
         *
         * @param template The banner's template.
         * @return The builder instance.
         */
        @NonNull
        public Builder setTemplate(@NonNull @Template String template) {
            this.template = template;
            return this;
        }

        /**
         * Sets the background color. Defaults to white.
         *
         * @param color The background color.
         * @return The builder instance.
         */
        @NonNull
        public Builder setBackgroundColor(@ColorInt int color) {
            this.backgroundColor = color;
            return this;
        }

        /**
         * Sets the dismiss button color. Defaults to black.
         *
         * @param color The dismiss button color.
         * @return The builder instance.
         */
        @NonNull
        public Builder setDismissButtonColor(@ColorInt int color) {
            this.dismissButtonColor = color;
            return this;
        }

        /**
         * Sets the border radius in dps. Defaults to 0.
         *
         * @param borderRadius The border radius.
         * @return The builder instance.
         */
        @NonNull
        public Builder setBorderRadius(@FloatRange(from = 0.0, to = 20.0) float borderRadius) {
            this.borderRadius = borderRadius;
            return this;
        }

        /**
         * Sets the display duration. Defaults to {@link #DEFAULT_DURATION_MS}.
         *
         * @param duration The duration in milliseconds.
         * @param timeUnit The time unit.
         * @return The builder instance.
         */
        @NonNull
        public Builder setDuration(@IntRange(from = 0) long duration, @NonNull TimeUnit timeUnit) {
            this.duration = timeUnit.toMillis(duration);
            return this;
        }

        /**
         * Sets the actions to run when the banner is clicked.
         *
         * @param actions The action map.
         * @return The builder instance.
         */
        @NonNull
        public Builder setActions(@Nullable Map<String, JsonValue> actions) {
            this.actions.clear();

            if (actions != null) {
                this.actions.putAll(actions);
            }

            return this;
        }

        /**
         * Adds an action to run when the banner is clicked.
         *
         * @param actionName The action name.
         * @param actionValue The action value.
         * @return The builder instance.
         */
        @NonNull
        public Builder addAction(@NonNull String actionName, @NonNull JsonValue actionValue) {
            this.actions.put(actionName, actionValue);
            return this;
        }

        /**
         * Builds the banner display content.
         *
         * @return The banner display content.
         * @throws IllegalArgumentException If more than 2 button are defined, if the supplied media
         * is not an image, or if the banner does not define at least a heading, body, or buttons.
         */
        @NonNull
        public BannerDisplayContent build() {
            Checks.checkArgument(borderRadius >= 0 && borderRadius <= 20.0, "Border radius must be between 0 and 20.");
            Checks.checkArgument(heading != null || body != null, "Either the body or heading must be defined.");
            Checks.checkArgument(buttons.size() <= MAX_BUTTONS, "Banner allows a max of " + MAX_BUTTONS + " buttons");
            Checks.checkArgument(media == null || media.getType().equals(MediaInfo.TYPE_IMAGE), "Banner only supports image media");

            return new BannerDisplayContent(this);
        }

    }

}
