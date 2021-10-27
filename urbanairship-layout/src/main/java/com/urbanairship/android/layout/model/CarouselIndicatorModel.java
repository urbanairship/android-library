/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public class CarouselIndicatorModel extends BaseModel {
    @NonNull
    private final String identifier;
    @NonNull
    private final String currentCardId; // TODO: what to do with this?

    public CarouselIndicatorModel(@NonNull String identifier, @NonNull String currentCardId) {
        super(ViewType.CAROUSEL_INDICATOR);

        this.identifier = identifier;
        this.currentCardId = currentCardId;
    }

    @NonNull
    public static CarouselIndicatorModel fromJson(@NonNull JsonMap json) {
        String identifier = json.opt("carouselIdentifier").optString();
        String currentCardId = json.opt("currentCardId").optString();

        return new CarouselIndicatorModel(identifier, currentCardId);
    }

    @NonNull
    public String getIdentifier() {
        return identifier;
    }

    @NonNull
    public String getCurrentCardId() {
        return currentCardId;
    }
}
