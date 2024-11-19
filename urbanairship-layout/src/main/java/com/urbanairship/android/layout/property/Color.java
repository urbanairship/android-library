/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.content.Context;

import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Color {

    @ColorInt
    public static final int TRANSPARENT = android.graphics.Color.TRANSPARENT;
    @ColorInt
    public static final int WHITE = android.graphics.Color.WHITE;
    @ColorInt
    public static final int BLACK = android.graphics.Color.BLACK;

    private final int defaultColor;
    @NonNull
    private final List<ColorSelector> selectors;

    public Color(int defaultColor, @NonNull List<ColorSelector> selectors) {
        this.defaultColor = defaultColor;
        this.selectors = selectors;
    }

    @NonNull
    public static Color fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap defaultColorJson = json.opt("default").optMap();
        @ColorInt Integer defaultColor = HexColor.fromJson(defaultColorJson);
        if (defaultColor == null) {
            throw new JsonException("Failed to parse color. 'default' may not be null! json = " + json);
        }
        JsonList selectorsJson = json.opt("selectors").optList();
        List<ColorSelector> selectors = ColorSelector.fromJsonList(selectorsJson);

        return new Color(defaultColor, selectors);
    }

    @Nullable
    public static Color fromJsonField(@Nullable JsonMap json, @NonNull String fieldName) throws JsonException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        JsonMap colorJson = json.opt(fieldName).optMap();
        if (colorJson.isEmpty()) {
            return null;
        }
        return fromJson(colorJson);
    }

    public static float alpha(@ColorInt int color) {
        return android.graphics.Color.alpha(color);
    }

    @ColorInt
    public int resolve(@NonNull Context context) {
        // Look for a selector that matches the current UI mode.
        boolean isDarkMode = ResourceUtils.isUiModeNight(context);
        for (ColorSelector selector : selectors) {
            if (selector.isDarkMode() == isDarkMode) {
                return selector.getColor();
            }
        }
        // Fall back to default color if no match.
        return defaultColor;
    }
}
