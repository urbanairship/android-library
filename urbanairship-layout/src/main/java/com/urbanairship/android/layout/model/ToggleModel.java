/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.FormEvent;
import com.urbanairship.android.layout.event.ToggleEvent;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.android.layout.reporting.FormData;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.Accessible.contentDescriptionFromJson;
import static com.urbanairship.android.layout.model.Identifiable.identifierFromJson;
import static com.urbanairship.android.layout.model.Validatable.requiredFromJson;

/**
 * Toggle input for use within a {@code FormController} or {@code NpsFormController}.
 */
public class ToggleModel extends CheckableModel implements Identifiable, Validatable {
    @NonNull
    private final String identifier;
    private final boolean isRequired;

    @Nullable
    private Boolean value = null;

    public ToggleModel(
        @NonNull String identifier,
        @NonNull ToggleStyle style,
        @Nullable String contentDescription,
        boolean isRequired,
        @Nullable Color backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.TOGGLE, style, contentDescription, backgroundColor, border);

        this.identifier = identifier;
        this.isRequired = isRequired;
    }

    @NonNull
    public static ToggleModel fromJson(@NonNull JsonMap json) throws JsonException {
        ToggleStyle toggleStyle = toggleStyleFromJson(json);
        String identifier = identifierFromJson(json);
        String contentDescription = contentDescriptionFromJson(json);
        boolean required = requiredFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);

        return new ToggleModel(identifier, toggleStyle, contentDescription, required, backgroundColor, border);
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
        return value != null || !isRequired;
    }

    @NonNull
    @Override
    public Event buildInputChangeEvent(boolean isChecked) {
        return new FormEvent.DataChange(identifier, new FormData.Toggle(isChecked), isValid());
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
