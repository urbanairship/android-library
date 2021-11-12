/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ModalPlacementSelector {
    @NonNull
    private final ModalPlacement placement;
    @Nullable
    private final WindowSize windowSize;
    @Nullable
    private final Orientation orientation;

    public ModalPlacementSelector(
        @NonNull ModalPlacement placement,
        @Nullable WindowSize windowSize,
        @Nullable Orientation orientation) {
        this.placement = placement;
        this.windowSize = windowSize;
        this.orientation = orientation;
    }

    @NonNull
    public static ModalPlacementSelector fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap placementJson = json.opt("placement").optMap();
        String windowSizeString = json.opt("window_size").optString();
        String orientationString = json.opt("orientation").optString();

        ModalPlacement placement = ModalPlacement.fromJson(placementJson);
        WindowSize windowSize = windowSizeString.isEmpty() ? null : WindowSize.from(windowSizeString);
        Orientation orientation = orientationString.isEmpty() ? null : Orientation.from(orientationString);

        return new ModalPlacementSelector(placement, windowSize, orientation);
    }

    @NonNull
    public static List<ModalPlacementSelector> fromJsonList(@NonNull JsonList json) throws JsonException {
        List<ModalPlacementSelector> selectors = new ArrayList<>(json.size());
        for (int i = 0; i < json.size(); i++) {
            JsonMap selectorJson = json.get(i).optMap();
            ModalPlacementSelector selector = ModalPlacementSelector.fromJson(selectorJson);
            selectors.add(selector);
        }
        return selectors;
    }

    @NonNull
    public ModalPlacement getPlacement() {
        return placement;
    }

    @Nullable
    public WindowSize getWindowSize() {
        return windowSize;
    }

    @Nullable
    public Orientation getOrientation() {
        return orientation;
    }
}
