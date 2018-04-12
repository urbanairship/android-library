/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.StringDef;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.ColorUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text display info.
 */
public class TextInfo implements JsonSerializable {

    // JSON keys
    private static final String TEXT_KEY = "text";
    private static final String SIZE_KEY = "size";
    private static final String COLOR_KEY = "color";
    private static final String ALIGNMENT_KEY = "alignment";
    private static final String STYLE_KEY = "style";
    private static final String FONT_FAMILY_KEY = "font_family";
    private static final String ANDROID_DRAWABLE_RES_ID_KEY = "android_drawable_res_id";


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
     * Center text alignment.
     */
    public static final String ALIGNMENT_CENTER = "center";


    @StringDef({ STYLE_BOLD, STYLE_UNDERLINE, STYLE_ITALIC })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Style {}

    public static final String STYLE_BOLD = "bold";
    public static final String STYLE_UNDERLINE = "underline";
    public static final String STYLE_ITALIC = "italic";

    private final String text;
    @ColorInt
    private final Integer color;
    private final Float size;
    @Alignment
    private final String alignment;
    @Style
    private final List<String> styles;

    private final List<String> fontFamilies;

    @DrawableRes
    private final int drawable;

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
        this.styles = new ArrayList<>(builder.styles);
        this.drawable = builder.drawable;
        this.fontFamilies = new ArrayList<>(builder.fontFamilies);
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(TEXT_KEY, text)
                      .putOpt(COLOR_KEY, color == null ? null : ColorUtils.convertToString(color))
                      .putOpt(SIZE_KEY, size)
                      .put(ALIGNMENT_KEY, alignment)
                      .put(STYLE_KEY, JsonValue.wrapOpt(styles))
                      .put(FONT_FAMILY_KEY, JsonValue.wrapOpt(fontFamilies))
                      .putOpt(ANDROID_DRAWABLE_RES_ID_KEY, drawable == 0 ? null : drawable)
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses a {@link TextInfo} from a {@link JsonValue}.
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
                    break;
                default:
                    throw new JsonException("Unexpected alignment: " + content.opt(ALIGNMENT_KEY));
            }
        }

        // Styles
        if (content.containsKey(STYLE_KEY)) {
            if (!content.opt(STYLE_KEY).isJsonList()) {
                throw new JsonException("Style must be an array: " + content.opt(STYLE_KEY));
            }

            for (JsonValue value : content.opt(STYLE_KEY).optList()) {
                switch (value.getString("").toLowerCase(Locale.ROOT)) {
                    case STYLE_BOLD:
                        builder.addStyle(STYLE_BOLD);
                        break;
                    case STYLE_ITALIC:
                        builder.addStyle(STYLE_ITALIC);
                        break;
                    case STYLE_UNDERLINE:
                        builder.addStyle(STYLE_UNDERLINE);
                        break;
                    default:
                        throw new JsonException("Invalid style: " + value);

                }
            }
        }

        // Fonts
        if (content.containsKey(FONT_FAMILY_KEY)) {
            if (!content.opt(FONT_FAMILY_KEY).isJsonList()) {
                throw new JsonException("Fonts must be an array: " + content.opt(STYLE_KEY));
            }

            for (JsonValue value : content.opt(FONT_FAMILY_KEY).optList()) {
                if (!value.isString()) {
                    throw new JsonException("Invalid font: " + value);
                }
                builder.addFontFamily(value.getString());
            }
        }

        // Android drawable
        builder.setDrawable(content.opt(ANDROID_DRAWABLE_RES_ID_KEY).getInt(0));

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
    @Nullable
    public String getText() {
        return text;
    }

    /**
     * Returns the font size.
     *
     * @return The font size.
     */
    @Nullable
    public Float getFontSize() {
        return size;
    }

    /**
     * Returns the font color.
     *
     * @return The font color.
     */
    @Nullable
    public Integer getColor() {
        return color;
    }

    /**
     * Returns the text alignment.
     *
     * @return The text alignment.
     */
    @Nullable
    @Alignment
    public String getAlignment() {
        return alignment;
    }

    /**
     * Returns a list of text styles.
     *
     * @return The list of text styles.
     */
    @NonNull
    @Style
    public List<String> getStyles() {
        return styles;
    }

    /**
     * List of font families.
     *
     * @return The list of font families.
     */
    @NonNull
    public List<String> getFontFamilies() {
        return fontFamilies;
    }

    /**
     * Returns the button icon.
     *
     * @return The icon resource ID.
     */
    @DrawableRes
    public int getDrawable() {
        return drawable;
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

        if (drawable != textInfo.drawable) {
            return false;
        }
        if (text != null ? !text.equals(textInfo.text) : textInfo.text != null) {
            return false;
        }
        if (color != null ? !color.equals(textInfo.color) : textInfo.color != null) {
            return false;
        }
        if (size != null ? !size.equals(textInfo.size) : textInfo.size != null) {
            return false;
        }
        if (alignment != null ? !alignment.equals(textInfo.alignment) : textInfo.alignment != null) {
            return false;
        }
        if (styles != null ? !styles.equals(textInfo.styles) : textInfo.styles != null) {
            return false;
        }
        return fontFamilies != null ? fontFamilies.equals(textInfo.fontFamilies) : textInfo.fontFamilies == null;
    }

    @Override
    public int hashCode() {
        int result = text != null ? text.hashCode() : 0;
        result = 31 * result + (color != null ? color.hashCode() : 0);
        result = 31 * result + (size != null ? size.hashCode() : 0);
        result = 31 * result + (alignment != null ? alignment.hashCode() : 0);
        result = 31 * result + (styles != null ? styles.hashCode() : 0);
        result = 31 * result + (fontFamilies != null ? fontFamilies.hashCode() : 0);
        result = 31 * result + drawable;
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
        private Integer color;
        private Float size;
        @DrawableRes
        private int drawable;

        @Alignment
        private String alignment;
        private List<String> styles = new ArrayList<>();
        private List<String> fontFamilies = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the text.
         *
         * @param text The text.
         * @return The builder instance.
         */
        public Builder setText(@NonNull @Size(min = 1) String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the drawable to appear next to the text.
         *
         * @param drawable The drawable resource ID.
         * @return The builder instance.
         */
        public Builder setDrawable(@DrawableRes int drawable) {
            this.drawable = drawable;
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
         * Adds a style.
         *
         * @param style The text style.
         * @return The builder instance.
         */
        public Builder addStyle(@NonNull @Style String style) {
            if (!styles.contains(style)) {
                styles.add(style);
            }
            return this;
        }

        /**
         * Adds a font family. The first font family found in the application's font resource directory
         * will be used.
         *
         * @param font The font family.
         * @return The builder instance.
         */
        public Builder addFontFamily(String font) {
            fontFamilies.add(font);
            return this;
        }

        /**
         * Builds the text info.
         *
         * @return The text info.
         * @throws IllegalArgumentException If the text and label are missing.
         */
        public TextInfo build() {
            Checks.checkArgument(drawable != 0 || text != null, "Missing text.");
            return new TextInfo(this);
        }
    }
}
