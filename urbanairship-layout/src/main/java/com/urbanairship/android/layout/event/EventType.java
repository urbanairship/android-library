/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.event;

/**
 * Event types.
 */
public enum EventType {
    /* View events. */

    /** Event emitted when views have been configured. */
    VIEW_INIT,
    /** Event emitted when views have been attached to window. Currently only needed for form inputs. */
    VIEW_ATTACHED,

    /* Button events. */

    BUTTON_BEHAVIOR_DISMISS,
    BUTTON_BEHAVIOR_CANCEL,
    BUTTON_BEHAVIOR_PAGER_NEXT,
    BUTTON_BEHAVIOR_PAGER_PREVIOUS,
    BUTTON_BEHAVIOR_FORM_SUBMIT,
    BUTTON_ACTIONS,

    /* Pager events */

    /** Pager initialization event. */
    PAGER_INIT,
    /** Pager scroll events. */
    PAGER_SCROLL,
    /** Pager page display actions event. */
    PAGER_PAGE_ACTIONS,

    /* Form events */

    /** Form controller initialization events. */
    FORM_INIT,
    /** NPS Form controller initialization events. */
    NPS_FORM_INIT,
    /** Form data changes. */
    FORM_DATA_CHANGE,
    /** Form validation state changes. */
    FORM_VALIDATION,

    /** Form input / input controller initialization events. */
    FORM_INPUT_INIT,

    /** Checkbox input events. */
    CHECKBOX_INPUT_CHANGE,
    /** Checkbox view updates. */
    CHECKBOX_VIEW_UPDATE,
    /** Radio input events. */
    RADIO_INPUT_CHANGE,
    /** Radio view updates. */
    RADIO_VIEW_UPDATE,
    /** Toggle input events. */
    TOGGLE_INPUT_CHANGE,
    /** Text input events. */
    TEXT_INPUT_CHANGE,
    /** Score input events. */
    SCORE_INPUT_CHANGE,

    /** Reporting events */
    REPORTING_EVENT,

    /** WebView dismiss event */
    WEBVIEW_CLOSE
}
