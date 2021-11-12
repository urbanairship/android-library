/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public class SwitchStyle extends ToggleStyle {
    @ColorInt
    private final int onColor;
    @ColorInt
    private final int offColor;

    public SwitchStyle(
        @ColorInt int onColor,
        @ColorInt int offColor
    ) {
        super(ToggleType.SWITCH);

        this.onColor = onColor;
        this.offColor = offColor;
    }

    @NonNull
    public static SwitchStyle fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap colors = json.opt("toggle_colors").optMap();
        @ColorInt Integer onColor = Color.fromJsonField(colors, "on");
        if (onColor == null) {
            throw new JsonException("Failed to parse SwitchStyle! Field 'toggle_colors.on' may not be null.");
        }
        @ColorInt Integer offColor = Color.fromJsonField(colors, "off");
        if (offColor == null) {
            throw new JsonException("Failed to parse SwitchStyle! Field 'toggle_colors.off' may not be null.");
        }

        return new SwitchStyle(onColor, offColor);
    }

    @ColorInt
    public int getOnColor() {
        return onColor;
    }

    @ColorInt
    public int getOffColor() {
        return offColor;
    }
}
