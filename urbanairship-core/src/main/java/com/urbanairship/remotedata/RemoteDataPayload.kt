/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap
import java.time.Instant

/**
 * Model representing a remote data payload.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RemoteDataPayload(
    val type: String,
    val timestamp: Instant,
    val data: JsonMap,
    val remoteDataInfo: RemoteDataInfo? = null
) {
    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun emptyPayload(type: String): RemoteDataPayload {
            return RemoteDataPayload(type, Instant.EPOCH, JsonMap.EMPTY_MAP, null)
        }
    }
}
