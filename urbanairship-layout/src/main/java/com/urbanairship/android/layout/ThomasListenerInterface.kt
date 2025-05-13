/* Copyright Airship and Contributors */
package com.urbanairship.android.layout

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.ThomasFormField.BaseForm
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

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

    /**
     * Called whenever the layout state changes
     *
     * @param state The layout state.
     */
    public fun onStateChanged(state: JsonSerializable)

    /**
     * Called on new analytics event
     *
     * @param event The analytics event.
     */
    public fun onReportingEvent(event: ReportingEvent)
}
