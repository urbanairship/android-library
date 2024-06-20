/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions

import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.content.InAppMessageDisplayContent

internal class InAppActionRunnerFactory {

    internal fun makeRunner(inAppMessage: InAppMessage, analytics: InAppMessageAnalyticsInterface): InAppActionRunner  {
        return InAppActionRunner(
            analytics = analytics,
            trackPermissionResults = inAppMessage.displayContent.displayType == InAppMessageDisplayContent.DisplayType.LAYOUT
        )
    }
}
