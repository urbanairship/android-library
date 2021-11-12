/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.android.layout.R;
import com.urbanairship.json.JsonMap;

import java.util.Locale;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import static java.util.Objects.requireNonNull;

public abstract class ButtonImage {
    @NonNull
    private final Type type;

    private ButtonImage(@NonNull Type type) {
        this.type = type;
    }

    @NonNull
    public static ButtonImage fromJson(@NonNull JsonMap json) {
        String typeString = json.opt("type").optString();
        switch (Type.from(typeString)) {
            case URL:
                return Url.fromJson(json);
            case ICON:
                return Icon.fromJson(json);
        }
        throw new IllegalArgumentException("Failed to parse button image! Unknown button image type value: " + typeString);
    }

    public enum Type {
        URL("url"),
        ICON("icon");

        @NonNull
        private final String value;

        Type(@NonNull String value) {
            this.value = value;
        }

        @NonNull
        public static Type from(@NonNull String value) {
            for (Type type : Type.values()) {
                if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown button image type value: " + value);
        }
    }

    @NonNull
    public Type getType() {
        return type;
    }

    public static final class Url extends ButtonImage {
        @NonNull private final String url;

        public Url(@NonNull String url) {
            super(Type.URL);
            this.url = url;
        }

        @NonNull
        public static Url fromJson(@NonNull JsonMap json) {
            String url = json.opt("url").optString();
            return new Url(url);
        }

        @NonNull
        public String getUrl() {
            return url;
        }
    }

    public static final class Icon extends ButtonImage {
        @NonNull private final DrawableResource drawable;
        @ColorInt private final int tint;

        public Icon(@NonNull DrawableResource drawable, int tint) {
            super(Type.ICON);
            this.drawable = drawable;
            this.tint = tint;
        }

        @NonNull
        public static Icon fromJson(@NonNull JsonMap json) {
            String iconString = json.opt( "icon").optString();
            DrawableResource icon = DrawableResource.from(iconString);
            @ColorInt int tint =
                requireNonNull(Color.fromJsonField(json, "tint"), "Failed to parse icon! Field 'tint' .");

            return new Icon(icon, tint);
        }

        @DrawableRes
        public int getDrawableRes() {
            return drawable.resId;
        }

        public int getTint() {
            return tint;
        }

        private enum DrawableResource {
            CLOSE("close", R.drawable.ua_ic_close_white);

            @NonNull
            private final String value;
            @DrawableRes
            private final int resId;

            DrawableResource(@NonNull String value, int resId) {
                this.value = value;
                this.resId = resId;
            }

            @NonNull
            private static DrawableResource from(String value) {
                for (DrawableResource res : DrawableResource.values()) {
                    if (res.value.equals(value.toLowerCase(Locale.ROOT))) {
                        return res;
                    }
                }
                throw new IllegalArgumentException("Unknown icon drawable resource: " + value);
            }
        }
    }
}
