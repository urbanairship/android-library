/* Copyright Airship and Contributors */

package com.urbanairship.embedded

import androidx.annotation.VisibleForTesting
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Provides information about pending embedded views.
 */
public class AirshipEmbeddedObserver
@VisibleForTesting
internal constructor(
    public val filter: (AirshipEmbeddedInfo) -> Boolean,
    private val manager: AirshipEmbeddedViewManager,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Construct an observer for all embedded views matching the given filter. */
    public constructor(filter: (AirshipEmbeddedInfo) -> Boolean) : this(filter, EmbeddedViewManager)

    /** Construct an observer for the given embedded ID. */
    public constructor(embeddedId: String) : this(filter = { it.embeddedId == embeddedId })

    /** Construct an observer for multiple embedded IDs. */
    public constructor(vararg embeddedIds: String) : this(filter = { it.embeddedId in embeddedIds })

    /** Listener that will be notified when embedded view info is updated. */
    public fun interface Listener {
        public fun onEmbeddedViewInfoUpdate(views: List<AirshipEmbeddedInfo>)
    }

    /** Listener for embedded view info updates. */
    public var listener: Listener? = null
    set(value) {
        field = value
        listenerJob?.cancel()
        if (value != null) {
            listenerJob = scope.launch {
                embeddedViewInfoFlow.collect { value.onEmbeddedViewInfoUpdate(it) }
            }
        }
    }

    private var listenerJob: Job? = null
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /** Embedded view info updates. */
    @JvmSynthetic
    public val embeddedViewInfoFlow: Flow<List<AirshipEmbeddedInfo>> =
        manager.allPending().map { list ->
            list.map {
                AirshipEmbeddedInfo(
                    instanceId = it.viewInstanceId,
                    embeddedId = it.embeddedViewId,
                    extras = it.extras
                )
            }.filter(filter)
        }
}
