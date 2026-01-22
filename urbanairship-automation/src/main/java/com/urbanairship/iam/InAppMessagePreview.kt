package com.urbanairship.iam

import android.content.Context
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.automation.utils.NetworkMonitor
import com.urbanairship.iam.actions.InAppActionRunner
import com.urbanairship.iam.adapter.DisplayAdapterFactory
import com.urbanairship.iam.analytics.InAppCustomEventContext
import com.urbanairship.android.layout.analytics.LayoutEventMessageId
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.iam.assets.EmptyAirshipCachedAssets
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException

/**
 * The class for previewing json content as an InAppMessage
 * @hide
 * */
public class InAppMessagePreview(
    private val json: JsonValue
) {

    private val scope = CoroutineScope(Dispatchers.Default)

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun display(context: Context) {
        try {
            val message = InAppMessage.parseJson(json)
            val factory = DisplayAdapterFactory(
                context = context,
                networkMonitor = NetworkMonitor(context),
                activityMonitor = GlobalActivityMonitor.shared(context)
            )
            val analytics = object : InAppMessageAnalyticsInterface {
                override fun recordEvent(event: LayoutEvent, layoutContext: LayoutData?) { }
                override fun customEventContext(state: LayoutData?): InAppCustomEventContext {
                    return InAppCustomEventContext(
                        id = LayoutEventMessageId.AppDefined(message.name),
                        context = null
                    )
                }
            }

            val actionRunner = InAppActionRunner(
                analytics = analytics,
                trackPermissionResults = false
            )
            val adapter = factory.makeAdapter(
                message = message,
                priority = 0,
                assets = EmptyAirshipCachedAssets(),
                actionRunner = actionRunner
            ).getOrElse { throw IllegalStateException("Failed to create adapter", it) }

            scope.launch {
                adapter.display(context, analytics)
            }
        } catch (ex: JSONException) {
            throw IllegalArgumentException("Invalid JSON", ex)
        }
    }
}
