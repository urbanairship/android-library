/* Copyright Airship and Contributors */

package com.urbanairship.android.layout;

import android.content.Context;

import com.urbanairship.android.layout.property.ModalPlacement;
import com.urbanairship.android.layout.property.ModalPlacementSelector;
import com.urbanairship.android.layout.property.Orientation;
import com.urbanairship.android.layout.property.PresentationType;
import com.urbanairship.android.layout.property.WindowSize;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ModalPresentation extends BasePresentation {

    @NonNull
    private final ModalPlacement defaultPlacement;
    @Nullable
    private final List<ModalPlacementSelector> placementSelectors;
    private final boolean dismissOnTouchOutside;

    public ModalPresentation(
        @NonNull ModalPlacement defaultPlacement,
        @Nullable List<ModalPlacementSelector> placementSelectors,
        boolean dismissOnTouchOutside
    ) {
        super(PresentationType.MODAL);

        this.defaultPlacement = defaultPlacement;
        this.placementSelectors = placementSelectors;
        this.dismissOnTouchOutside = dismissOnTouchOutside;
    }

    @NonNull
    public static ModalPresentation fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap defaultPlacementJson = json.opt("default_placement").optMap();
        if (defaultPlacementJson.isEmpty()) {
            throw new JsonException("Failed to parse ModalPresentation! Field 'default_placement' is required.");
        }
        JsonList placementSelectorsJson = json.opt("placement_selectors").optList();
        ModalPlacement defaultPlacement = ModalPlacement.fromJson(defaultPlacementJson);
        List<ModalPlacementSelector> placementSelectors =
            placementSelectorsJson.isEmpty() ? null : ModalPlacementSelector.fromJsonList(placementSelectorsJson);

        boolean dismissOnTouchOutside = json.opt("dismiss_on_touch_outside").getBoolean(false);

        return new ModalPresentation(defaultPlacement, placementSelectors, dismissOnTouchOutside);
    }

    @NonNull
    public ModalPlacement getDefaultPlacement() {
        return defaultPlacement;
    }

    @Nullable
    public List<ModalPlacementSelector> getPlacementSelectors() {
        return placementSelectors;
    }

    public boolean isDismissOnTouchOutside() {
        return dismissOnTouchOutside;
    }

    @NonNull
    public ModalPlacement getResolvedPlacement(@NonNull Context context) {
        if (placementSelectors == null || placementSelectors.isEmpty()) {
            return defaultPlacement;
        }

        Orientation orientation = ResourceUtils.getOrientation(context);
        WindowSize windowSize = ResourceUtils.getWindowSize(context);

        // Try to find a matching placement selector.
        for (ModalPlacementSelector selector : placementSelectors) {
            if (selector.getWindowSize() != null && selector.getWindowSize() != windowSize) {
                continue;
            }
            if (selector.getOrientation() != null && selector.getOrientation() != orientation) {
                continue;
            }

            return selector.getPlacement();
        }

        // Otherwise, return the default placement.
        return defaultPlacement;
    }
}
