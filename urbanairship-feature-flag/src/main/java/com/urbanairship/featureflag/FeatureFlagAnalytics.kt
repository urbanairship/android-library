package com.urbanairship.featureflag

import com.urbanairship.UALog
import com.urbanairship.analytics.Analytics

/** Analytics helper that handles tracking of feature flag interactions. */
internal class FeatureFlagAnalytics(
    private val analytics: Analytics,
) {

    /** Tracks an interaction with the given feature [flag]. */
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
            analytics.addEvent(event)
        } catch (exception: Exception) {
            UALog.e(exception) { "Unable to track interaction: $flag" }
        }
    }
}
