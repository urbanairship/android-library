/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;

public enum ViewType {
    CONTAINER("container"),
    LINEAR_LAYOUT("linear_layout"),
    SCROLL_LAYOUT("scroll_layout"),
    EMPTY_VIEW("empty_view"),
    WEB_VIEW("web_view"),
    MEDIA("media"),
    LABEL("label"),
    LABEL_BUTTON("label_button"),
    IMAGE_BUTTON("image_button"),
    PAGER_CONTROLLER("pager_controller"),
    PAGER("pager"),
    PAGER_INDICATOR("pager_indicator"),
    FORM_CONTROLLER("form_controller"),
    NPS_FORM_CONTROLLER("nps_form_controller"),
    CHECKBOX_CONTROLLER("checkbox_controller"),
    CHECKBOX("checkbox"),
    TOGGLE("toggle"),
    RADIO_INPUT_CONTROLLER("radio_input_controller"),
    RADIO_INPUT("radio_input"),
    TEXT_INPUT("text_input"),
    SCORE("score"),
    UNKNOWN("");

    /** View types that provide values for forms (possibly via an intermediate controller). */
    private static final List<ViewType> FORM_INPUTS = Arrays.asList(
        CHECKBOX_CONTROLLER, CHECKBOX, RADIO_INPUT_CONTROLLER, RADIO_INPUT,
        TOGGLE, TEXT_INPUT, SCORE, FORM_CONTROLLER, NPS_FORM_CONTROLLER
    );

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

    public boolean isFormInput() {
        return FORM_INPUTS.contains(this);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
