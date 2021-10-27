/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.view.View;
import android.view.ViewGroup;

import com.urbanairship.android.layout.util.PercentUtils;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public class Size {
    @NonNull
    private static final String SIZE_AUTO = "auto";
    @NonNull
    public static final Size AUTO = new Size(SIZE_AUTO, SIZE_AUTO);

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

    public void applyTo(@NonNull View view, @NonNull ViewGroup.LayoutParams layoutParams) {
        int width = 0;
        int height = 0;

        Dimension w = getWidth();
        switch (w.getType()) {
            case AUTO:
                width = ViewGroup.LayoutParams.WRAP_CONTENT;
                break;
            case PERCENT:
                if (w.getFloat() == 1) {
                    width = ViewGroup.LayoutParams.MATCH_PARENT;
                } else {
                    // TODO: make this work... or don't... this method is only used by Scroll Layout
                    //  and it may be fine to assume all scroll children will be either MATCH x WRAP
                    //  or WRAP x MATCH depending on the scroll direction?
                    width = ViewGroup.LayoutParams.MATCH_PARENT;
                }
                break;
            case ABSOLUTE:
                width = (int) ResourceUtils.dpToPx(view.getContext(), w.getInt());
                break;
        }

        Dimension h = getHeight();
        switch (h.getType()) {
            case AUTO:
                height = ViewGroup.LayoutParams.WRAP_CONTENT;
                break;
            case PERCENT:
                if (h.getFloat() == 1) {
                    height = ViewGroup.LayoutParams.MATCH_PARENT;
                } else {
                    // TODO: make this work... (see above, tho!)
                    height = ViewGroup.LayoutParams.MATCH_PARENT;
                }
                break;
            case ABSOLUTE:
                height = (int) ResourceUtils.dpToPx(view.getContext(), h.getInt());
                break;
        }

        layoutParams.width = width;
        layoutParams.height = height;
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
