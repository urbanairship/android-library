package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import com.urbanairship.Predicate
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.rewrite.DerivedStateFlow
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.getUrlInfos
import com.urbanairship.automation.rewrite.inappmessage.resumedActivitiesUpdates
import com.urbanairship.automation.rewrite.utils.NetworkMonitor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal class DelegatingDisplayAdapter(
    message: InAppMessage,
    assets: AirshipCachedAssetsInterface,
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
