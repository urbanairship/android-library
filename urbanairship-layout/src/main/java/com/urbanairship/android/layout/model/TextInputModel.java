/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.TextInputEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.FormInputType;
import com.urbanairship.android.layout.property.TextInputTextAppearance;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.android.layout.reporting.LayoutData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class TextInputModel extends BaseModel implements Identifiable, Accessible, Validatable {
    @NonNull
    private final String identifier;
    @NonNull
    private final FormInputType inputType;
    @NonNull
    private final TextInputTextAppearance textAppearance;
    @Nullable
    private final String hintText;
    @Nullable
    private final String contentDescription;
    private final boolean isRequired;

    @Nullable
    private String value = null;

    public TextInputModel(
        @NonNull String identifier,
        @NonNull FormInputType inputType,
        @NonNull TextInputTextAppearance textAppearance,
        @Nullable String hintText,
        @Nullable String contentDescription,
        boolean isRequired,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.TEXT_INPUT, backgroundColor, border);

        this.identifier = identifier;
        this.inputType = inputType;
        this.textAppearance = textAppearance;
        this.hintText = hintText;
        this.contentDescription = contentDescription;
        this.isRequired = isRequired;
    }

    @NonNull
    public static TextInputModel fromJson(@NonNull JsonMap json) throws JsonException {
        String inputTypeString = json.opt("input_type").optString();
        FormInputType inputType = FormInputType.from(inputTypeString);
        String hintText = json.opt("place_holder").getString();
        Color hintColor = Color.fromJsonField(json, "place_holder_text_color");
        JsonMap textAppearanceJson = json.opt("text_appearance").optMap();
        TextInputTextAppearance textAppearance = TextInputTextAppearance.fromJson(textAppearanceJson);
        String identifier = Identifiable.identifierFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);
        boolean required = Validatable.requiredFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new TextInputModel(
            identifier,
            inputType,
            textAppearance,
            hintText,
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
    public boolean isRequired() {
        return isRequired;
    }

    @Override
    public boolean isValid() {
        return !isRequired || !UAStringUtil.isEmpty(value);
    }

    @NonNull
    public FormInputType getInputType() {
        return inputType;
    }

    @NonNull
    public TextInputTextAppearance getTextAppearance() {
        return textAppearance;
    }

    @Nullable
    public String getHintText() {
        return hintText;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    public void onConfigured() {
        bubbleEvent(new TextInputEvent.Init(identifier, isValid()), LayoutData.empty());
    }

    public void onAttachedToWindow() {
        bubbleEvent(new Event.ViewAttachedToWindow(this), LayoutData.empty());
    }

    public void onInputChange(@NonNull String value) {
        this.value = value;
        bubbleEvent(new FormEvent.DataChange(new FormData.TextInput(identifier, value), isValid()), LayoutData.empty());
    }
}
