/* Copyright Airship and Contributors */

package com.urbanairship.iam

import androidx.annotation.RestrictTo
import com.urbanairship.iam.actions.InAppActionRunner
import com.urbanairship.iam.adapter.DisplayAdapter
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.coordinator.DisplayCoordinator

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class PreparedInAppMessageData(
    val message: InAppMessage,
    val displayAdapter: DisplayAdapter,
    val displayCoordinator: DisplayCoordinator,
    val analytics: InAppMessageAnalyticsInterface,
    val actionRunner: InAppActionRunner
)
