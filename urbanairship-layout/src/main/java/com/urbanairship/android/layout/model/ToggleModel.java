/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.ToggleEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.Accessible.contentDescriptionFromJson;
import static com.urbanairship.android.layout.model.Identifiable.identifierFromJson;
import static com.urbanairship.android.layout.model.Validatable.requiredFromJson;
import static com.urbanairship.android.layout.reporting.AttributeName.attributeNameFromJson;

/**
 * Toggle input for use within a {@code FormController} or {@code NpsFormController}.
 */
public class ToggleModel extends CheckableModel implements Identifiable, Validatable {
    @NonNull
    private final String identifier;
    private final boolean isRequired;
    @Nullable
    private final AttributeName attributeName;
    @Nullable
    private final JsonValue attributeValue;

    @Nullable
    private Boolean value = null;

    public ToggleModel(
        @NonNull String identifier,
        @NonNull ToggleStyle style,
        @Nullable AttributeName attributeName,
        @Nullable JsonValue attributeValue,
        @Nullable String contentDescription,
        boolean isRequired,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.TOGGLE, style, contentDescription, backgroundColor, border);

        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.identifier = identifier;
        this.isRequired = isRequired;
    }

    @NonNull
    public static ToggleModel fromJson(@NonNull JsonMap json) throws JsonException {
        ToggleStyle toggleStyle = toggleStyleFromJson(json);
        AttributeName attributeName = attributeNameFromJson(json);
        JsonValue attributeValue = json.opt("attribute_value");
        String identifier = identifierFromJson(json);
        String contentDescription = contentDescriptionFromJson(json);
        boolean required = requiredFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new ToggleModel(identifier, toggleStyle, attributeName, attributeValue, contentDescription, required, backgroundColor, border);
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
    public boolean isRequired() {
        return isRequired;
    }

    @Override
    public boolean isValid() {
        return Objects.equals(value, true) || !isRequired;
    }

    @NonNull
    @Override
    public Event buildInputChangeEvent(boolean isChecked) {
        return new FormEvent.DataChange(new FormData.Toggle(identifier, isChecked), isValid(), attributeName, attributeValue);
    }

    @NonNull
    @Override
    public Event buildInitEvent() {
        return new ToggleEvent.Init(identifier, isValid());
    }

    public void onCheckedChange(boolean isChecked) {
        this.value = isChecked;
        super.onCheckedChange(isChecked);
    }
}
