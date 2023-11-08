/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import static com.urbanairship.android.layout.model.SafeAreaAware.ignoreSafeAreaFromJson;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.android.layout.model.SafeAreaAware;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

public class BannerPlacement implements SafeAreaAware {
    @NonNull
    private final ConstrainedSize size;
    @Nullable
    private final Margin margin;
    @Nullable
    private final Position position;
    private final boolean ignoreSafeArea;

    @Nullable
    private final Border border;

    @Nullable
    private final Color backgroundColor;

    public BannerPlacement(
        @NonNull ConstrainedSize size,
        @Nullable Margin margin,
        @Nullable Position position,
        boolean ignoreSafeArea,
        @Nullable Border border,
        @Nullable Color backgroundColor
    ) {
        this.size = size;
        this.margin = margin;
        this.position = position;
        this.ignoreSafeArea = ignoreSafeArea;
        this.border = border;
        this.backgroundColor = backgroundColor;
    }

    @NonNull
    public static BannerPlacement fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap sizeJson = json.opt("size").optMap();
        if (sizeJson.isEmpty()) {
            throw new JsonException("Failed to parse Modal Placement! Field 'size' is required.");
        }
        String positionJson = json.opt("position").optString();
        JsonMap marginJson = json.opt("margin").optMap();
        JsonMap borderJson = json.opt("border").optMap();
        JsonMap backgroundJson = json.opt("background_color").optMap();

        ConstrainedSize size = ConstrainedSize.fromJson(sizeJson);
        Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);

        VerticalPosition verticalPosition = VerticalPosition.from(positionJson);
        Position position = new Position(HorizontalPosition.CENTER, verticalPosition);

        boolean ignoreSafeArea = ignoreSafeAreaFromJson(json);

        Border border = borderJson.isEmpty() ? null : Border.fromJson(borderJson);
        Color backgroundColor = backgroundJson.isEmpty() ? null : Color.fromJson(backgroundJson);

        return new BannerPlacement(size, margin, position, ignoreSafeArea, border, backgroundColor);
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

    @Nullable
    public Border getBorder() {
        return border;
    }

    @Nullable
    public Color getBackgroundColor() {
        return backgroundColor;
    }

}
