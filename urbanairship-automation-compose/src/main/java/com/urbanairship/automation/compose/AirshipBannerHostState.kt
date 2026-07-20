/* Copyright Airship and Contributors */

package com.urbanairship.automation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.platform.LocalContext
import com.urbanairship.UALog
import com.urbanairship.android.layout.AirshipBannerViewManager
import com.urbanairship.android.layout.ui.BannerLayout
import com.urbanairship.banner.BannerViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Creates an [AirshipBannerHostState] that can be used to manage the state of a banner host.
 *
 * Remembering the state starts consuming the pending banner queue, so the returned state should
 * be passed to a mounted [AirshipBannerHost].
 */
@Composable
public fun rememberAirshipBannerHostState(): AirshipBannerHostState {
    return rememberAirshipBannerHostState(BannerViewManager)
}

/** State holder for [AirshipBannerHost] content. */
@Stable
public class AirshipBannerHostState internal constructor(
    private val bannerViewManager: AirshipBannerViewManager
) {
    internal var currentLayout: BannerLayout? by mutableStateOf(null)

    /**
     * Flag indicating whether a banner layout is available for display.
     *
     * @return `true` if a banner layout is available for display, otherwise `false`.
     */
    public val isAvailable: Boolean by derivedStateOf(structuralEqualityPolicy()) {
        currentLayout != null
    }

    /** Dismiss the currently displayed banner, reporting a user dismiss. */
    public suspend fun dismissCurrent(): Unit = coroutineScope {
        currentLayout?.dismissFromUser()
    }

    /**
     * Dismisses the currently displayed banner, reporting a user dismiss for it, and drops all
     * pending banners, resolving each dropped display request as cancelled.
     */
    public suspend fun dismissAll(): Unit = coroutineScope {
        currentLayout?.dismissFromUser()
        bannerViewManager.dismissAll()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AirshipBannerHostState

        return currentLayout == other.currentLayout
    }

    override fun hashCode(): Int {
        return currentLayout?.hashCode() ?: 0
    }
}

/**
 * Creates an [AirshipBannerHostState] and launches an effect to collect pending display requests.
 */
@Composable
internal fun rememberAirshipBannerHostState(
    bannerViewManager: AirshipBannerViewManager
): AirshipBannerHostState {
    val context = LocalContext.current
    val state = remember { AirshipBannerHostState(bannerViewManager) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bannerViewManager) {
        // Collect display requests and update the current layout state.
        withContext(Dispatchers.Default) {
            bannerViewManager.displayRequests(scope)
                .map { it.next }
                // Reuse the existing layout while the view instance is unchanged, so that queue
                // changes don't swap in a new BannerLayout mid-display.
                .distinctUntilChangedBy { it?.viewInstanceId }
                .map { next ->
                    if (next == null) {
                        // Nothing to display.
                        UALog.v { "No banner display request available" }
                        null
                    } else {
                        // Inflate the banner layout.
                        UALog.v { "Banner display request available: \"${next.viewInstanceId}\"" }
                        try {
                            val displayArgs = next.displayArgsProvider.invoke()
                            BannerLayout(context, next.viewInstanceId, displayArgs, bannerViewManager)
                        } catch (e: Exception) {
                            UALog.e(e) { "Failed to create banner layout for instance: \"${next.viewInstanceId}\"" }
                            // Resolve and remove the failed request, so that the queue advances.
                            runCatching {
                                next.displayArgsProvider.invoke().listener.onDismiss(cancel = true)
                            }
                            bannerViewManager.dismiss(next.viewInstanceId)
                            null
                        }
                    }
                }
                .catch {
                    UALog.e(it) { "Banner display request collection failed!" }
                    throw it
                }
                .collect { state.currentLayout = it }
        }
    }

    return state
}
