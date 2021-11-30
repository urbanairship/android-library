/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public class SwitchStyle extends ToggleStyle {
    @NonNull
    private final Color onColor;
    @NonNull
    private final Color offColor;

    public SwitchStyle(
        @NonNull Color onColor,
        @NonNull Color offColor
    ) {
        super(ToggleType.SWITCH);

        this.onColor = onColor;
        this.offColor = offColor;
    }

    @NonNull
    public static SwitchStyle fromJson(@NonNull JsonMap json) throws JsonException {
        JsonMap colors = json.opt("toggle_colors").optMap();
        Color onColor = Color.fromJsonField(colors, "on");
        if (onColor == null) {
            throw new JsonException("Failed to parse SwitchStyle! Field 'toggle_colors.on' may not be null.");
        }
        Color offColor = Color.fromJsonField(colors, "off");
        if (offColor == null) {
            throw new JsonException("Failed to parse SwitchStyle! Field 'toggle_colors.off' may not be null.");
        }

        return new SwitchStyle(onColor, offColor);
    }

    @NonNull
    public Color getOnColor() {
        return onColor;
    }

    @NonNull
    public Color getOffColor() {
        return offColor;
    }
}
