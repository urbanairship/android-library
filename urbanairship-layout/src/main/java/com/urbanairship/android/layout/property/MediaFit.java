/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.widget.ImageView;

import java.util.Locale;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonException;

/**
 * Property that determines how an image should be scaled in an {@code ImageView}.
 */
public enum MediaFit {
    CENTER("center", ImageView.ScaleType.CENTER),
    CENTER_INSIDE("center_inside", ImageView.ScaleType.FIT_CENTER),
    CENTER_CROP("center_crop", ImageView.ScaleType.CENTER_CROP);

    @NonNull
    private final String value;
    @NonNull
    private final ImageView.ScaleType scaleType;

    MediaFit(@NonNull String value, @NonNull ImageView.ScaleType scaleType) {
        this.value = value;
        this.scaleType = scaleType;
    }

    @NonNull
    public static MediaFit from(@NonNull String value) throws JsonException {
        for (MediaFit type : MediaFit.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown MediaFit value: " + value);
    }

    @NonNull
    public static ImageView.ScaleType asScaleType(@NonNull String mediaFit) throws JsonException {
        return MediaFit.from(mediaFit).getScaleType();
    }

    @NonNull
    public ImageView.ScaleType getScaleType() {
        return scaleType;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
