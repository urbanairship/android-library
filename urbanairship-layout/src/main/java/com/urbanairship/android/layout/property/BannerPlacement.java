/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.android.layout.model.SafeAreaAware;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.SafeAreaAware.ignoreSafeAreaFromJson;

public class BannerPlacement implements SafeAreaAware {
    @NonNull
    private final ConstrainedSize size;
    @Nullable
    private final Margin margin;
    @Nullable
    private final Position position;
    private final boolean ignoreSafeArea;

    public BannerPlacement(
        @NonNull ConstrainedSize size,
        @Nullable Margin margin,
        @Nullable Position position,
        boolean ignoreSafeArea
    ) {
        this.size = size;
        this.margin = margin;
        this.position = position;
        this.ignoreSafeArea = ignoreSafeArea;
    }

    @NonNull
    public static BannerPlacement fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap sizeJson = json.opt("size").optMap();
        if (sizeJson.isEmpty()) {
            throw new JsonException("Failed to parse Modal Placement! Field 'size' is required.");
        }
        JsonMap positionJson = json.opt("position").optMap();
        JsonMap marginJson = json.opt("margin").optMap();

        ConstrainedSize size = ConstrainedSize.fromJson(sizeJson);
        Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);
        Position position = positionJson.isEmpty() ? null : Position.fromJson(positionJson);
        boolean ignoreSafeArea = ignoreSafeAreaFromJson(json);

        return new BannerPlacement(size, margin, position, ignoreSafeArea);
    }

    @Nullable
    public Margin getMargin() {
        return margin;
    }

    @NonNull
    public ConstrainedSize getSize() {
        return size;
    }

    @Nullable
    public Position getPosition() {
        return position;
    }

    @Override
    public boolean shouldIgnoreSafeArea() {
        return ignoreSafeArea;
    }
}
