/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.display;

import com.urbanairship.android.layout.model.LayoutModel;
import com.urbanairship.android.layout.property.BannerPosition;
import com.urbanairship.android.layout.property.Margin;
import com.urbanairship.android.layout.property.Size;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BannerDisplay implements Display {
    @NonNull
    private final Info info;
    @NonNull
    private final LayoutModel layout;

    @NonNull
    public static BannerDisplay fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap bannerJson = json.opt("banner").optMap();
        JsonMap layoutJson = json.opt("layout").optMap();

        Info info = Info.fromJson(bannerJson);
        LayoutModel layout = LayoutModel.fromJson(layoutJson);

        return new BannerDisplay(info, layout);
    }

    public BannerDisplay(@NonNull Info info, @NonNull LayoutModel layout) {
        this.layout = layout;
        this.info = info;
    }

    @NonNull
    public Info getInfo() {
        return info;
    }

    @NonNull
    public LayoutModel getLayout() {
        return layout;
    }

    public static class Info {
        private final int duration;
        @NonNull
        private final BannerPosition position;
        @Nullable
        private final Margin margin;
        @Nullable
        private final Size size;

        Info(int duration, @NonNull BannerPosition position, @Nullable Margin margin, @Nullable Size size) {
            this.duration = duration;
            this.position = position;
            this.margin = margin;
            this.size = size;
        }

        @NonNull
        public static Info fromJson(@NonNull JsonMap json) throws JsonException {
            int duration = json.opt("duration").getInt(0);
            String positionString = json.opt("position").optString();
            JsonMap marginJson = json.opt("margin").optMap();
            JsonMap sizeJson = json.opt("size").optMap();

            BannerPosition position = BannerPosition.from(positionString);
            Margin margin = marginJson.isEmpty() ? null : Margin.fromJson(marginJson);
            Size size = sizeJson.isEmpty() ? null : Size.fromJson(sizeJson);

            return new Info(duration, position, margin, size);
        }

        public int getDuration() {
            return duration;
        }

        @NonNull
        public BannerPosition getPosition() {
            return position;
        }

        @Nullable
        public Margin getMargin() {
            return margin;
        }

        @Nullable
        public Size getSize() {
            return size;
        }
    }
}
