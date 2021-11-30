/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.TextAppearance;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LabelModel extends BaseModel implements Accessible {
    @NonNull
    private final String text;
    @NonNull
    private final TextAppearance textAppearance;
    @Nullable
    private final String contentDescription;

    public LabelModel(
        @NonNull String text,
        @NonNull TextAppearance textAppearance,
        @Nullable String contentDescription,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.LABEL, backgroundColor, border);

        this.text = text;
        this.textAppearance = textAppearance;
        this.contentDescription = contentDescription;
    }

    @NonNull
    public static LabelModel fromJson(@NonNull JsonMap json) throws JsonException {
        String text = json.opt("text").optString();
        JsonMap textAppearanceJson = json.opt("text_appearance").optMap();
        TextAppearance textAppearance = TextAppearance.fromJson(textAppearanceJson);
        String contentDescription = Accessible.contentDescriptionFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new LabelModel(
            text,
            textAppearance,
            contentDescription,
            backgroundColor,
            border
        );
    }

    @NonNull
    public String getText() {
        return text;
    }

    @NonNull
    public TextAppearance getTextAppearance() {
        return textAppearance;
    }

    @Nullable
    @Override
    public String getContentDescription() {
        return contentDescription;
    }
}
