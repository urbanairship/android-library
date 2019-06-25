/* Copyright Airship and Contributors */

package com.urbanairship.debug.event

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.urbanairship.debug.R
import com.urbanairship.json.JsonMap

/**
 * Event colors, initials, details helpers.
 */
internal class EventInfo {

    companion object {
        private const val LOCATION_EVENT = "location"
        private const val REGION_EVENT = "region_event"
        private const val SCREEN_EVENT = "screen_tracking"
        private const val CUSTOM_EVENT = "custom_event"
        private const val IAA_RESOLUTION_EVENT = "in_app_resolution"
        private const val IAA_DISPLAY_EVENT = "in_app_display"
        private const val APP_BACKGROUND_EVENT = "app_background"
        private const val APP_FOREGROUND_EVENT = "app_foreground"
        private const val PUSH_ARRIVED_EVENT = "push_arrived"
        private const val ASSOCIATE_IDENTIFIERS_EVENT = "associate_identifiers"
        private const val INSTALL_ATTRIBUTION_EVENT = "install_attribution"

        val KNOWN_TYPES = listOf(LOCATION_EVENT, REGION_EVENT, SCREEN_EVENT,
                CUSTOM_EVENT, IAA_RESOLUTION_EVENT, IAA_DISPLAY_EVENT, APP_BACKGROUND_EVENT,
                APP_FOREGROUND_EVENT, PUSH_ARRIVED_EVENT, ASSOCIATE_IDENTIFIERS_EVENT,
                INSTALL_ATTRIBUTION_EVENT)

        @JvmStatic
        @ColorInt
        fun getColor(context: Context, type: String): Int {
            return ContextCompat.getColor(context, getColorRes(type))
        }

        @ColorRes
        fun getColorRes(type: String): Int {
            return when (type) {
                LOCATION_EVENT -> R.color.ua_location_event
                REGION_EVENT -> R.color.ua_region_event
                SCREEN_EVENT -> R.color.ua_screen_event
                CUSTOM_EVENT -> R.color.ua_custom_event
                IAA_RESOLUTION_EVENT -> R.color.ua_in_app_resolution_event
                IAA_DISPLAY_EVENT -> R.color.ua_in_app_display_event
                APP_BACKGROUND_EVENT -> R.color.ua_app_background_event
                APP_FOREGROUND_EVENT -> R.color.ua_app_foreground_event
                PUSH_ARRIVED_EVENT -> R.color.ua_push_arrived_event
                ASSOCIATE_IDENTIFIERS_EVENT -> R.color.ua_associate_identifiers_event
                INSTALL_ATTRIBUTION_EVENT -> R.color.ua_install_attribution_event
                else -> R.color.ua_event
            }
        }

        fun getEventName(context: Context, type: String): String {
            return when (type) {
                LOCATION_EVENT -> context.getString(R.string.ua_location_event_name)
                REGION_EVENT -> context.getString(R.string.ua_region_event_name)
                SCREEN_EVENT -> context.getString(R.string.ua_screen_event_name)
                CUSTOM_EVENT -> context.getString(R.string.ua_custom_event_name)
                IAA_RESOLUTION_EVENT -> context.getString(R.string.ua_in_app_resolution_event_name)
                IAA_DISPLAY_EVENT -> context.getString(R.string.ua_in_app_display_event_name)
                APP_BACKGROUND_EVENT -> context.getString(R.string.ua_app_background_event_name)
                APP_FOREGROUND_EVENT -> context.getString(R.string.ua_app_foreground_event_name)
                PUSH_ARRIVED_EVENT -> context.getString(R.string.ua_push_arrived_event_name)
                ASSOCIATE_IDENTIFIERS_EVENT -> context.getString(R.string.ua_associate_identifiers_event_name)
                INSTALL_ATTRIBUTION_EVENT -> context.getString(R.string.ua_install_attribution_event_name)
                else -> context.getString(R.string.ua_event_name, type)
            }
        }

        @JvmStatic
        fun getEventInitials(type: String): String {
            return when (type) {
                LOCATION_EVENT -> "L"
                REGION_EVENT -> "R"
                SCREEN_EVENT -> "S"
                CUSTOM_EVENT -> "C"
                IAA_RESOLUTION_EVENT -> "IR"
                IAA_DISPLAY_EVENT -> "ID"
                APP_BACKGROUND_EVENT -> "AB"
                APP_FOREGROUND_EVENT -> "AF"
                PUSH_ARRIVED_EVENT -> "PA"
                ASSOCIATE_IDENTIFIERS_EVENT -> "AI"
                INSTALL_ATTRIBUTION_EVENT -> "IA"
                else -> type.firstOrEmpty().toUpperCase()
            }
        }

        fun getDetailedEventName(context: Context, type: String, eventData: JsonMap): String? {
            val shortEventName = getEventName(context, type)
            return when (type) {
                EventInfo.LOCATION_EVENT -> "$shortEventName ${eventData.opt("lat")}, ${eventData.opt("long")}"
                EventInfo.REGION_EVENT -> "$shortEventName  ${eventData.opt("source").getString("")}: ${eventData.opt("action").getString("").toUpperCase()} ${eventData.opt("region_id")}"
                EventInfo.SCREEN_EVENT -> "$shortEventName  ${eventData.opt("screen").getString("")}"
                EventInfo.CUSTOM_EVENT -> "$shortEventName  ${eventData.opt("event_name").getString("")}"
                else -> shortEventName
            }
        }

        fun String.firstOrEmpty(): String {
            if (isEmpty()) {
                return ""
            }
            return get(0).toString()
        }
    }
}
