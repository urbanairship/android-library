package com.urbanairship.devapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import com.urbanairship.AirshipDispatchers
import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.CallbackLiveUpdateNotificationHandler
import com.urbanairship.liveupdate.LiveUpdate
import com.urbanairship.liveupdate.LiveUpdateEvent
import java.util.concurrent.ExecutionException
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sample sports live update handler, with support for asynchronously fetching team images during
 * onUpdate.
 */
class SampleAsyncLiveUpdate(
    backgroundDispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher(),
) : CallbackLiveUpdateNotificationHandler {

    private val scope = CoroutineScope(backgroundDispatcher + SupervisorJob())

    override fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate,
        resultCallback: CallbackLiveUpdateNotificationHandler.LiveUpdateResultCallback
    ) {
        Log.d("SampleAsyncLiveUpdate", "onUpdate: action=$event, update=$update")

        if (event == LiveUpdateEvent.END) {
            // Dismiss the live update on END. The default behavior will leave the Live Update
            // in the notification tray until the dismissal time is reached or the user dismisses it.
            resultCallback.cancel()
            return
        }

        val contentUpdate = ContentUpdate.fromJson(update.content)

        val bigLayout = RemoteViews(context.packageName, R.layout.live_update_notification_big)
        contentUpdate.fillLayout(bigLayout, layoutType = ContentUpdate.LayoutType.BIG)

        val smallLayout = RemoteViews(context.packageName, R.layout.live_update_notification_small)
        contentUpdate.fillLayout(smallLayout, layoutType = ContentUpdate.LayoutType.SMALL)

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addCategory(update.name)
            ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?.setPackage(null)

        val contentIntent = PendingIntent.getActivity(
            context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "sports")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(smallLayout).setCustomBigContentView(bigLayout)
            .setContentIntent(contentIntent)

        scope.launch {
            val icons = contentUpdate.loadTeamIcons(context)

            withContext(Dispatchers.Main) {
                if (icons.teamOne != null && icons.teamTwo != null) {
                    smallLayout.setImageViewBitmap(R.id.teamTwoImage, icons.teamTwo)
                    smallLayout.setImageViewBitmap(R.id.teamOneImage, icons.teamOne)
                    bigLayout.setImageViewBitmap(R.id.teamOneImage, icons.teamOne)
                    bigLayout.setImageViewBitmap(R.id.teamTwoImage, icons.teamTwo)
                }

                resultCallback.ok(builder)
            }
        }
    }

    private data class ContentUpdate(
        val teamOne: Team,
        val teamTwo: Team,
        val statusUpdate: String
    ) {
        data class Team (
            val name: String,
            val score: Int,
            val imageUrl: String
        ) {
            @WorkerThread
            @Throws(ExecutionException::class, InterruptedException::class)
            fun fetchBitmap(context: Context): Bitmap? {
                if (imageUrl.isEmpty()) {
                    return null
                }

                return Glide.with(context).asBitmap().load(imageUrl).submit().get()
            }
        }

        enum class LayoutType {
            BIG, SMALL
        }

        fun fillLayout(layout: RemoteViews, layoutType: LayoutType) {
            layout.setTextViewText(R.id.teamOneScore, teamOne.score.toString())
            layout.setTextViewText(R.id.teamTwoScore, teamTwo.score.toString())

            when(layoutType) {
                LayoutType.BIG -> {
                    layout.setTextViewText(R.id.teamOneName, teamOne.name)
                    layout.setTextViewText(R.id.teamTwoName, teamTwo.name)
                    layout.setTextViewText(R.id.statusUpdate, statusUpdate)
                }
                else -> {}
            }
        }

        @WorkerThread
        fun loadTeamIcons(context: Context): TeamLogo {
            val result = listOf(teamOne, teamTwo).map {
                try {
                    it.fetchBitmap(context)
                } catch (_: Exception) {
                    null
                }
            }.let {
                TeamLogo(it[0], it[1])
            }

            return result
        }

        data class TeamLogo(
            val teamOne: Bitmap?,
            val teamTwo: Bitmap?
        )

        companion object {
            private const val TEAM_ONE_PREFIX = "team_one_"
            private const val TEAM_TWO_PREFIX = "team_two_"

            private const val SCORE_KEY = "score"
            private const val NAME_KEY = "name"
            private const val IMAGE_KEY = "image"

            private const val STATUS_UPDATE_KEY = "status_update"

            fun fromJson(value: JsonMap): ContentUpdate {
                return ContentUpdate(
                    teamOne = Team(
                        name = value.opt(TEAM_ONE_PREFIX + NAME_KEY).getString("Foxes"),
                        score = value.opt(TEAM_ONE_PREFIX + SCORE_KEY).getInt(0),
                        imageUrl = value.opt(TEAM_ONE_PREFIX + IMAGE_KEY).getString("")
                    ),
                    teamTwo = Team(
                        name = value.opt(TEAM_TWO_PREFIX + NAME_KEY).getString("Tigers"),
                        score = value.opt(TEAM_TWO_PREFIX + SCORE_KEY).getInt(0),
                        imageUrl = value.opt(TEAM_TWO_PREFIX + IMAGE_KEY).getString("")
                    ),
                    statusUpdate = value.opt(STATUS_UPDATE_KEY).optString()
                )
            }
        }
    }
}
