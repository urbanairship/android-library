/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import com.urbanairship.audience.AudienceSelector
import com.urbanairship.audience.DeviceInfoProvider

internal class AudienceEvaluator(private val context: Context) {
    suspend fun evaluate(
        audienceSelector: AudienceSelector,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider
    ): Boolean {
        return audienceSelector.evaluate(context, newEvaluationDate, infoProvider)
    }
}
