package com.urbanairship.android.layout.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.android.layout.reporting.LayoutData

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface LayoutMessageAnalyticsInterface {
    public fun recordEvent(event: LayoutEvent, layoutContext: LayoutData?)
    public fun recordImpression(date: Long): Boolean
}
