/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.FormInputType;
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

import static androidx.annotation.Dimension.DP;

public class TextInputModel extends BaseModel implements Identifiable, Accessible, Validatable {
    @NonNull
    private final String identifier;
    @NonNull
    private final FormInputType inputType;
    @Dimension(unit = DP)
    private final int fontSize;
    @ColorInt
    private final int foregroundColor;
    @Nullable
    private final String hintText;
    @NonNull
    private final List<TextStyle> textStyles;
    @NonNull
    private final List<String> fontFamilies;
    @Nullable
    private final String contentDescription;
    @Nullable
    private final Boolean isRequired;

    public TextInputModel(
        @NonNull String identifier,
        @NonNull FormInputType inputType,
        @Dimension(unit = DP) int fontSize,
        @ColorInt int foregroundColor,
        @Nullable String hintText,
        @NonNull List<TextStyle> textStyles,
        @NonNull List<String> fontFamilies,
        @Nullable String contentDescription,
        @Nullable Boolean isRequired,
        @Nullable @ColorInt Integer backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.TEXT_INPUT, backgroundColor, border);

        this.identifier = identifier;
        this.inputType = inputType;
        this.fontSize = fontSize;
        this.foregroundColor = foregroundColor;
        this.hintText = hintText;
        this.textStyles = textStyles;
        this.fontFamilies = fontFamilies;
        this.contentDescription = contentDescription;
        this.isRequired = isRequired;
    }

    @NonNull
    public static TextInputModel fromJson(@NonNull JsonMap json) throws JsonException {
        String inputTypeString = json.opt("input_type").optString();
        FormInputType inputType = FormInputType.from(inputTypeString);
        int fontSize = json.opt("font_size").getInt(14);
        @ColorInt Integer foregroundColor = Color.fromJsonField(json, "foreground_color");
        if (foregroundColor == null) {
            throw new JsonException("Failed to parse TextInput. 'foreground_color' may not be null!");
        }
        String placeholder = json.opt("place_holder").getString();
        JsonList textStylesJson = json.opt("text_styles").optList();
        JsonList fontFamiliesJson = json.opt("font_families").optList();

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

        String identifier = Identifiable.identifierFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);
        Boolean required = Validatable.requiredFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new TextInputModel(
            identifier,
            inputType,
            fontSize,
            foregroundColor,
            placeholder,
            textStyles,
            fontFamilies,
            contentDescription,
            required,
            backgroundColor,
            border
        );
    }

    @NonNull
    public BaseModel getView() {
        return this;
    }

    @Override
    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @Override
    @Nullable
    public String getContentDescription() {
        return contentDescription;
    }

    @Override
    @Nullable
    public Boolean isRequired() {
        return isRequired;
    }

    @ColorInt
    public int getForegroundColor() {
        return foregroundColor;
    }

    @NonNull
    public FormInputType getInputType() {
        return inputType;
    }

    public int getFontSize() {
        return fontSize;
    }

    @Nullable
    public String getHintText() {
        return hintText;
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
