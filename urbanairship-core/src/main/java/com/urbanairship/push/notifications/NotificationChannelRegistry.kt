/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.annotation.XmlRes
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.AirshipDispatchers
import com.urbanairship.AirshipExecutors
import com.urbanairship.PendingResult
import com.urbanairship.R
import com.urbanairship.UALog
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Compatibility class for registering notification channels.
 */
public class NotificationChannelRegistry @VisibleForTesting internal constructor(
    private val context: Context,
    @VisibleForTesting private val dataManager: NotificationChannelRegistryDataManager,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * NotificationChannelRegistry constructor.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(context: Context, configOptions: AirshipConfigOptions) : this(
        context,
        NotificationChannelRegistryDataManager(context, configOptions.appKey, DATABASE_NAME)
    )

    /**
     * Gets a notification channel by identifier.
     *
     * @param id The notification channel identifier.
     * @return An optional [NotificationChannelCompat].
     */
    public suspend fun getNotificationChannel(id: String): NotificationChannelCompat? {
        return scope.async {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return@async dataManager.getChannel(id) ?: getAndCreateDefaultChannel(id)
            }

            notificationManager.getNotificationChannel(id)?.let {
                return@async NotificationChannelCompat(it)
            }

            val result = dataManager.getChannel(id) ?: getAndCreateDefaultChannel(id)
            result?.let { notificationManager.createNotificationChannel(it.toNotificationChannel()) }

            return@async result
        }
        .await()
    }

    /**
     * Gets a notification channel by identifier.
     *
     * @param id The notification channel identifier.
     * @return A [PendingResult] of [NotificationChannelCompat].
     */
    public fun getNotificationChannelAsPending(id: String): PendingResult<NotificationChannelCompat> {
        val pendingResult = PendingResult<NotificationChannelCompat>()

        scope.launch {
            pendingResult.setResult(getNotificationChannel(id))
        }

        return pendingResult
    }

    /**
     * Gets a notification channel by identifier.
     *
     * @param id The notification channel identifier.
     * @return A NotificationChannelCompat, or null if one could not be found.
     */
    @WorkerThread
    public fun getNotificationChannelSync(id: String): NotificationChannelCompat? {
        try {
            return runBlocking { getNotificationChannel(id) }
        } catch (e: InterruptedException) {
            UALog.e(e, "Failed to get notification channel.")
            Thread.currentThread().interrupt()
        } catch (e: ExecutionException) {
            UALog.e(e, "Failed to get notification channel.")
        }

        return null
    }

    /**
     * Deletes a notification channel, by identifier. On Android O and above, this method
     * will also delete the equivalent NotificationChannel on [NotificationManager].
     *
     * @param id The notification channel identifier.
     */
    public fun deleteNotificationChannel(id: String) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.deleteNotificationChannel(id)
            }
            dataManager.deleteChannel(id)
        }
    }

    /**
     * Adds a notification channel and saves it to disk. This method is a no-op if a channel
     * is already created with the same identifier. On Android O and above, this method
     * will also create an equivalent NotificationChannel with [NotificationManager].
     *
     * @param channelCompat A NotificationChannelCompat.
     */
    public fun createNotificationChannel(channelCompat: NotificationChannelCompat) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(channelCompat.toNotificationChannel())
            }
            dataManager.createChannel(channelCompat)
        }
    }

    /**
     * Like [createNotificationChannel], but on Android O and above,
     * the channel will not be created with the NotificationManager until it is accessed with
     * [getNotificationChannel].
     *
     * @param channelCompat A [NotificationChannelCompat].
     */
    public fun createDeferredNotificationChannel(channelCompat: NotificationChannelCompat) {
        scope.launch {
            dataManager.createChannel(channelCompat)
        }
    }

    /**
     * Creates notification channels from an XML file. Any channel that is already created
     * will no-op. On Android O and above, each channel will also create an equivalent
     * NotificationChannel with NotificationManager.
     *
     * The resource file can define all attributes on the channel:
     * <pre>
     * `<resources>
     * <NotificationChannel
     * id="breaking_news"
     * name="@string/breaking_news"
     * description="@string/breaking_news_description"
     * importance="3"
     * can_bypass_dnd="false"
     * can_show_badge="true"
     * group="News"
     * light_color="@color/blue"
     * should_show_lights="true"
     * should_vibrate="true"
     * vibration_pattern="100,150,100" />
     * </resources>
    ` *
    </pre> *
     *
     * @param resourceId The xml resource ID.
     * @hide
     */
    public fun createNotificationChannels(@XmlRes resourceId: Int) {
        scope.launch {
            val channelCompats = NotificationChannelCompat.fromXml(context, resourceId)

            for (channelCompat in channelCompats) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notificationManager.createNotificationChannel(channelCompat.toNotificationChannel())
                }

                dataManager.createChannel(channelCompat)
            }
        }
    }

    @WorkerThread
    private fun getAndCreateDefaultChannel(id: String): NotificationChannelCompat? {
        val result = NotificationChannelCompat
            .fromXml(context, R.xml.ua_default_channels)
            .firstOrNull { it.id == id }

        result?.let { dataManager.createChannel(it) }

        return result
    }

    public companion object {

        /**
         * The registry database name.
         */
        private const val DATABASE_NAME = "ua_notification_channel_registry.db"
    }
}
