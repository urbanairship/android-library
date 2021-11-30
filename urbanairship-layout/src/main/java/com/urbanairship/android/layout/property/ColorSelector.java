/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ColorSelector {
    @Nullable
    private final Platform platform;
    private final boolean darkMode;
    @ColorInt
    private final int color;

    public ColorSelector(@Nullable Platform platform, boolean darkMode, int color) {
        this.platform = platform;
        this.darkMode = darkMode;
        this.color = color;
    }

    @NonNull
    public static ColorSelector fromJson(@NonNull JsonMap json) throws JsonException {
        String platformString = json.opt("platform").optString();
        Platform platform = platformString.isEmpty() ? null : Platform.from(platformString);
        boolean darkMode = json.opt("dark_mode").getBoolean(false);
        JsonMap colorJson = json.opt("color").optMap();
        @ColorInt Integer color = HexColor.fromJson(colorJson);
        if (color == null) {
            throw new JsonException("Failed to parse color selector. 'color' may not be null! json = '" + json + "'");
        }

        return new ColorSelector(platform, darkMode, color);
    }

    @NonNull
    public static List<ColorSelector> fromJsonList(@NonNull JsonList json) throws JsonException {
        List<ColorSelector> selectors = new ArrayList<>(json.size());
        for (int i = 0; i < json.size(); i++) {
            JsonMap selectorJson = json.get(i).optMap();
            ColorSelector selector = ColorSelector.fromJson(selectorJson);
            selectors.add(selector);
        }
        return selectors;
    }

    @Nullable
    public Platform getPlatform() {
        return platform;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    @ColorInt
    public int getColor() {
        return color;
    }
}
