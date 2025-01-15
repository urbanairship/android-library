package com.urbanairship.sample.glance

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.urbanairship.liveupdate.LiveUpdate
import com.urbanairship.liveupdate.LiveUpdateEvent
import com.urbanairship.liveupdate.LiveUpdateResult
import com.urbanairship.liveupdate.SuspendLiveUpdateCustomHandler
import com.urbanairship.sample.glance.SampleAppWidget.Companion.dataPrefKey

/**
 * A custom live update handler that updates the content of the SampleAppWidget.
 *
 * Sample `start` payload:
 *
 * ```json
 * {
 *   "device_types": ["android"],
 *   "audience": {
 *     "tag": "widget-users"
 *   },
 * 	 "notification": {
 * 	   "android": {
 * 	     "live_update": {
 * 		   "name": "medals-widget-users",
 * 		   "type": "medals-widget",
 * 		   "event": "start",
 * 		   "content_state": {
 *           "countries": [
 *             { "country": "US", "medals": [39, 41, 33] },
 *             { "country": "CN", "medals": [38, 32, 19] },
 *             { "country": "JP", "medals": [27, 14, 17] }
 *           ]
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
internal class SampleAppWidgetLiveUpdate : SuspendLiveUpdateCustomHandler {
    override suspend fun onUpdate(
        context: Context, event: LiveUpdateEvent, update: LiveUpdate
    ): LiveUpdateResult<Nothing> {

        Log.v("AppWidgetLiveUpdate", "onUpdate: $update")

        // Set the new content for all SampleAppWidgets
        GlanceAppWidgetManager(context)
            .getGlanceIds(SampleAppWidget::class.java)
            .forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[dataPrefKey] = update.content.toString()
                }
            }

        // Update all SampleAppWidgets
        SampleAppWidget().updateAll(context)

        return LiveUpdateResult.ok()
    }
}
