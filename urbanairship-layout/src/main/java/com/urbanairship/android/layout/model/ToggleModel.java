/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ToggleStyle;
import com.urbanairship.android.layout.property.ToggleType;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Toggle input for use within a {@code FormController} or {@code NpsFormController}.
 */
public class ToggleModel extends BaseModel implements Identifiable, Accessible, Validatable {
    @NonNull
    private final String identifier;
    @NonNull
    private final ToggleStyle toggleStyle;
    @Nullable
    private final String contentDescription;
    @Nullable
    private final Boolean isRequired;

    public ToggleModel(
        @NonNull String identifier,
        @NonNull ToggleStyle toggleStyle,
        @Nullable String contentDescription,
        @Nullable Boolean isRequired,
        @Nullable Integer backgroundColor,
        @Nullable Border border
    ) {
        super(ViewType.TOGGLE, backgroundColor, border);

        this.identifier = identifier;
        this.toggleStyle = toggleStyle;
        this.contentDescription = contentDescription;
        this.isRequired = isRequired;
    }

    @NonNull
    public static ToggleModel fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap toggleStyleJson = json.opt("toggle_style").optMap();
        ToggleStyle toggleStyle = ToggleStyle.fromJson(toggleStyleJson);
        String identifier = Identifiable.identifierFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);
        Boolean required = Validatable.requiredFromJson(json);
        @ColorInt Integer backgroundColor = backgroundColorFromJson(json);
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
    @Nullable
    public String getContentDescription() {
        return contentDescription;
    }

    @Override
    @Nullable
    public Boolean isRequired() {
        return isRequired;
    }

    @NonNull
    public ToggleStyle getToggleStyle() {
        return toggleStyle;
    }

    @NonNull
    public ToggleType getToggleType() {
        return toggleStyle.getType();
    }
}
