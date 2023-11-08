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

    @Nullable
    private final Border border;

    @Nullable
    private final Color backgroundColor;

    public ModalPlacement(
            @NonNull ConstrainedSize size,
            @Nullable Margin margin,
            @Nullable Position position,
            @Nullable Color shadeColor,
            boolean ignoreSafeArea,
            @Nullable Orientation orientationLock,
            @Nullable Border border,
            @Nullable Color backgroundColor
    ) {
        this.size = size;
        this.margin = margin;
        this.position = position;
        this.shadeColor = shadeColor;
        this.ignoreSafeArea = ignoreSafeArea;
        this.orientationLock = orientationLock;
        this.border = border;
        this.backgroundColor = backgroundColor;
    }

    @NonNull
    public static ModalPlacement fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap sizeJson = json.opt("size").optMap();
        if (sizeJson.isEmpty()) {
            throw new JsonException("Failed to parse Modal Placement! Field 'size' is required.");
        }
        JsonMap positionJson = json.opt("position").optMap();
        JsonMap marginJson = json.opt("margin").optMap();
        JsonMap borderJson = json.opt("border").optMap();
        JsonMap backgroundJson = json.opt("background_color").optMap();

        ConstrainedSize size = ConstrainedSize.fromJson(sizeJson);
        Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);
        Position position = positionJson.isEmpty() ? null : Position.fromJson(positionJson);
        Color shadeColor = Color.fromJsonField(json, "shade_color");
        boolean ignoreSafeArea = ignoreSafeAreaFromJson(json);

        String orientationString = json.opt("device").optMap().opt("lock_orientation").optString();
        Orientation orientationLock = orientationString.isEmpty() ? null : Orientation.from(orientationString);

        Border border = borderJson.isEmpty() ? null : Border.fromJson(borderJson);
        Color backgroundColor = backgroundJson.isEmpty() ? null : Color.fromJson(backgroundJson);

        return new ModalPlacement(size, margin, position, shadeColor, ignoreSafeArea, orientationLock, border, backgroundColor);
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

    @Nullable
    public Border getBorder() {
        return border;
    }

    @Nullable
    public Color getBackgroundColor() {
        return backgroundColor;
    }
}
