/* Copyright Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import android.graphics.Color;
import androidx.annotation.ColorInt;
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
 * Display content for a {@link com.urbanairship.iam.InAppMessage#TYPE_FULLSCREEN} in-app message.
 */
public class FullScreenDisplayContent implements DisplayContent {

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

    /**
     * Maximum number of buttons.
     */
    public static final int MAX_BUTTONS = 5;

    /**
     * Default factory method.
     *
     * @param builder The display content builder.
     */
    private FullScreenDisplayContent(@NonNull Builder builder) {
        this.heading = builder.heading;
        this.body = builder.body;
        this.media = builder.media;
        this.buttonLayout = builder.buttonLayout;
        this.buttons = builder.buttons;
        this.template = builder.template;
        this.backgroundColor = builder.backgroundColor;
        this.dismissButtonColor = builder.dismissButtonColor;
        this.footer = builder.footer;
    }

    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed FullScreenDisplayContent.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static FullScreenDisplayContent parseJson(@NonNull JsonValue json) throws JsonException {
        return fromJson(json);
    }

    /**
     * Parses full screen display JSON.
     *
     * @param value The json payload.
     * @return The parsed display content.
     * @throws JsonException If the json was unable to be parsed.
     */
    @NonNull
    public static FullScreenDisplayContent fromJson(@NonNull JsonValue value) throws JsonException {
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

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid full screen message JSON: " + content, e);
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
                      .build()
                      .toJsonValue();
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

        FullScreenDisplayContent that = (FullScreenDisplayContent) o;

        if (backgroundColor != that.backgroundColor) {
            return false;
        }
        if (dismissButtonColor != that.dismissButtonColor) {
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
        if (template != null ? !template.equals(that.template) : that.template != null) {
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
        result = 31 * result + (buttonLayout != null ? buttonLayout.hashCode() : 0);
        result = 31 * result + (template != null ? template.hashCode() : 0);
        result = 31 * result + backgroundColor;
        result = 31 * result + dismissButtonColor;
        result = 31 * result + (footer != null ? footer.hashCode() : 0);
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
    public static Builder newBuilder(@NonNull FullScreenDisplayContent displayContent) {
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

        private Builder() {
        }

        private Builder(FullScreenDisplayContent displayContent) {
            this.heading = displayContent.heading;
            this.body = displayContent.body;
            this.media = displayContent.media;
            this.buttonLayout = displayContent.buttonLayout;
            this.buttons = displayContent.buttons;
            this.template = displayContent.template;
            this.backgroundColor = displayContent.backgroundColor;
            this.dismissButtonColor = displayContent.dismissButtonColor;
            this.footer = displayContent.footer;
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
         * Adds a button the button info. Max of 5 buttons are supported.
         * If more than 2 buttons are supplied, button layout will default to stacked.
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
         * Sets the message's buttons. Max of 5 buttons are supported.
         * If more than 2 buttons are supplied, button layout will default to stacked.
         *
         * @param buttons A list of button infos.
         * @return The builder instance.
         */
        @NonNull
        public Builder setButtons(@Nullable @Size(max = 5) List<ButtonInfo> buttons) {
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
         * Sets the button layout. If more than 2 buttons are supplied,
         * the layout will default to {@link com.urbanairship.iam.DisplayContent.ButtonLayout#BUTTON_LAYOUT_STACKED}.
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
         * Builds the full screen display content.
         *
         * @return The full screen display content.
         * @throws IllegalArgumentException If more than 5 buttons are defined, or if the heading and body are both missing.
         */
        @NonNull
        public FullScreenDisplayContent build() {
            if (buttons.size() > 2) {
                buttonLayout = BUTTON_LAYOUT_STACKED;
            }

            Checks.checkArgument(buttons.size() <= MAX_BUTTONS, "Full screen allows a max of " + MAX_BUTTONS + " buttons");
            Checks.checkArgument(heading != null || body != null, "Either the body or heading must be defined.");
            return new FullScreenDisplayContent(this);
        }

    }

}
