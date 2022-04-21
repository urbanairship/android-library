/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TextInputTextAppearance extends TextAppearance {

    @Nullable
    private final Color hintColor;

    public TextInputTextAppearance(@NonNull TextAppearance textAppearance, @Nullable Color hintColor) {
        super(textAppearance);
        this.hintColor = hintColor;
    }

    @NonNull
    public static TextInputTextAppearance fromJson(@NonNull JsonMap json) throws JsonException {
        Color color = Color.fromJsonField(json, "place_holder_color");
        return new TextInputTextAppearance(TextAppearance.fromJson(json), color);
    }

    @Nullable
    public Color getHintColor() {
        return hintColor;
    }

}
