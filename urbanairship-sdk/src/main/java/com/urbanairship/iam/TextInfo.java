/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.ColorUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Text display info.
 */
public class TextInfo implements JsonSerializable {

    // JSON keys
    private static final String TEXT_KEY = "text";
    private static final String SIZE_KEY = "size";
    private static final String COLOR_KEY = "color";
    private static final String ALIGNMENT_KEY = "alignment";

    @StringDef({ ALIGNMENT_RIGHT, ALIGNMENT_LEFT, ALIGNMENT_CENTER })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Alignment {}

    /**
     * Right text alignment.
     */
    public static final String ALIGNMENT_RIGHT = "right";

    /**
     * Left text alignment.
     */
    public static final String ALIGNMENT_LEFT = "left";

    /**
     * Center text alignment.`
     */
    public static final String ALIGNMENT_CENTER = "center";

    private final String text;
    @ColorInt
    private final int color;
    private final float size;
    @Alignment
    private final String alignment;

    /**
     * Default constructor.
     *
     * @param builder The text info builder.
     */
    private TextInfo(Builder builder) {
        this.text = builder.text;
        this.color = builder.color;
        this.size = builder.size;
        this.alignment = builder.alignment;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(TEXT_KEY, text)
                      .put(COLOR_KEY, ColorUtils.convertToString(color))
                      .put(SIZE_KEY, size)
                      .put(ALIGNMENT_KEY, alignment)
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses an {@link TextInfo} from a {@link JsonValue}.
     *
     * @param jsonValue The json value.
     * @return The parsed text info.
     * @throws JsonException If the text info was unable to be parsed.
     */
    public static TextInfo parseJson(JsonValue jsonValue) throws JsonException {
        JsonMap content = jsonValue.optMap();
        Builder builder = newBuilder();

        // Text
        if (content.containsKey(TEXT_KEY)) {
            builder.setText(content.get(TEXT_KEY).getString());
        }

        // Color
        if (content.containsKey(COLOR_KEY)) {
            try {
                builder.setColor(Color.parseColor(content.opt(COLOR_KEY).getString("")));
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid color: " + content.opt(COLOR_KEY), e);
            }
        }

        // Size
        if (content.containsKey(SIZE_KEY)) {
            if (!content.opt(SIZE_KEY).isNumber()) {
                throw new JsonException("Size must be a number: " + content.opt(SIZE_KEY));
            }

            builder.setFontSize(content.opt(SIZE_KEY).getNumber().floatValue());
        }

        // Alignment
        if (content.containsKey(ALIGNMENT_KEY)) {
            switch (content.opt(ALIGNMENT_KEY).getString("")) {
                case ALIGNMENT_CENTER:
                    builder.setAlignment(ALIGNMENT_CENTER);
                    break;
                case ALIGNMENT_LEFT:
                    builder.setAlignment(ALIGNMENT_LEFT);
                    break;
                case ALIGNMENT_RIGHT:
                    builder.setAlignment(ALIGNMENT_RIGHT);
                default:
                    throw new JsonException("Unexpected alignment: " + content.opt(ALIGNMENT_KEY));
            }
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid text object JSON: " + content, e);
        }
    }

    /**
     * Returns the text.
     *
     * @return The text.
     */
    @NonNull
    public String getText() {
        return text;
    }

    /**
     * Returns the font size.
     *
     * @return The font size.
     */
    public float getFontSize() {
        return size;
    }

    /**
     * Returns the font color.
     *
     * @return The font color.
     */
    public int getColor() {
        return color;
    }

    /**
     * Returns the text alignment.
     *
     * @return The text alignment.
     */
    @NonNull
    @Alignment
    public String getAlignment() {
        return alignment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TextInfo textInfo = (TextInfo) o;

        if (color != textInfo.color) {
            return false;
        }
        if (Float.compare(textInfo.size, size) != 0) {
            return false;
        }
        if (text != null ? !text.equals(textInfo.text) : textInfo.text != null) {
            return false;
        }
        return alignment != null ? alignment.equals(textInfo.alignment) : textInfo.alignment == null;

    }

    @Override
    public int hashCode() {
        int result = text != null ? text.hashCode() : 0;
        result = 31 * result + color;
        result = 31 * result + (size != +0.0f ? Float.floatToIntBits(size) : 0);
        result = 31 * result + (alignment != null ? alignment.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return toJsonValue().toString();
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
     * Text info builder.
     */
    public static class Builder {
        private String text;
        @ColorInt
        private int color = Color.BLACK;
        private float size = 14;
        @Alignment
        private String alignment = ALIGNMENT_LEFT;

        private Builder() {}

        /**
         * Sets the text.
         *
         * @param text The text.
         * @return The builder instance.
         */
        public Builder setText(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the text color. Defaults to black.
         *
         * @param color The text color.
         * @return The builder instance.
         */
        public Builder setColor(@ColorInt int color) {
            this.color = color;
            return this;
        }

        /**
         * Sets the font size. Defaults to 14sp.
         *
         * @param size The font size.
         * @return The builder instance.
         */
        public Builder setFontSize(float size) {
            this.size = size;
            return this;
        }

        /**
         * Sets the text alignment.
         *
         * @param alignment The text alignment.
         * @return The builder instance.
         */
        public Builder setAlignment(@NonNull @Alignment String alignment) {
            this.alignment = alignment;
            return this;
        }

        /**
         * Builds the text info.
         *
         * @return The text info.
         * @throws IllegalArgumentException If the text is missing.
         */
        public TextInfo build() {
            Checks.checkNotNull(text, "Missing text");
            return new TextInfo(this);
        }
    }
}
