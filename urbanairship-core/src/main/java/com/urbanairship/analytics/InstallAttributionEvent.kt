/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf

/**
 * Event to track Google Play Store referrals.
 *
 * @param referrer The Play Store install referrer.
 */
internal class InstallAttributionEvent(private val referrer: String) : Event() {

    override val type: EventType = EventType.INSTALL_ATTRIBUTION

    @Throws(com.urbanairship.json.JsonException::class)
    override fun getEventData(conversionData: ConversionData): JsonMap = jsonMapOf(
        PLAY_STORE_REFERRER to referrer
    )

    companion object {
        private const val PLAY_STORE_REFERRER = "google_play_referrer"
    }
}
