/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AudienceEvaluator {
    public suspend fun evaluate(
        compoundAudience: CompoundAudienceSelector?,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider
    ): AudienceResult {
        return compoundAudience?.evaluate(newEvaluationDate, infoProvider)
            ?: AudienceResult.match

    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AudienceResult(
    val isMatch: Boolean
) {

    internal fun negate(): AudienceResult = AudienceResult(!isMatch)

    public companion object {
        public val match: AudienceResult = AudienceResult(true)
        public val miss: AudienceResult = AudienceResult(false)
    }
}
