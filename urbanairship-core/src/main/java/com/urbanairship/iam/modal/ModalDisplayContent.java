/* Copyright Airship and Contributors */

package com.urbanairship.iam.modal;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
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
import java.util.List;

/**
 * Display content for a {@link com.urbanairship.iam.InAppMessage#TYPE_MODAL} in-app message.
 */
public class ModalDisplayContent implements DisplayContent {

    @StringDef({ TEMPLATE_HEADER_MEDIA_BODY, TEMPLATE_MEDIA_HEADER_BODY, TEMPLATE_HEADER_BODY_MEDIA })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Template {}

    /**
     * Template with display order of header, media, body, buttons, footer.
     */
    @NonNull
    public static final String TEMPLATE_HEADER_MEDIA_BODY = "header_media_body";

    /**
     * Template with display order of media, header, body, buttons, footer.
     */
    @NonNull
    public static final String TEMPLATE_MEDIA_HEADER_BODY = "media_header_body";

    /**
     * Template with display order of header, body, media, buttons, footer.
     */
    @NonNull
    public static final String TEMPLATE_HEADER_BODY_MEDIA = "header_body_media";

    private final TextInfo heading;
    private final TextInfo body;
    private final MediaInfo media;
    private final List<ButtonInfo> buttons;
    @ButtonLayout
    private final String buttonLayout;
    @Template
    private final String template;
    private final int backgroundColor;
    private final int dismissButtonColor;
    private final ButtonInfo footer;
    private final float borderRadius;
    private final boolean isFullscreenDisplayAllowed;

    /**
     * Maximum number of button supported by a modal.
     */
    public static final int MAX_BUTTONS = 2;

    /**
     * Default factory method.
     *
     * @param builder The display content builder.
     */
    private ModalDisplayContent(@NonNull Builder builder) {
        this.heading = builder.heading;
        this.body = builder.body;
        this.media = builder.media;
        this.buttonLayout = builder.buttonLayout;
        this.buttons = builder.buttons;
        this.template = builder.template;
        this.backgroundColor = builder.backgroundColor;
        this.dismissButtonColor = builder.dismissButtonColor;
        this.footer = builder.footer;
        this.borderRadius = builder.borderRadius;
        this.isFullscreenDisplayAllowed = builder.isFullscreenDisplayAllowed;
    }

    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed ModalDisplayContent.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static ModalDisplayContent parseJson(@NonNull JsonValue json) throws JsonException {
        return fromJson(json);
    }

    /**
     * Parses modal display JSON.
     *
     * @param value The json payload.
     * @return The parsed display content.
     * @throws JsonException If the json was unable to be parsed.
     */
    @NonNull
    public static ModalDisplayContent fromJson(@NonNull JsonValue value) throws JsonException {
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

        // Media
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

        // Footer
        if (content.containsKey(FOOTER_KEY)) {
            builder.setFooter(ButtonInfo.fromJson(content.opt(FOOTER_KEY)));
        }

        // Template
        if (content.containsKey(TEMPLATE_KEY)) {
            switch (content.opt(TEMPLATE_KEY).optString()) {
                case TEMPLATE_HEADER_BODY_MEDIA:
                    builder.setTemplate(TEMPLATE_HEADER_BODY_MEDIA);
                    break;
                case TEMPLATE_HEADER_MEDIA_BODY:
                    builder.setTemplate(TEMPLATE_HEADER_MEDIA_BODY);
                    break;
                case TEMPLATE_MEDIA_HEADER_BODY:
                    builder.setTemplate(TEMPLATE_MEDIA_HEADER_BODY);
                    break;
                default:
                    throw new JsonException("Unexpected template: " + content.opt(TEMPLATE_KEY));
            }
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

        // Allow Fullscreen display
        if (content.containsKey(ALLOW_FULLSCREEN_DISPLAY_KEY)) {
            if (!content.opt(ALLOW_FULLSCREEN_DISPLAY_KEY).isBoolean()) {
                throw new JsonException("Allow fullscreen display must be a boolean " + content.opt(ALLOW_FULLSCREEN_DISPLAY_KEY));
            }

            builder.setAllowFullscreenDisplay(content.opt(ALLOW_FULLSCREEN_DISPLAY_KEY).getBoolean(false));
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid in-app message modal JSON: " + content, e);
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
                      .put(TEMPLATE_KEY, template)
                      .put(BACKGROUND_COLOR_KEY, ColorUtils.convertToString(backgroundColor))
                      .put(DISMISS_BUTTON_COLOR_KEY, ColorUtils.convertToString(dismissButtonColor))
                      .put(FOOTER_KEY, footer)
                      .put(BORDER_RADIUS_KEY, borderRadius)
                      .put(ALLOW_FULLSCREEN_DISPLAY_KEY, isFullscreenDisplayAllowed)
                      .build()
                      .toJsonValue();
    }

    /**
     * Returns {@code true} if the modal dialog is allowed to be displayed as fullscreen, otherwise
     * {@code false}. See {@link Builder#setAllowFullscreenDisplay(boolean)}} for more details.
     *
     * @return {@code true} to allow the modal dialog to display as full screen, otherwise {@code false}.
     */
    public boolean isFullscreenDisplayAllowed() {
        return isFullscreenDisplayAllowed;
    }

    /**
     * Returns the optional heading {@link TextInfo}.
     *
     * @return The display heading.
     */
    @Nullable
    public TextInfo getHeading() {
        return heading;
    }

    /**
     * Returns the optional body {@link TextInfo}.
     *
     * @return The display body.
     */
    @Nullable
    public TextInfo getBody() {
        return body;
    }

    /**
     * Returns the optional {@link MediaInfo}.
     *
     * @return The display media.
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
     * Returns the template.
     *
     * @return The template.
     */
    @NonNull
    @Template
    public String getTemplate() {
        return template;
    }

    /**
     * Returns the background color.
     *
     * @return The background color.
     */
    @ColorInt
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Returns the dismiss button color.
     *
     * @return The dismiss button color.
     */
    @ColorInt
    public int getDismissButtonColor() {
        return dismissButtonColor;
    }

    /**
     * Returns the footer button.
     *
     * @return The footer button.
     */
    @Nullable
    public ButtonInfo getFooter() {
        return footer;
    }

    /**
     * Returns the border radius in dps.
     *
     * @return Border radius in dps.
     */
    public float getBorderRadius() {
        return borderRadius;
    }

    @NonNull
    @Override
    public String toString() {
        return toJsonValue().toString();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModalDisplayContent that = (ModalDisplayContent) o;

        if (backgroundColor != that.backgroundColor) {
            return false;
        }
        if (dismissButtonColor != that.dismissButtonColor) {
            return false;
        }
        if (Float.compare(that.borderRadius, borderRadius) != 0) {
            return false;
        }
        if (isFullscreenDisplayAllowed != that.isFullscreenDisplayAllowed) {
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
        if (!buttonLayout.equals(that.buttonLayout)) {
            return false;
        }
        if (!template.equals(that.template)) {
            return false;
        }
        return footer != null ? footer.equals(that.footer) : that.footer == null;
    }

    @Override
    public int hashCode() {
        int result = heading != null ? heading.hashCode() : 0;
        result = 31 * result + (body != null ? body.hashCode() : 0);
        result = 31 * result + (media != null ? media.hashCode() : 0);
        result = 31 * result + (buttons != null ? buttons.hashCode() : 0);
        result = 31 * result + buttonLayout.hashCode();
        result = 31 * result + template.hashCode();
        result = 31 * result + backgroundColor;
        result = 31 * result + dismissButtonColor;
        result = 31 * result + (footer != null ? footer.hashCode() : 0);
        result = 31 * result + (borderRadius != +0.0f ? Float.floatToIntBits(borderRadius) : 0);
        result = 31 * result + (isFullscreenDisplayAllowed ? 1 : 0);
        return result;
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
    public static Builder newBuilder(@NonNull ModalDisplayContent displayContent) {
        return new Builder(displayContent);
    }

    /**
     * Display Content Builder.
     */
    public static class Builder {

        private TextInfo heading;
        private TextInfo body;
        private MediaInfo media;
        private List<ButtonInfo> buttons = new ArrayList<>();
        @ButtonLayout
        private String buttonLayout = BUTTON_LAYOUT_SEPARATE;

        @Template
        private String template = TEMPLATE_HEADER_MEDIA_BODY;

        private int backgroundColor = Color.WHITE;
        private int dismissButtonColor = Color.BLACK;
        private ButtonInfo footer;
        private float borderRadius;
        private boolean isFullscreenDisplayAllowed;

        private Builder() {
        }

        private Builder(@NonNull ModalDisplayContent displayContent) {
            this.heading = displayContent.heading;
            this.body = displayContent.body;
            this.media = displayContent.media;
            this.buttonLayout = displayContent.buttonLayout;
            this.buttons = displayContent.buttons;
            this.template = displayContent.template;
            this.backgroundColor = displayContent.backgroundColor;
            this.dismissButtonColor = displayContent.dismissButtonColor;
            this.footer = displayContent.footer;
            this.borderRadius = displayContent.borderRadius;
            this.isFullscreenDisplayAllowed = displayContent.isFullscreenDisplayAllowed;
        }

        /**
         * Sets the message's heading.
         *
         * @param heading The message's heading.
         * @return The builder instance.
         */
        @NonNull
        public Builder setHeading(@Nullable TextInfo heading) {
            this.heading = heading;
            return this;
        }

        /**
         * Sets the message's body.
         *
         * @param body The message's body.
         * @return The builder instance.
         */
        @NonNull
        public Builder setBody(@Nullable TextInfo body) {
            this.body = body;
            return this;
        }

        /**
         * Adds a button info.
         *
         * @param buttonInfo Adds a button to the message.
         * @return The builder instance.
         */
        @NonNull
        public Builder addButton(@NonNull ButtonInfo buttonInfo) {
            this.buttons.add(buttonInfo);
            return this;
        }

        /**
         * Sets the message's buttons.
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
         * Sets the media.
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
         * Sets the button layout.
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
         * Sets the template. Defaults to {@link #TEMPLATE_HEADER_MEDIA_BODY}.
         *
         * @param template The message's template.
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
         * Sets the footer button.
         *
         * @param footer The footer button info.
         * @return The builder instance.
         */
        @NonNull
        public Builder setFooter(@Nullable ButtonInfo footer) {
            this.footer = footer;
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
         * Enables the modal dialog to display as fullscreen. The modal will display as fullscreen if
         * enabled and and the bool resource `ua_iam_modal_allow_fullscreen_display` is true.
         * `ua_iam_modal_allow_fullscreen_display` defaults to true  when the screen width is less than 480dps.
         *
         * @param isFullscreenDisplayAllowed {@code true} to allow displaying the iam as fullscreen,
         * otherwise {@code false}.
         * @return The builder instance.
         */
        @NonNull
        public Builder setAllowFullscreenDisplay(boolean isFullscreenDisplayAllowed) {
            this.isFullscreenDisplayAllowed = isFullscreenDisplayAllowed;
            return this;
        }

        /**
         * Builds the modal display content.
         *
         * @return The modal display content.
         * @throws IllegalArgumentException If more than 2 buttons are defined, or if both the heading and body are missing.
         */
        @NonNull
        public ModalDisplayContent build() {
            Checks.checkArgument(borderRadius >= 0 && borderRadius <= 20.0, "Border radius must be between 0 and 20.");
            Checks.checkArgument(buttons.size() <= MAX_BUTTONS, "Modal allows a max of " + MAX_BUTTONS + " buttons");
            Checks.checkArgument(heading != null || body != null, "Either the body or heading must be defined.");
            return new ModalDisplayContent(this);
        }

    }

}

