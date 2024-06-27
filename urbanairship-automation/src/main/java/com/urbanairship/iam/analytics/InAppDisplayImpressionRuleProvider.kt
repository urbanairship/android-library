/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.iam.InAppMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal sealed class InAppDisplayImpressionRule {
    internal data object Once : InAppDisplayImpressionRule()
    internal data class Interval(val value: Duration) : InAppDisplayImpressionRule()
}

internal interface InAppDisplayImpressionRuleInterface {
    fun impressionRules(message: InAppMessage) : InAppDisplayImpressionRule
}

internal class DefaultInAppDisplayImpressionRuleProvider : InAppDisplayImpressionRuleInterface {

    override fun impressionRules(message: InAppMessage): InAppDisplayImpressionRule {
        return if (message.isEmbedded()) {
            InAppDisplayImpressionRule.Interval(DEFAULT_EMBEDDED_IMPRESSION_INTERVAL)
        } else {
            InAppDisplayImpressionRule.Once
        }
    }

    companion object {
        private val DEFAULT_EMBEDDED_IMPRESSION_INTERVAL: Duration = 30.minutes
    }
}
