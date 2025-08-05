/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.events

import androidx.lifecycle.ViewModel
import com.urbanairship.UAirship
import com.urbanairship.analytics.Analytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

internal interface AnalyticsViewModel {
    val trackAdvertisingId: Flow<Boolean>
}

internal class DefaultAnalyticsViewModel: AnalyticsViewModel, ViewModel() {

    private val analytics: Analytics?
        get() {
            return if (!UAirship.isFlying) {
                null
            } else {
                UAirship.shared().analytics
            }
        }

    override val trackAdvertisingId: Flow<Boolean>
        get() {
            val analytics = this.analytics ?: return emptyFlow()
            return flowOf(!analytics.associatedIdentifiers.isLimitAdTrackingEnabled)
        }
}
