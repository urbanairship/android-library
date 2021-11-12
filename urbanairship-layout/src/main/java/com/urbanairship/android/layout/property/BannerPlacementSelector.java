/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BannerPlacementSelector {
    @NonNull
    private final BannerPlacement placement;
    @Nullable
    private final WindowSize windowSize;
    @Nullable
    private final Orientation orientation;

    public BannerPlacementSelector(
        @NonNull BannerPlacement placement,
        @Nullable WindowSize windowSize,
        @Nullable Orientation orientation) {
        this.placement = placement;
        this.windowSize = windowSize;
        this.orientation = orientation;
    }

    @NonNull
    public static BannerPlacementSelector fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap placementJson = json.opt("placement").optMap();
        String windowSizeString = json.opt("window_size").optString();
        String orientationString = json.opt("orientation").optString();

        BannerPlacement placement = BannerPlacement.fromJson(placementJson);
        WindowSize windowSize = windowSizeString.isEmpty() ? null : WindowSize.from(windowSizeString);
        Orientation orientation = orientationString.isEmpty() ? null : Orientation.from(orientationString);

        return new BannerPlacementSelector(placement, windowSize, orientation);
    }

    @NonNull
    public static List<BannerPlacementSelector> fromJsonList(@NonNull JsonList json) throws JsonException {
        List<BannerPlacementSelector> selectors = new ArrayList<>(json.size());
        for (int i = 0; i < json.size(); i++) {
            JsonMap selectorJson = json.get(i).optMap();
            BannerPlacementSelector selector = BannerPlacementSelector.fromJson(selectorJson);
            selectors.add(selector);
        }
        return selectors;
    }

    @NonNull
    public BannerPlacement getPlacement() {
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
