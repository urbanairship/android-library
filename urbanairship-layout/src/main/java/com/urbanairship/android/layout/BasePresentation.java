/* Copyright Airship and Contributors */

package com.urbanairship.android.layout;

import com.urbanairship.android.layout.model.ModalPresentation;
import com.urbanairship.android.layout.property.PresentationType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public abstract class BasePresentation {
    @NonNull
    private final PresentationType type;

    public BasePresentation(@NonNull PresentationType type) {
        this.type = type;
    }

    @NonNull
    public static BasePresentation fromJson(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();
        switch (PresentationType.from(typeString)) {
            case BANNER:
                return BannerPresentation.fromJson(json);
            case MODAL:
                return ModalPresentation.fromJson(json);
        }
        throw new JsonException("Failed to parse presentation! Unknown type: " + typeString);
    }

    @NonNull
    public PresentationType getType() {
        return type;
    }
}
