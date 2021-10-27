/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import android.graphics.Color;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CheckboxInputModel extends InputModel {
    @NonNull
    private final String identifier;
    @NonNull
    private final LabelModel label;
    @Nullable
    private final Border border;
    @Nullable
    @ColorInt
    private final Integer backgroundColor;

    public CheckboxInputModel(
        @NonNull String identifier,
        @NonNull LabelModel label,
        @Nullable Border border,
        @Nullable @ColorInt Integer backgroundColor
    ) {
        super(ViewType.CHECKBOX_INPUT);

        this.identifier = identifier;
        this.label = label;
        this.border = border;
        this.backgroundColor = backgroundColor;
    }

    @NonNull
    public static CheckboxInputModel fromJson(@NonNull JsonMap json) throws JsonException {
        String identifier = json.opt("identifier").optString();
        JsonMap labelJson = json.opt("label").optMap();
        JsonMap borderJson = json.opt("border").optMap();
        String colorString = json.opt("backgroundColor").optString();

        LabelModel label = LabelModel.fromJson(labelJson);
        Border border = borderJson.isEmpty() ? null : Border.fromJson(borderJson);
        @ColorInt Integer backgroundColor = colorString.isEmpty() ? null : Color.parseColor(colorString);

        return new CheckboxInputModel(identifier, label, border, backgroundColor);
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public LabelModel getLabel() {
        return label;
    }

    @Nullable
    public Border getBorder() {
        return border;
    }

    @Nullable
    @ColorInt
    public Integer getBackgroundColor() {
        return backgroundColor;
    }
}
