/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import java.util.Arrays

public enum class ViewType(
    private val value: String
) {
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
    STACK_IMAGE_BUTTON("stack_image_button"),
    UNKNOWN("");

    public val isFormInput: Boolean
        get() = FORM_INPUTS.contains(this)

    public val isController: Boolean
        get() = CONTROLLERS.contains(this)

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        /** View types that provide values for forms (possibly via an intermediate controller).  */
        private val FORM_INPUTS = listOf(
            CHECKBOX_CONTROLLER,
            CHECKBOX,
            RADIO_INPUT_CONTROLLER,
            RADIO_INPUT,
            TOGGLE,
            CHECKBOX_TOGGLE_LAYOUT,
            RADIO_INPUT_TOGGLE_LAYOUT,
            BASIC_TOGGLE_LAYOUT,
            TEXT_INPUT,
            SCORE,
            FORM_CONTROLLER,
            NPS_FORM_CONTROLLER,
            SCORE_CONTROLLER,
            SCORE_TOGGLE_LAYOUT
        )

        private val CONTROLLERS = listOf(
            CHECKBOX_CONTROLLER,
            FORM_CONTROLLER,
            NPS_FORM_CONTROLLER,
            PAGER_CONTROLLER,
            RADIO_INPUT_CONTROLLER,
            STATE_CONTROLLER,
            SCORE_CONTROLLER
        )

        public fun from(value: String): ViewType {
            val content = value.lowercase()
            return entries.firstOrNull { it.value == content } ?: UNKNOWN
        }

        public fun from(ordinal: Int): ViewType {
            return entries.firstOrNull { it.ordinal == ordinal } ?: UNKNOWN
        }
    }
}
