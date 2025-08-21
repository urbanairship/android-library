/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.channel.AirshipChannel
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ChannelCapture detects a knock when the application is foregrounded 6 times in 30 seconds.
 * When a knock is detected, it writes the channel ID to the clipboard as ua:<channel_id>.
</channel_id> */
public class ChannelCapture @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    context: Context,
    private val configOptions: AirshipConfigOptions,
    private val airshipChannel: AirshipChannel,
    preferenceDataStore: PreferenceDataStore,
    private val activityMonitor: ActivityMonitor,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) : AirshipComponent(context, preferenceDataStore) {

    private var clipboardManager: ClipboardManager? = null
    private val listener: ApplicationListener

    private var indexOfKnocks = 0
    private var knockTimes = LongArray(KNOCKS_TO_TRIGGER_CHANNEL_CAPTURE)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /**
     * Returns `true` if channel capture is enabled, [com.urbanairship.AirshipConfigOptions.channelCaptureEnabled]
     * is set to `true`, otherwise `false`.
     * @return `true` if channel capture is enabled, otherwise `false`.
     */
    public var isEnabled: Boolean = false

    init {
        this.listener = object : SimpleApplicationListener() {
            override fun onForeground(time: Long) {
                countForeground(time)
            }
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun init() {
        super.init()

        isEnabled = configOptions.channelCaptureEnabled

        activityMonitor.addApplicationListener(listener)
    }

    /**
     * Count the number of foregrounds to perform the knock.
     * @param time the timestamp to when the app has been foregrounded.
     */
    private fun countForeground(time: Long) {
        if (!isEnabled) {
            return
        }

        if (indexOfKnocks >= KNOCKS_TO_TRIGGER_CHANNEL_CAPTURE) {
            indexOfKnocks = 0
        }
        knockTimes[indexOfKnocks] = time
        indexOfKnocks++
        if (checkKnock()) {
            writeClipboard()
        }
    }

    /**
     * Check if a knock should be launched.
     * @return `true` if there is a knock, otherwise return `false`.
     */
    private fun checkKnock(): Boolean {
        val currentTime = Calendar.getInstance().timeInMillis

        return !knockTimes.any { it + KNOCKS_MAX_TIME.inWholeMilliseconds < currentTime }
    }

    /**
     * Check if the clipboard is available and perform the channel capture.
     */
    private fun writeClipboard() {
        if (clipboardManager == null) {
            // Since ClipboardManager initialization can fail deep in the android platform
            // stack, catch any unanticipated errors here.
            try {
                clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            } catch (e: Exception) {
                UALog.e(e, "Unable to initialize clipboard manager: ")
            }
        }

        if (clipboardManager == null) {
            UALog.d("Unable to attempt channel capture, clipboard manager uninitialized")
            return
        }

        // reset the knock counters so it takes 6 new knocks to capture channel
        knockTimes = LongArray(KNOCKS_TO_TRIGGER_CHANNEL_CAPTURE)
        indexOfKnocks = 0

        val channel = airshipChannel.id
        val channelIdForClipboard = if (channel.isNullOrEmpty()) "ua:" else "ua:$channel"

        scope.launch {
            try {
                val clipData = ClipData.newPlainText("UA Channel ID", channelIdForClipboard)
                clipboardManager?.setPrimaryClip(clipData)
                UALog.d("Channel ID copied to clipboard")
            } catch (e: Exception) {
                UALog.w(e, "Channel capture failed! Unable to copy Channel ID to clipboard.")
            }
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun tearDown() {
        activityMonitor.removeApplicationListener(listener)
    }

    internal companion object {
        private val KNOCKS_MAX_TIME = 30.seconds
        private const val KNOCKS_TO_TRIGGER_CHANNEL_CAPTURE = 6
    }
}
