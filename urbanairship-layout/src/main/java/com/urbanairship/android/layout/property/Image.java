/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.content.Context;
import android.graphics.drawable.Drawable;
import com.urbanairship.UALog;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.widget.ShapeDrawableWrapper;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import java.util.Locale;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

public abstract class Image {
    @NonNull
    private final Type type;

    private Image(@NonNull Type type) {
        this.type = type;
    }

    @NonNull
    public static Image fromJson(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();
        switch (Type.from(typeString)) {
            case URL:
                return Url.fromJson(json);
            case ICON:
                return Icon.fromJson(json);
        }
        throw new JsonException("Failed to parse image! Unknown button image type value: " + typeString);
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
        public static Type from(@NonNull String value) throws JsonException {
            for (Type type : Type.values()) {
                if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                    return type;
                }
            }
            throw new JsonException("Unknown button image type value: " + value);
        }
    }

    @NonNull
    public Type getType() {
        return type;
    }

    public static final class Url extends Image {
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

    public static final class Icon extends Image {
        @NonNull
        private final DrawableResource drawable;
        @NonNull
        private final Color tint;
        private final float scale;

        public Icon(@NonNull DrawableResource drawable, @NonNull Color tint, float scale) {
            super(Type.ICON);
            this.drawable = drawable;
            this.tint = tint;
            this.scale = scale;
        }

        @NonNull
        public static Icon fromJson(@NonNull JsonMap json) throws JsonException {
            String iconString = json.opt( "icon").optString();
            DrawableResource icon = DrawableResource.from(iconString);
            Color tint = Color.fromJsonField(json, "color");
            if (tint == null) {
                throw new JsonException("Failed to parse icon! Field 'color' is required.");
            }
            float scale = json.opt("scale").getFloat(1);

            return new Icon(icon, tint, scale);
        }

        @DrawableRes
        public int getDrawableRes() {
            return drawable.resId;
        }

        @NonNull
        public String getContentDescription(Context context, Icon icon) {
            switch (icon.drawable.value) {
                case "close":
                    return context.getString(com.urbanairship.R.string.ua_icon_button_close);
                case "checkmark":
                    return context.getString(com.urbanairship.R.string.ua_icon_button_checkmark);
                case "forward_arrow":
                    return context.getString(com.urbanairship.R.string.ua_icon_button_forward_arrow);
                case "backward_arrow":
                    return context.getString(com.urbanairship.R.string.ua_icon_button_backward_arrow);
                default:
                    UALog.w("Unknown Icon drawable");
                    return "";
            }
        }

        public Drawable getDrawable(@NonNull Context context) {
            return getDrawable(context, true);
        }

        @Nullable
        public Drawable getDrawable(@NonNull Context context, boolean enabledState) {
            Drawable d = ContextCompat.getDrawable(context, getDrawableRes());
            if (d != null) {
                DrawableCompat.setTint(d, enabledState ? tint.resolve(context) : LayoutUtils.generateDisabledColor(tint.resolve(context)));
                return new ShapeDrawableWrapper(d, 1, scale);
            } else {
                return null;
            }
        }

        @NonNull
        public Color getTint() {
            return tint;
        }

        public float getScale() {
            return scale;
        }

        private enum DrawableResource {
            CLOSE("close", R.drawable.ua_layout_ic_close),
            CHECKMARK("checkmark", R.drawable.ua_layout_ic_check),
            ARROW_FORWARD("forward_arrow", R.drawable.ua_layout_ic_arrow_forward),
            ARROW_BACK("back_arrow", R.drawable.ua_layout_ic_arrow_back);

            @NonNull
            private final String value;
            @DrawableRes
            private final int resId;

            DrawableResource(@NonNull String value, int resId) {
                this.value = value;
                this.resId = resId;
            }

            @NonNull
            public String getValue() {
                return value;
            }

            @NonNull
            private static DrawableResource from(String value) throws JsonException {
                for (DrawableResource res : DrawableResource.values()) {
                    if (res.value.equals(value.toLowerCase(Locale.ROOT))) {
                        return res;
                    }
                }
                throw new JsonException("Unknown icon drawable resource: " + value);
            }
        }
    }
}
