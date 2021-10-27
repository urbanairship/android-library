/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum ViewType {
    CONTAINER("container"),
    LINEAR_LAYOUT("linear_layout"),
    SCROLL_LAYOUT("scroll_layout"),
    WEB_VIEW("web_view"),
    MEDIA("media"),
    LABEL("label"),
    BUTTON("button"),
    IMAGE_BUTTON("image_button"),
    CAROUSEL("carousel"),
    CAROUSEL_INDICATOR("carousel_indicator"),
    CHECKBOX_INPUT("checkbox"),
    RADIO_INPUT("radio"),
    TEXT_INPUT("text_input"),
    UNKNOWN("");

    @NonNull
    private final String value;

    ViewType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static ViewType from(@NonNull String value) {
        for (ViewType type : ViewType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @NonNull
    public static ViewType from(int ordinal) {
        for (ViewType type : ViewType.values()) {
            if (type.ordinal() == ordinal) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
