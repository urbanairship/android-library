/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public abstract class InputModel extends BaseModel {
    public InputModel(@NonNull ViewType viewType) {
        super(viewType);
    }

    @NonNull
    public static InputModel fromJson(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();

        switch (ViewType.from(typeString)) {
            case CHECKBOX_INPUT:
                return CheckboxInputModel.fromJson(json);
            case RADIO_INPUT:
                return RadioInputModel.fromJson(json);
            case TEXT_INPUT:
                return TextInputModel.fromJson(json);
        }

        throw new JsonException("Error inflating layout! Unrecognized view type: " + typeString);
    }
}
