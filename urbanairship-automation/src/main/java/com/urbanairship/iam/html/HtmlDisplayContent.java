/* Copyright Airship and Contributors */

package com.urbanairship.iam.html;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;

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

    /**
     * The content's width payload key
     */
    @NonNull
    public static final String WIDTH_KEY = "width";

    /**
     * The content's height payload key
     */
    @NonNull
    public static final String HEIGHT_KEY = "height";

    /**
     * The content's aspect lock payload key
     */
    @NonNull
    public static final String ASPECT_LOCK_KEY = "aspect_lock";

    /**
     * The content's require connectivity key
     */
    @NonNull
    public static final String REQUIRE_CONNECTIVITY = "require_connectivity";

    private final String url;
    private final int dismissButtonColor;
    private final int backgroundColor;
    private final float borderRadius;
    private final boolean isFullscreenDisplayAllowed;
    private final int width;
    private final int height;
    private final boolean keepAspectRatio;
    private final boolean requireConnectivity;

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
        this.width = builder.width;
        this.height = builder.height;
        this.keepAspectRatio = builder.keepAspectRatio;
        this.requireConnectivity = builder.requireConnectivity;
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

        // Require connectivity
        if (content.containsKey(REQUIRE_CONNECTIVITY)) {
            if (!content.opt(REQUIRE_CONNECTIVITY).isBoolean()) {
                throw new JsonException("Require connectivity must be a boolean " + content.opt(REQUIRE_CONNECTIVITY));
            }

            builder.setRequireConnectivity(content.opt(REQUIRE_CONNECTIVITY).getBoolean(true));
        }

        // Width
        if (content.containsKey(WIDTH_KEY) && !content.opt(WIDTH_KEY).isNumber()) {
            throw new JsonException("Width must be a number " + content.opt(WIDTH_KEY));
        }

        // Height
        if (content.containsKey(HEIGHT_KEY) && !content.opt(HEIGHT_KEY).isNumber()) {
            throw new JsonException("Height must be a number " + content.opt(HEIGHT_KEY));
        }

        // Aspect lock
        if (content.containsKey(ASPECT_LOCK_KEY) && !content.opt(ASPECT_LOCK_KEY).isBoolean()) {
            throw new JsonException("Aspect lock must be a boolean " + content.opt(ASPECT_LOCK_KEY));
        }

        int width = content.opt(WIDTH_KEY).getInt(0);
        int height = content.opt(HEIGHT_KEY).getInt(0);
        boolean aspectLock = content.opt(ASPECT_LOCK_KEY).getBoolean(false);
        builder.setSize(width, height, aspectLock);

        try {
            return builder.build();
        } catch (
                IllegalArgumentException e) {
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
                      .put(WIDTH_KEY, width)
                      .put(HEIGHT_KEY, height)
                      .put(ASPECT_LOCK_KEY, keepAspectRatio)
                      .put(REQUIRE_CONNECTIVITY, requireConnectivity)
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

    @NonNull
    @Override
    public String toString() {
        return toJsonValue().toString();
    }

    @Override
    public boolean equals(Object o) {
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

        if (width != that.width) {
            return false;
        }

        if (height != that.height) {
            return false;
        }

        if (keepAspectRatio != that.keepAspectRatio) {
            return false;
        }

        if (requireConnectivity != that.requireConnectivity) {
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
        result = 31 * result + width;
        result = 31 * result + height;
        result = 31 * result + (keepAspectRatio ? 1 : 0);
        result = 31 * result + (requireConnectivity ? 1 : 0);
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
     * Gets the desired width when displaying the message as a dialog.
     *
     * @return The desired width.
     */
    @Dimension
    public long getWidth() {
        return width;
    }

    /**
     * Gets the desired height when displaying the message as a dialog.
     *
     * @return The desired height.
     */
    @Dimension
    public long getHeight() {
        return height;
    }

    /**
     * Gets the aspect lock when displaying the message as a dialog.
     *
     * @return The aspect lock.
     */
    public boolean getAspectRatioLock() {
        return keepAspectRatio;
    }

    /**
     * Checks if the message can be displayed or not if connectivity is unavailable.
     *
     * @return {@code true} if connectivity is required, otherwise {@code false}.
     */
    public boolean getRequireConnectivity() {
        return requireConnectivity;
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
        private int width;
        private int height;
        private boolean keepAspectRatio;
        private boolean requireConnectivity = true;

        private Builder() {}

        private Builder(@NonNull HtmlDisplayContent displayContent) {
            this.url = displayContent.url;
            this.dismissButtonColor = displayContent.dismissButtonColor;
            this.backgroundColor = displayContent.backgroundColor;
            this.width = displayContent.width;
            this.height = displayContent.height;
            this.keepAspectRatio = displayContent.keepAspectRatio;
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
         * Sets the size constraint for the display content when displaying the HTML message as a dialog.
         *
         * @param width The width. Use 0 for fill.
         * @param height The height. Use 0 for fill.
         * @param keepAspectRatio If the aspect ratio should be kept if the width or height are larger
         * than the screen.
         * @return The builder instance.
         */
        @NonNull
        public Builder setSize(@Dimension int width, @Dimension int height, boolean keepAspectRatio) {
            this.width = width;
            this.height = height;
            this.keepAspectRatio = keepAspectRatio;
            return this;
        }

        /**
         * Sets whether the message should check connectivity before displaying.
         *
         * @param requireConnectivity {@code true} to require connectivity, otherwise {@code false}.
         * @return The builder instance.
         */
        @NonNull
        public Builder setRequireConnectivity(boolean requireConnectivity) {
            this.requireConnectivity = requireConnectivity;
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
