/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;

public class TextAppearance {
    @NonNull
    private final Color color;
    @Dimension(unit = Dimension.DP)
    private final int fontSize;
    @NonNull
    private final TextAlignment alignment;
    @NonNull
    private final List<TextStyle> textStyles;
    @NonNull
    private final List<String> fontFamilies;

    public TextAppearance(
        @NonNull Color color,
        int fontSize,
        @NonNull TextAlignment alignment,
        @NonNull List<TextStyle> textStyles,
        @NonNull List<String> fontFamilies
    ) {
        this.color = color;
        this.fontSize = fontSize;
        this.alignment = alignment;
        this.textStyles = textStyles;
        this.fontFamilies = fontFamilies;
    }

    @NonNull
    public static TextAppearance fromJson(@NonNull JsonMap json) throws JsonException {
        int fontSize = json.opt("font_size").getInt(14);
        Color color = Color.fromJsonField(json, "color");
        if (color == null) {
            throw new JsonException("Failed to parse text appearance. 'color' may not be null!");
        }
        String alignmentString = json.opt("alignment").optString();
        JsonList textStylesJson = json.opt("styles").optList();
        JsonList fontFamiliesJson = json.opt("font_families").optList();
        TextAlignment alignment = alignmentString.isEmpty()
            ? TextAlignment.CENTER
            : TextAlignment.from(alignmentString);

        List<TextStyle> textStyles = new ArrayList<>();
        for (int i = 0; i < textStylesJson.size(); i++) {
            String styleString = textStylesJson.get(i).optString();
            TextStyle style = TextStyle.from(styleString);
            textStyles.add(style);
        }

        List<String> fontFamilies = new ArrayList<>();
        for (int i = 0; i < fontFamiliesJson.size(); i++) {
            String fontFamily = fontFamiliesJson.get(i).optString();
            fontFamilies.add(fontFamily);
        }

        return new TextAppearance(color, fontSize, alignment, textStyles, fontFamilies);
    }

    @NonNull
    public Color getColor() {
        return color;
    }

    @Dimension(unit = Dimension.DP)
    public int getFontSize() {
        return fontSize;
    }

    @NonNull
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
