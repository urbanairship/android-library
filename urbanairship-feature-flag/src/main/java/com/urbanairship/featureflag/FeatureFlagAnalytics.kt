package com.urbanairship.featureflag

import com.urbanairship.UALog
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics

class FeatureFlagAnalytics(
    private val eventFeed: AirshipEventFeed,
    private val analytics: Analytics,
) {

    fun trackInteraction(flag: FeatureFlag) {
        if (!flag.exists) {
            UALog.e { "Flag does not exist, unable to track interaction: $flag" }
            return
        }

        if (flag.reportingInfo == null) {
            UALog.e { "Flag missing reporting info, unable to track interaction: $flag" }
            return
        }

        try {
            val event = FeatureFlagInteractionEvent(flag)
            if (analytics.addEvent(event)) {
                eventFeed.emit(AirshipEventFeed.Event.FeatureFlagInteracted(event.data))
            }
        } catch (exception: Exception) {
            UALog.e(exception) { "Unable to track interaction: $flag" }
        }
    }
}
