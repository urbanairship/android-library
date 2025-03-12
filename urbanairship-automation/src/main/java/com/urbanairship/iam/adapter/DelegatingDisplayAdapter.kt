/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import com.urbanairship.Predicate
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.util.DerivedStateFlow
import com.urbanairship.automation.utils.NetworkMonitor
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.getUrlInfos
import com.urbanairship.iam.resumedActivitiesUpdates
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal class DelegatingDisplayAdapter(
    message: InAppMessage,
    assets: AirshipCachedAssets,
    val delegate: Delegate,
    networkMonitor: NetworkMonitor,
    activityMonitor: ActivityMonitor
) : DisplayAdapter {

    interface Delegate {
        val activityPredicate: Predicate<Activity>?

        @MainThread
        suspend fun display(context: Context, analytics: InAppMessageAnalyticsInterface): DisplayResult
    }

    override val isReady: StateFlow<Boolean>

    init {
        val needsNetwork = message.getUrlInfos().any {
            when(it.type) {
                UrlInfo.UrlType.WEB_PAGE -> it.requiresNetwork
                UrlInfo.UrlType.IMAGE -> it.requiresNetwork && !assets.isCached(it.url)
                UrlInfo.UrlType.VIDEO -> it.requiresNetwork
            }
        }

        isReady = DerivedStateFlow(
            onValue = {
                (!needsNetwork || networkMonitor.isConnected.value) && activityMonitor.getResumedActivities(delegate.activityPredicate).isNotEmpty()
            },
            updates = combine(networkMonitor.isConnected, activityMonitor.resumedActivitiesUpdates(delegate.activityPredicate)) { isConnected, hasActivity ->
                (!needsNetwork || isConnected) && hasActivity
            }
        )
    }

    override suspend fun display(
        context: Context, analytics: InAppMessageAnalyticsInterface
    ): DisplayResult {
        return delegate.display(context, analytics)
    }
}
