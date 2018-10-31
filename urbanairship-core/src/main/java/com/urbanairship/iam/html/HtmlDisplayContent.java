/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.html;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.iam.DisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.ColorUtils;

/**
 * Display content for a {@link com.urbanairship.iam.InAppMessage#TYPE_HTML} in-app message.
 */
public class HtmlDisplayContent implements DisplayContent {

    private final String url;
    private final int dismissButtonColor;
    private final int backgroundColor;
    private final float borderRadius;
    private final boolean isFullscreenDisplayAllowed;

    /**
     * Default factory method.
     *
     * @param builder The display content builder.
     */
    private HtmlDisplayContent(@NonNull Builder builder) {
        this.url = builder.url;
        this.dismissButtonColor = builder.dismissButtonColor;
        this.backgroundColor = builder.backgroundColor;
        this.borderRadius = builder.borderRadius;
        this.isFullscreenDisplayAllowed = builder.isFullscreenDisplayAllowed;
    }

    /**
     * Parses HTML display JSON.
     *
     * @param value The json payload.
     * @return The parsed display content.
     * @throws JsonException If the json was unable to be parsed.
     */
    @NonNull
    public static HtmlDisplayContent fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap content = value.optMap();

        Builder builder = newBuilder();

        // Dismiss button color
        if (content.containsKey(DISMISS_BUTTON_COLOR_KEY)) {
            try {
                builder.setDismissButtonColor(Color.parseColor(content.opt(DISMISS_BUTTON_COLOR_KEY).optString()));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid dismiss button color: " + content.opt(DISMISS_BUTTON_COLOR_KEY), e);
            }
        }

        // URL
        if (content.containsKey(URL_KEY)) {
            String url = content.opt(URL_KEY).getString();
            if (url == null) {
                throw new JsonException("Invalid url: " + content.opt(URL_KEY));
            }
            builder.setUrl(url);
        }

        // Background color
        if (content.containsKey(BACKGROUND_COLOR_KEY)) {
            try {
                builder.setBackgroundColor(Color.parseColor(content.opt(BACKGROUND_COLOR_KEY).optString()));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid background color: " + content.opt(BACKGROUND_COLOR_KEY), e);
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
            throw new JsonException("Invalid html message JSON: " + content, e);
        }
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(DISMISS_BUTTON_COLOR_KEY, ColorUtils.convertToString(dismissButtonColor))
                      .put(URL_KEY, url)
                      .put(BACKGROUND_COLOR_KEY, ColorUtils.convertToString(backgroundColor))
                      .put(BORDER_RADIUS_KEY, borderRadius)
                      .put(ALLOW_FULLSCREEN_DISPLAY_KEY, isFullscreenDisplayAllowed)
                      .build()
                      .toJsonValue();
    }


    /**
     * Returns the url.
     *
     * @return The url.
     */
    @NonNull
    public String getUrl() {
        return url;
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
     * Returns the background color.
     *
     * @return The background color.
     */
    @ColorInt
    public int getBackgroundColor() {
        return backgroundColor;
    }

    @SuppressLint("UnknownNullness")
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

        HtmlDisplayContent that = (HtmlDisplayContent) o;

        if (dismissButtonColor != that.dismissButtonColor) {
            return false;
        }
        if (backgroundColor != that.backgroundColor) {
            return false;
        }
        if (Float.compare(that.borderRadius, borderRadius) != 0) {
            return false;
        }
        if (isFullscreenDisplayAllowed != that.isFullscreenDisplayAllowed) {
            return false;
        }
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + dismissButtonColor;
        result = 31 * result + backgroundColor;
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
    public static Builder newBuilder(@NonNull HtmlDisplayContent displayContent) {
        return new Builder(displayContent);
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
     * Returns {@code true} if the html message is allowed to be displayed as fullscreen, otherwise
     * {@code false}. See {@link Builder#setAllowFullscreenDisplay(boolean)}} for more details.
     *
     * @return {@code true} to allow the html message to display as full screen, otherwise {@code false}.
     */
    public boolean isFullscreenDisplayAllowed() {
        return isFullscreenDisplayAllowed;
    }

    /**
     * Display Content Builder.
     */
    public static class Builder {

        private String url;
        private int dismissButtonColor = Color.BLACK;
        private int backgroundColor = Color.WHITE;
        private float borderRadius;
        private boolean isFullscreenDisplayAllowed;

        private Builder() {}

        private Builder(@NonNull HtmlDisplayContent displayContent) {
            this.url = displayContent.url;
            this.dismissButtonColor = displayContent.dismissButtonColor;
            this.backgroundColor = displayContent.backgroundColor;
        }

        /**
         * Sets the message's URL.
         *
         * @param url The message's URL.
         * @return The builder instance.
         */
        @NonNull
        public Builder setUrl(@NonNull String url) {
            this.url = url;
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
         * Enables the message to display as fullscreen. The message will display as fullscreen if
         * enabled and and the bool resource `ua_iam_html_allow_fullscreen_display` is true.
         * `ua_iam_html_allow_fullscreen_display` defaults to true  when the screen width is less than 480dps.
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
         * Builds the HTML display content.
         *
         * @return The HTML display content.
         * @throws IllegalArgumentException If the URL is missing.
         */
        @NonNull
        public HtmlDisplayContent build() {
            Checks.checkArgument(url != null, "Missing URL");
            return new HtmlDisplayContent(this);
        }
    }
}
