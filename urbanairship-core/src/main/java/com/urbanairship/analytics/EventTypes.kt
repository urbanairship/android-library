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
    PUSH_ARRIVED("push_arrived"),
    REGION_ENTER("region_event"),
    REGION_EXIT("region_event"),
    CUSTOM_EVENT("enhanced_custom_event"),
    FEATURE_FLAG_INTERACTION("feature_flag_interaction"),
    IN_APP_DISPLAY("in_app_display"),
    IN_APP_RESOLUTION("in_app_resolution"),
    IN_APP_BUTTON_TAP("in_app_button_tap"),
    IN_APP_PERMISSION_RESULT("in_app_permission_result"),
    IN_APP_FORM_DISPLAY("in_app_form_display"),
    IN_APP_FORM_RESULT("in_app_form_result"),
    IN_APP_GESTURE("in_app_gesture"),
    IN_APP_PAGER_COMPLETED("in_app_pager_completed"),
    IN_APP_PAGER_SUMMARY("in_app_pager_summary"),
    IN_APP_PAGE_SWIPE("in_app_page_swipe"),
    IN_APP_PAGE_VIEW("in_app_page_view"),
    IN_APP_PAGE_ACTION("in_app_page_action")
}
