/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.TextAlignment;
import com.urbanairship.android.layout.property.TextStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LabelModel extends BaseModel implements Accessible {
    @NonNull
    private final String text;
    @NonNull
    private final Integer fontSize;
    @ColorInt
    private final int foregroundColor;
    @Nullable
    private final TextAlignment alignment;
    @NonNull
    private final List<TextStyle> textStyles;
    @NonNull
    private final List<String> fontFamilies;
    @Nullable
    private final String contentDescription;

    public LabelModel(
        @NonNull String text,
        @NonNull Integer fontSize,
        @ColorInt int foregroundColor,
        @Nullable TextAlignment alignment,
        @NonNull List<TextStyle> textStyles,
        @NonNull List<String> fontFamilies,
        @Nullable String contentDescription,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.LABEL, backgroundColor, border);

        this.text = text;
        this.fontSize = fontSize;
        this.foregroundColor = foregroundColor;
        this.alignment = alignment;
        this.textStyles = textStyles;
        this.fontFamilies = fontFamilies;
        this.contentDescription = contentDescription;
    }

    @NonNull
    public static LabelModel fromJson(@NonNull JsonMap json) throws JsonException {
        String text = json.opt("text").optString();
        int fontSize = json.opt("font_size").getInt(14);
        @ColorInt Integer foregroundColor = Color.fromJsonField(json, "foreground_color");
        if (foregroundColor == null) {
            throw new JsonException("Failed to parse label. 'foreground_color' may not be null! json = '" + json + "'");
        }
        String alignmentString = json.opt("alignment").optString();
        JsonList textStylesJson = json.opt("text_styles").optList();
        JsonList fontFamiliesJson = json.opt("font_families").optList();
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

        String contentDescription = Accessible.contentDescriptionFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new LabelModel(
            text,
            fontSize,
            foregroundColor,
            alignment,
            textStyles,
            fontFamilies,
            contentDescription,
            backgroundColor,
            border
        );
    }

    @NonNull
    public String getText() {
        return text;
    }

    @Dimension(unit = Dimension.DP)
    public int getFontSize() {
        return fontSize;
    }

    @ColorInt
    public int getForegroundColor() {
        return foregroundColor;
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

    @Nullable
    @Override
    public String getContentDescription() {
        return contentDescription;
    }
}
