/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable

/**
 * Thomas listener.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ThomasListenerInterface {

    /**
     * Called when the view is dismissed from outside the view.
     *
     * @param cancel If the layout was cancelled
     */
    public fun onDismiss(cancel: Boolean)

    /**
     * Called whenever the view visibility changes
     *
     * @param isVisible The visibility state.
     * @param isForegrounded The app state.
     */
    public fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean)

    /** Whether [onStateChanged] is called; when `false`, layout state isn't collected for it at all. */
    public val wantsStateChanges: Boolean
        get() = false

    /**
     * Called when a controller scope's layout state changes, if [wantsStateChanges] is `true`.
     *
     * Each form, pager, state, video, or async view controller scope reports separately, once
     * when it's created if its state isn't empty, then each time its serialized state changes.
     *
     * @param state The scope's layout state.
     */
    public fun onStateChanged(state: JsonSerializable)

    /**
     * Called on new analytics event
     *
     * @param event The analytics event.
     */
    public fun onReportingEvent(event: ReportingEvent)
}
