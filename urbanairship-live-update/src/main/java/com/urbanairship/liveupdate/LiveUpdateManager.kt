/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.PrivacyManager.FEATURE_PUSH
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.data.LiveUpdateDatabase
import com.urbanairship.liveupdate.notification.LiveUpdatePayload
import com.urbanairship.push.PushManager

/**
 * Airship Live Updates.
 */
public class LiveUpdateManager

/** @hide */
@VisibleForTesting
internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    config: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val pushManager: PushManager,
    private val channel: AirshipChannel,
    db: LiveUpdateDatabase = LiveUpdateDatabase.createDatabase(context, config),
    private val registrar: LiveUpdateRegistrar = LiveUpdateRegistrar(context, channel, db.liveUpdateDao()),
) : AirshipComponent(context, dataStore) {

    private val isFeatureEnabled: Boolean
        get() = privacyManager.isEnabled(FEATURE_PUSH)

    public constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        channel: AirshipChannel,
        pushManager: PushManager
    ) : this(context, dataStore, config, privacyManager, pushManager, channel)

    /**
     * Registers a [handler] for the given [type].
     *
     * @param type The handler type.
     * @param handler A [LiveUpdateHandler].
     */
    public fun register(type: String, handler: LiveUpdateHandler<*>) {
        registrar.register(type, handler)
    }

    /**
     * Registers a [handler] for the given [type].
     *
     * @param type The handler type.
     * @param handler A [SuspendLiveUpdateHandler].
     */
    public fun register(type: String, handler: AsyncLiveUpdateNotificationHandler) {
        registrar.register(type, handler)
    }

    /**
     * Starts tracking for a Live Update, with initial [content].
     *
     * @param name The Live Update name.
     * @param type The handler type.
     * @param content A [JsonMap] with initial content.
     * @param timestamp The start timestamp, used to filter out-of-order events (default: now).
     * @param dismissTimestamp Optional timestamp, when to end this Live Update (default: null).
     */
    @JvmOverloads
    public fun start(
        name: String,
        type: String,
        content: JsonMap,
        timestamp: Long = System.currentTimeMillis(),
        dismissTimestamp: Long? = null,
    ) {
        if (isFeatureEnabled) {
            registrar.start(name, type, content, timestamp, dismissTimestamp)
        }
    }

    /**
     * Updates the [content] for a tracked Live Update.
     *
     * @param name The live update name.
     * @param content A [JsonMap] with updated content.
     * @param timestamp The update timestamp, used to filter out-of-order events (default: now).
     */
    @JvmOverloads
    public fun update(
        name: String,
        content: JsonMap,
        timestamp: Long = System.currentTimeMillis(),
        dismissTimestamp: Long? = null,
    ) {
        if (isFeatureEnabled) {
            registrar.update(name, content, timestamp, dismissTimestamp)
        }
    }

    /**
     * Ends tracking for the Live Update with the given [name].
     *
     * @param name The live update name.
     * @param timestamp The end timestamp, used to filter out-of-order events (default: now).
     */
    @JvmOverloads
    public fun end(
        name: String,
        content: JsonMap? = null,
        timestamp: Long = System.currentTimeMillis(),
        dismissTimestamp: Long? = null,
    ) {
        if (isFeatureEnabled) {
            registrar.stop(name, content, timestamp, dismissTimestamp)
        }
    }

    /** Ends tracking for all active Live Updates. */
    public fun clearAll() {
        if (isFeatureEnabled) {
            registrar.clearAll()
        }
    }

    /**
     * Returns a list with all active live updates
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public suspend fun getAllActiveUpdates(): List<LiveUpdate> = registrar.getAllActiveUpdates()

    /**
     * Cancels the notification associated with the given Live Update [name].
     *
     * This will not end tracking the Live Update and is a no-op for live updates that use custom
     * handlers.
     *
     * @param name The live update name.
     */
    internal fun cancel(name: String) {
        registrar.cancel(name)
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getComponentGroup(): Int = AirshipComponentGroups.LIVE_UPDATE

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun init() {
        super.init()

        channel.addChannelListener { updateLiveActivityEnablement() }
        privacyManager.addListener { updateLiveActivityEnablement() }

        pushManager.addPushListener { message, _ ->
            message.liveUpdatePayload
                ?.let { LiveUpdatePayload.fromJson(it) }
                ?.let { registrar.onLiveUpdatePushReceived(message, it) }
        }

        updateLiveActivityEnablement()
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun onComponentEnableChange(isEnabled: Boolean): Unit =
        updateLiveActivityEnablement()

    private fun updateLiveActivityEnablement() {
        if (isFeatureEnabled) {
            // Check for any active live Updates that have had their notifications cleared.
            // This makes sure we'll end the live update if the notification is dropped due
            // to an app upgrade or other cases where we don't get notified of the dismiss.
            registrar.stopLiveUpdatesForClearedNotifications()
        } else {
            // Clear all live updates.
            registrar.clearAll()
        }
    }

    public companion object {
        /**
         * Gets the shared [LiveUpdateManager] instance.
         *
         * @return the shared instance of `LiveUpdateManager`.
         */
        @JvmStatic
        public fun shared(): LiveUpdateManager =
            UAirship.shared().requireComponent(LiveUpdateManager::class.java)
    }
}
