/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.html;

import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

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

    /**
     * Default factory method.
     *
     * @param builder The display content builder.
     */
    private HtmlDisplayContent(Builder builder) {
        this.url = builder.url;
        this.dismissButtonColor = builder.dismissButtonColor;
        this.backgroundColor = builder.backgroundColor;
    }

    /**
     * Parses HTML display JSON.
     *
     * @param json The json payload.
     * @return The parsed display content.
     * @throws JsonException If the json was unable to be parsed.
     */
    @NonNull
    public static HtmlDisplayContent parseJson(JsonValue json) throws JsonException {
        JsonMap content = json.optMap();

        Builder builder = newBuilder();

        // Dismiss button color
        if (content.containsKey(DISMISS_BUTTON_COLOR_KEY)) {
            try {
                builder.setDismissButtonColor(Color.parseColor(content.opt(DISMISS_BUTTON_COLOR_KEY).getString("")));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid dismiss button color: " + content.opt(DISMISS_BUTTON_COLOR_KEY), e);
            }
        }

        // URL
        if (content.containsKey(URL_KEY)) {
            builder.setUrl(content.opt(URL_KEY).getString());
        }

        // Background color
        if (content.containsKey(BACKGROUND_COLOR_KEY)) {
            try {
                builder.setBackgroundColor(Color.parseColor(content.opt(BACKGROUND_COLOR_KEY).getString("")));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid background color: " + content.opt(BACKGROUND_COLOR_KEY), e);
            }
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid html message JSON: " + content, e);
        }
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(DISMISS_BUTTON_COLOR_KEY, ColorUtils.convertToString(dismissButtonColor))
                      .put(URL_KEY, url)
                      .put(BACKGROUND_COLOR_KEY, ColorUtils.convertToString(backgroundColor))
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

        return url != null ? url.equals(that.url) : that.url == null;
    }

    @Override
    public int hashCode() {
        int result = url != null ? url.hashCode() : 0;
        result = 31 * result + dismissButtonColor;
        result = 31 * result + backgroundColor;
        return result;
    }

    /**
     * Builder factory method.
     *
     * @return A builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Display Content Builder.
     */
    public static class Builder {

        private String url;
        private int dismissButtonColor = Color.BLACK;
        private int backgroundColor = Color.WHITE;;

        /**
         * Default constructor.
         */
        private Builder() {}

        /**
         * Sets the message's URL.
         *
         * @param url The message's URL.
         * @return The builder instance.
         */
        @NonNull
        public Builder setUrl(String url) {
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
