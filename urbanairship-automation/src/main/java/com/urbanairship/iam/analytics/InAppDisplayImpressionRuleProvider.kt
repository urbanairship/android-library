/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.iam.InAppMessage

internal sealed class InAppDisplayImpressionRule {
    internal data object Once : InAppDisplayImpressionRule()
    internal data class Interval(val value: Int) : InAppDisplayImpressionRule()
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
        private const val DEFAULT_EMBEDDED_IMPRESSION_INTERVAL = 30
    }
}
