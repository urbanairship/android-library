/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.android.layout.util.PercentUtils;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

// Note: If a parent defines `auto` for a dimension, children must have either `auto` or `points` for the same dimension
public class Size {
    @NonNull
    private static final String SIZE_AUTO = "auto";

    @NonNull
    private final Dimension width;
    @NonNull
    private final Dimension height;

    public Size(@NonNull String width, @NonNull String height) {
        this.width = Dimension.of(width);
        this.height = Dimension.of(height);
    }

    @NonNull
    public static Size fromJson(@NonNull JsonMap json) throws JsonException {
        String width = json.opt("width").coerceString();
        String height = json.opt("height").coerceString();

        if (width == null || height == null) {
            throw new JsonException("Size requires both width and height!");
        }

        return new Size(width, height);
    }

    @NonNull
    @Override
    public String toString() {
        return "Size { width=" + getWidth() + ", height=" + getHeight() + " }";
    }

    @NonNull
    public Dimension getWidth() {
        return width;
    }

    @NonNull
    public Dimension getHeight() {
        return height;
    }

    public enum DimensionType {
        AUTO,
        PERCENT,
        ABSOLUTE
    }

    public abstract static class Dimension {
        @NonNull
        protected final String value;
        @NonNull
        private final DimensionType type;

        Dimension(@NonNull String value, @NonNull DimensionType type) {
            this.value = value;
            this.type = type;
        }

        @NonNull
        public static Dimension of(@NonNull String value) {
            if (value.equals(SIZE_AUTO)) {
                return new AutoDimension();
            } else if (PercentUtils.isPercent(value)) {
                return new PercentDimension(value);
            } else {
                return new AbsoluteDimension(value);
            }
        }

        public abstract float getFloat();

        public abstract int getInt();

        @NonNull
        public DimensionType getType() {
            return type;
        }

        public boolean isAuto() {
            return type == DimensionType.AUTO;
        }

        public boolean isPercent() {
            return type == DimensionType.PERCENT;
        }

        public boolean isAbsolute() {
            return type == DimensionType.ABSOLUTE;
        }
    }

    public static class AutoDimension extends Dimension {
        AutoDimension() {
            super(SIZE_AUTO, DimensionType.AUTO);
        }

        @Override
        public float getFloat() {
            return -1f;
        }

        @Override
        public int getInt() {
            return -1;
        }

        @NonNull
        @Override
        public String toString() {
            return value;
        }
    }

    public static class PercentDimension extends Dimension {
        PercentDimension(@NonNull String value) {
            super(value, DimensionType.PERCENT);
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

    public static class AbsoluteDimension extends Dimension {
        AbsoluteDimension(@NonNull String value) {
            super(value, DimensionType.ABSOLUTE);
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
