/* Copyright Airship and Contributors */

package com.urbanairship.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.PrivacyManager
import com.urbanairship.json.JsonMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Airship events feed.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipEventFeed(
    private val privacyManager: PrivacyManager,
    private val isAnalyticsEnabled: Boolean
) {
    public sealed class Event {
        public data class ScreenTracked(public val name: String) : Event()
        public data class RegionEnter(public val data: JsonMap) : Event()
        public data class RegionExit(public val data: JsonMap) : Event()
        public data class CustomEvent(public val data: JsonMap, public val value: Double?) : Event()
        public data class FeatureFlagInteracted(public val data: JsonMap) : Event()
    }

    private val _events: MutableSharedFlow<Event> = MutableSharedFlow(
        extraBufferCapacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    public val events: SharedFlow<Event> = _events.asSharedFlow()

    public fun emit(event: Event) {
        if (isAnalyticsEnabled && privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS)) {
            _events.tryEmit(event)
        }
    }
}
