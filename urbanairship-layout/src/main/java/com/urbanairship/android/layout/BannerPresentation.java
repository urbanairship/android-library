/* Copyright Airship and Contributors */

package com.urbanairship.android.layout;

import android.content.Context;

import com.urbanairship.android.layout.property.BannerPlacement;
import com.urbanairship.android.layout.property.BannerPlacementSelector;
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

public class BannerPresentation extends BasePresentation {
    private static final int DEFAULT_DURATION = 7000;

    @NonNull
    private final BannerPlacement defaultPlacement;
    private final int durationMs;
    @Nullable
    private final List<BannerPlacementSelector> placementSelectors;

    public BannerPresentation(
        @NonNull BannerPlacement defaultPlacement,
        int durationMs,
        @Nullable List<BannerPlacementSelector> placementSelectors
    ) {
        super(PresentationType.BANNER);

        this.defaultPlacement = defaultPlacement;
        this.durationMs = durationMs;
        this.placementSelectors = placementSelectors;
    }

    @NonNull
    public static BannerPresentation fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap defaultPlacementJson = json.opt("default_placement").optMap();
        if (defaultPlacementJson.isEmpty()) {
            throw new JsonException("Failed to parse BannerPresentation! Field 'default_placement' is required.");
        }
        int durationMs = json.opt("duration_milliseconds").getInt(DEFAULT_DURATION);
        JsonList placementSelectorsJson = json.opt("placement_selectors").optList();
        BannerPlacement defaultPlacement = BannerPlacement.fromJson(defaultPlacementJson);
        List<BannerPlacementSelector> placementSelectors =
            placementSelectorsJson.isEmpty() ? null : BannerPlacementSelector.fromJsonList(placementSelectorsJson);

        return new BannerPresentation(defaultPlacement, durationMs, placementSelectors);
    }

    @NonNull
    public BannerPlacement getDefaultPlacement() {
        return defaultPlacement;
    }

    public int getDurationMs() {
        return durationMs;
    }

    @Nullable
    public List<BannerPlacementSelector> getPlacementSelectors() {
        return placementSelectors;
    }

    @NonNull
    public BannerPlacement getResolvedPlacement(@NonNull Context context) {
        if (placementSelectors == null || placementSelectors.isEmpty()) {
            return defaultPlacement;
        }

        Orientation orientation = ResourceUtils.getOrientation(context);
        WindowSize windowSize = ResourceUtils.getWindowSize(context);

        // Try to find a matching placement selector.
        for (BannerPlacementSelector selector : placementSelectors) {
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
