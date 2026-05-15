package com.urbanairship.analytics

/**
 * Event type.
 */
public enum class EventType(public val reportingName: String) {
    APP_FOREGROUND("app_foreground"),
    APP_BACKGROUND("app_background"),
    SCREEN_TRACKING("screen_tracking"),
    ASSOCIATE_IDENTIFIERS("associate_identifiers"),
    INSTALL_ATTRIBUTION("install_attribution"),
    INTERACTIVE_NOTIFICATION_ACTION("interactive_notification_action"),
    REGION_ENTER("region_event"),
    REGION_EXIT("region_event"),
    CUSTOM_EVENT("enhanced_custom_event"),
    FEATURE_FLAG_INTERACTION("feature_flag_interaction"),
    IN_APP_DISPLAY("in_app_display"),
    IN_APP_RESOLUTION("in_app_resolution"),
    LAYOUT_BUTTON_TAP("in_app_button_tap"),
    LAYOUT_PERMISSION_RESULT("in_app_permission_result"),
    LAYOUT_FORM_DISPLAY("in_app_form_display"),
    LAYOUT_FORM_RESULT("in_app_form_result"),
    LAYOUT_GESTURE("in_app_gesture"),
    LAYOUT_PAGER_COMPLETED("in_app_pager_completed"),
    LAYOUT_PAGER_SUMMARY("in_app_pager_summary"),
    LAYOUT_PAGE_SWIPE("in_app_page_swipe"),
    LAYOUT_PAGE_VIEW("in_app_page_view"),
    LAYOUT_PAGE_ACTION("in_app_page_action")
}
