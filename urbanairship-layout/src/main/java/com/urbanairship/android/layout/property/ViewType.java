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
    BUTTON_LAYOUT("button_layout"),
    PAGER_CONTROLLER("pager_controller"),
    PAGER("pager"),
    PAGER_INDICATOR("pager_indicator"),
    STORY_INDICATOR("story_indicator"),
    FORM_CONTROLLER("form_controller"),
    NPS_FORM_CONTROLLER("nps_form_controller"),
    CHECKBOX_CONTROLLER("checkbox_controller"),
    CHECKBOX("checkbox"),
    TOGGLE("toggle"),
    BASIC_TOGGLE_LAYOUT("basic_toggle_layout"),
    RADIO_INPUT_TOGGLE_LAYOUT("radio_input_toggle_layout"),
    CHECKBOX_TOGGLE_LAYOUT("checkbox_toggle_layout"),
    RADIO_INPUT_CONTROLLER("radio_input_controller"),
    RADIO_INPUT("radio_input"),
    TEXT_INPUT("text_input"),
    SCORE("score"),
    STATE_CONTROLLER("state_controller"),
    CUSTOM_VIEW("custom_view"),
    ICON_VIEW("icon_view"),
    SCORE_CONTROLLER("score_controller"),
    SCORE_TOGGLE_LAYOUT("score_toggle_layout"),
    UNKNOWN("");

    /** View types that provide values for forms (possibly via an intermediate controller). */
    private static final List<ViewType> FORM_INPUTS = Arrays.asList(
            CHECKBOX_CONTROLLER, CHECKBOX, RADIO_INPUT_CONTROLLER, RADIO_INPUT,
            TOGGLE, CHECKBOX_TOGGLE_LAYOUT, RADIO_INPUT_TOGGLE_LAYOUT, BASIC_TOGGLE_LAYOUT,
            TEXT_INPUT, SCORE, FORM_CONTROLLER, NPS_FORM_CONTROLLER, SCORE_CONTROLLER, SCORE_TOGGLE_LAYOUT
    );

    private static final List<ViewType> CONTROLLERS = Arrays.asList(
            CHECKBOX_CONTROLLER, FORM_CONTROLLER, NPS_FORM_CONTROLLER, PAGER_CONTROLLER,
            RADIO_INPUT_CONTROLLER, STATE_CONTROLLER, SCORE_CONTROLLER
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

    public boolean isController() {
        return CONTROLLERS.contains(this);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
