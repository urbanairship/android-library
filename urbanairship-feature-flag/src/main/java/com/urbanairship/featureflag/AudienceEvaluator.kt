/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider

internal class AudienceEvaluator {
    suspend fun evaluate(
        audienceSelector: AudienceSelector,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider
    ): Boolean {
        return audienceSelector.evaluate(newEvaluationDate, infoProvider)
    }
}
