/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import static android.graphics.Color.parseColor;

public final class Color {

    private Color() {}

    @Nullable
    @ColorInt
    public static Integer fromJson(@Nullable JsonMap json) {
        if (json == null || json.isEmpty()) {
            Logger.verbose("Ignoring json Color. Color is null or empty");
            return null;
        }

        String hex = json.opt("hex").optString();
        float alpha = json.opt("alpha").getFloat(1f);

        if (hex.isEmpty() || alpha > 1f || alpha < 0) {
            Logger.warn("Invalid Color json: %s", json.toString());
            return null;
        }

        int color = parseColor(hex);
        if (alpha != 1f) {
            color = ColorUtils.setAlphaComponent(color, (int)(alpha * 255));
        }

        return color;
    }

    @Nullable
    @ColorInt
    public static Integer fromJsonField(JsonMap json, String fieldName) {
        if (json == null || json.isEmpty()) {
            Logger.verbose("Ignoring json Color from field! Map is null or empty.");
            return null;
        }
        JsonMap colorJson = json.opt(fieldName).optMap();
        return fromJson(colorJson);
    }
}
