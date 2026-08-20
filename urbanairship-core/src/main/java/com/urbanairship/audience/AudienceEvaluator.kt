/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import com.urbanairship.cache.AirshipCache
import java.time.Instant

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AudienceEvaluator(cache: AirshipCache) {

    private val hashChecker = HashChecker(cache)

    public suspend fun evaluate(
        compoundAudience: CompoundAudienceSelector?,
        newEvaluationDate: Instant,
        infoProvider: DeviceInfoProvider
    ): AirshipDeviceAudienceResult {

        return compoundAudience?.evaluate(newEvaluationDate, infoProvider, hashChecker)
            ?: AirshipDeviceAudienceResult.match

    }
}
