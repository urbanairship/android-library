/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.graphics.Color;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.TextAlignment;
import com.urbanairship.android.layout.property.TextStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LabelModel extends BaseModel {
    @NonNull
    private final String text;
    @Nullable
    private final Integer fontSize;
    @Nullable
    @ColorInt
    private final Integer color;
    @Nullable
    private final TextAlignment alignment;
    @NonNull
    private final List<TextStyle> textStyles;
    @NonNull
    private final List<String> fontFamilies;

    public LabelModel(
        @NonNull String text,
        @Nullable Integer fontSize,
        @Nullable @ColorInt Integer color,
        @Nullable TextAlignment alignment,
        @NonNull List<TextStyle> textStyles,
        @NonNull List<String> fontFamilies,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable Border border) {
        super(ViewType.LABEL, backgroundColor, border);

        this.text = text;
        this.fontSize = fontSize;
        this.color = color;
        this.alignment = alignment;
        this.textStyles = textStyles;
        this.fontFamilies = fontFamilies;
    }

    @NonNull
    public static LabelModel fromJson(@NonNull JsonMap json) {
        String text = json.opt("text").optString();
        int fontSizeInt = json.opt("fontSize").getInt(-1);
        String colorString = json.opt("color").optString();
        String alignmentString = json.opt("alignment").optString();
        JsonList textStylesJson = json.opt("textStyles").optList();
        JsonList fontFamiliesJson = json.opt("fontFamilies").optList();

        Integer fontSize = fontSizeInt == -1 ? null : fontSizeInt;
        @ColorInt Integer color = colorString.isEmpty() ? null : Color.parseColor(colorString);
        TextAlignment alignment = alignmentString.isEmpty() ? null : TextAlignment.from(alignmentString);

        List<TextStyle> textStyles = new ArrayList<>();
        if (textStylesJson.size() > 0) {
            for (int i = 0; i < textStylesJson.size(); i++) {
                String styleString = textStylesJson.get(i).optString();
                TextStyle style = TextStyle.from(styleString);
                textStyles.add(style);
            }
        }

        List<String> fontFamilies = new ArrayList<>();
        if (fontFamiliesJson.size() > 0) {
            for (int i = 0; i < fontFamiliesJson.size(); i++) {
                String fontFamily = fontFamiliesJson.get(i).optString();
                fontFamilies.add(fontFamily);
            }
        }

        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new LabelModel(text, fontSize, color, alignment, textStyles, fontFamilies, backgroundColor, border);
    }

    @NonNull
    public String getText() {
        return text;
    }

    @Nullable
    public Integer getFontSize() {
        return fontSize;
    }

    @Nullable
    @ColorInt
    public Integer getColor() {
        return color;
    }

    @Nullable
    public TextAlignment getAlignment() {
        return alignment;
    }

    @NonNull
    public List<TextStyle> getTextStyles() {
        return textStyles;
    }

    @NonNull
    public List<String> getFontFamilies() {
        return fontFamilies;
    }
}
