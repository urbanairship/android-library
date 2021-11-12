/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Styling info for score views. */
public class ScoreStyle {
    @NonNull
    private final ScoreType type;
    private final int fontSize;
    @NonNull
    private final List<TextStyle> textStyles;
    @NonNull
    private final List<String> fontFamilies;
    @NonNull
    private final Border outlineBorder;
    private final int spacing;
    @NonNull
    private final ScoreColors selectedColors;
    @NonNull
    private final ScoreColors deselectedColors;

    public ScoreStyle(
        @NonNull ScoreType type,
        int fontSize,
        @NonNull List<TextStyle> textStyles,
        @NonNull List<String> fontFamilies,
        @NonNull Border outlineBorder,
        int spacing,
        @NonNull ScoreColors selectedColors,
        @NonNull ScoreColors deselectedColors
    ) {
        this.type = type;
        this.fontSize = fontSize;
        this.textStyles = textStyles;
        this.fontFamilies = fontFamilies;
        this.outlineBorder = outlineBorder;
        this.spacing = spacing;
        this.selectedColors = selectedColors;
        this.deselectedColors = deselectedColors;
    }

    @NonNull
    public static ScoreStyle fromJson(JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();
        int fontSize = json.opt("font_size").getInt(14);
        JsonList textStylesJson = json.opt("text_styles").optList();
        JsonList fontFamiliesJson = json.opt("font_families").optList();
        JsonMap outlineBorderJson = json.opt("outline_border").optMap();
        int spacing = json.opt("spacing").getInt(0);
        JsonMap selectedColorsJson = json.opt("selected_colors").optMap();
        JsonMap deselectedColorsJson = json.opt("deselected_colors").optMap();

        ScoreType type = ScoreType.from(typeString);

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

        Border outlineBorder = Border.fromJson(outlineBorderJson);
        ScoreColors selectedColors = ScoreColors.fromJson(selectedColorsJson);
        ScoreColors deselectedColors = ScoreColors.fromJson(deselectedColorsJson);

        return new ScoreStyle(
            type,
            fontSize,
            textStyles,
            fontFamilies,
            outlineBorder,
            spacing,
            selectedColors,
            deselectedColors
        );
    }

    @NonNull
    public ScoreType getType() {
        return type;
    }

    @Dimension(unit = Dimension.DP)
    public int getFontSize() {
        return fontSize;
    }

    @NonNull
    public List<TextStyle> getTextStyles() {
        return textStyles;
    }

    @NonNull
    public List<String> getFontFamilies() {
        return fontFamilies;
    }

    @NonNull
    public Border getOutlineBorder() {
        return outlineBorder;
    }

    @Dimension(unit = Dimension.DP)
    public int getSpacing() {
        return spacing;
    }

    @NonNull
    public ScoreColors getSelectedColors() {
        return selectedColors;
    }

    @NonNull
    public ScoreColors getDeselectedColors() {
        return deselectedColors;
    }

    public static class ScoreColors {
        @ColorInt
        private final int numberColor;
        @ColorInt
        @Nullable
        private final Integer fillColor;

        public ScoreColors(int numberColor, @Nullable Integer fillColor) {
            this.numberColor = numberColor;
            this.fillColor = fillColor;
        }

        @NonNull
        public static ScoreColors fromJson(JsonMap json) throws JsonException {
            @ColorInt
            Integer numberColor = Color.fromJsonField(json, "number");
            if (numberColor == null) {
                throw new JsonException("Failed to parse score style colors! Field 'number' may not be null.");
            }
            @ColorInt
            Integer fillColor = Color.fromJsonField(json, "fill");

            return new ScoreColors(numberColor, fillColor);
        }

        public int getNumberColor() {
            return numberColor;
        }

        @Nullable
        public Integer getFillColor() {
            return fillColor;
        }
    }
}
