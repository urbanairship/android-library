/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.android.layout.util.PercentUtils;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.urbanairship.android.layout.property.ConstrainedSize.ConstrainedDimensionType.ABSOLUTE;
import static com.urbanairship.android.layout.property.ConstrainedSize.ConstrainedDimensionType.PERCENT;

public class ConstrainedSize extends Size {
    @Nullable
    private final ConstrainedDimension minWidth;
    @Nullable
    private final ConstrainedDimension minHeight;
    @Nullable
    private final ConstrainedDimension maxWidth;
    @Nullable
    private final ConstrainedDimension maxHeight;

    public ConstrainedSize(@NonNull String width, @NonNull String height,
                           @Nullable String minWidth, @Nullable String minHeight,
                           @Nullable String maxWidth, @Nullable String maxHeight) {
        super(width, height);
        this.minWidth = ConstrainedDimension.of(minWidth);
        this.minHeight = ConstrainedDimension.of(minHeight);
        this.maxWidth = ConstrainedDimension.of(maxWidth);
        this.maxHeight = ConstrainedDimension.of(maxHeight);
    }

    @NonNull
    public static ConstrainedSize fromJson(@NonNull JsonMap json) throws JsonException {
        String width = json.opt("width").coerceString();
        String height = json.opt("height").coerceString();
        if (width == null || height == null) {
            throw new JsonException("Size requires both width and height!");
        }

        String minWidth = json.opt("min_width").coerceString();
        String minHeight = json.opt("min_height").coerceString();
        String maxWidth = json.opt("max_width").coerceString();
        String maxHeight = json.opt("max_height").coerceString();

        return new ConstrainedSize(width, height, minWidth, minHeight, maxWidth, maxHeight);
    }

    @NonNull
    @Override
    public String toString() {
        return "ConstrainedSize { " +
            "width=" + getWidth() + ", height=" + getHeight() +
            ", minWidth=" + getMinWidth() + ", minHeight=" + getMinHeight() +
            ", maxWidth=" + getMaxWidth() + ", maxHeight=" + getMaxHeight() +
            " }";
    }

    @Nullable
    public ConstrainedDimension getMinWidth() {
        return minWidth;
    }

    @Nullable
    public ConstrainedDimension getMinHeight() {
        return minHeight;
    }

    @Nullable
    public ConstrainedDimension getMaxWidth() {
        return maxWidth;
    }

    @Nullable
    public ConstrainedDimension getMaxHeight() {
        return maxHeight;
    }

    public enum ConstrainedDimensionType {
        PERCENT,
        ABSOLUTE
    }

    public abstract static class ConstrainedDimension {
        @NonNull
        protected final String value;
        @NonNull
        private final ConstrainedDimensionType type;

        ConstrainedDimension(@NonNull String value, @NonNull ConstrainedDimensionType type) {
            this.value = value;
            this.type = type;
        }

        @Nullable
        public static ConstrainedDimension of(@Nullable String value) {
            if (value == null) {
                return null;
            } else if (PercentUtils.isPercent(value)) {
                return new PercentConstrainedDimension(value);
            } else {
                return new AbsoluteConstrainedDimension(value);
            }
        }

        public abstract float getFloat();

        public abstract int getInt();

        @NonNull
        public ConstrainedDimensionType getType() {
            return type;
        }

        public boolean isPercent() {
            return type == PERCENT;
        }

        public boolean isAbsolute() {
            return type == ABSOLUTE;
        }
    }

    public static class PercentConstrainedDimension extends ConstrainedDimension {
        PercentConstrainedDimension(@NonNull String value) {
            super(value, PERCENT);
        }

        @Override
        public float getFloat() {
            return PercentUtils.parse(value);
        }

        @Override
        public int getInt() {
            return (int) getFloat();
        }

        @NonNull
        @Override
        public String toString() {
            return (int) (getFloat() * 100) + "%";
        }
    }

    public static class AbsoluteConstrainedDimension extends ConstrainedDimension {
        AbsoluteConstrainedDimension(@NonNull String value) {
            super(value, ABSOLUTE);
        }

        @Override
        public float getFloat() {
            return Float.parseFloat(value);
        }

        @Override
        public int getInt() {
            return Integer.parseInt(value);
        }

        @NonNull
        @Override
        public String toString() {
            return getInt() + "dp";
        }
    }
}
