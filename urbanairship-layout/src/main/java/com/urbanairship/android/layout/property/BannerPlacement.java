/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BannerPlacement {
    @NonNull
    private final ConstrainedSize size;
    @Nullable
    private final Margin margin;
    @Nullable
    private final Position position;

    public BannerPlacement(
        @NonNull ConstrainedSize size,
        @Nullable Margin margin,
        @Nullable Position position
    ) {
        this.size = size;
        this.margin = margin;
        this.position = position;
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

        return new BannerPlacement(size, margin, position);
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
}
