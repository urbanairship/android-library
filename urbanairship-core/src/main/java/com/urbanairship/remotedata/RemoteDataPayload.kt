/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap

/**
 * Model representing a remote data payload.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RemoteDataPayload(
    val type: String,
    val timestamp: Long,
    val data: JsonMap,
    val remoteDataInfo: RemoteDataInfo? = null
) {
    public companion object {
        @JvmStatic
        public fun emptyPayload(type: String): RemoteDataPayload {
            return RemoteDataPayload(type, 0, JsonMap.EMPTY_MAP, null)
        }
    }
}
