/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.android.layout.model.SafeAreaAware;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.model.SafeAreaAware.ignoreSafeAreaFromJson;

public class ModalPlacement implements SafeAreaAware {

    @NonNull
    private final ConstrainedSize size;
    @Nullable
    private final Margin margin;
    @Nullable
    private final Position position;
    @Nullable
    private final Color shadeColor;
    private final boolean ignoreSafeArea;

    @Nullable
    private final Orientation orientationLock;

    public ModalPlacement(
            @NonNull ConstrainedSize size,
            @Nullable Margin margin,
            @Nullable Position position,
            @Nullable Color shadeColor,
            boolean ignoreSafeArea,
            @Nullable Orientation orientationLock
    ) {
        this.size = size;
        this.margin = margin;
        this.position = position;
        this.shadeColor = shadeColor;
        this.ignoreSafeArea = ignoreSafeArea;
        this.orientationLock = orientationLock;
    }

    @NonNull
    public static ModalPlacement fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap sizeJson = json.opt("size").optMap();
        if (sizeJson.isEmpty()) {
            throw new JsonException("Failed to parse Modal Placement! Field 'size' is required.");
        }
        JsonMap positionJson = json.opt("position").optMap();
        JsonMap marginJson = json.opt("margin").optMap();

        ConstrainedSize size = ConstrainedSize.fromJson(sizeJson);
        Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);
        Position position = positionJson.isEmpty() ? null : Position.fromJson(positionJson);
        Color backgroundColor = Color.fromJsonField(json, "shade_color");
        boolean ignoreSafeArea = ignoreSafeAreaFromJson(json);

        String orientationString = json.opt("device").optMap().opt("lock_orientation").optString();
        Orientation orientationLock = orientationString.isEmpty() ? null : Orientation.from(orientationString);

        return new ModalPlacement(size, margin, position, backgroundColor, ignoreSafeArea, orientationLock);
    }

    @Nullable
    public Orientation getOrientationLock() {
        return orientationLock;
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

    @Nullable
    public Color getShadeColor() {
        return shadeColor;
    }

    @Override
    public boolean shouldIgnoreSafeArea() {
        return ignoreSafeArea;
    }
}
