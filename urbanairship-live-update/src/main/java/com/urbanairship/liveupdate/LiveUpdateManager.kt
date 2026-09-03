/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.PrivacyManager
import com.urbanairship.Airship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonMap
import com.urbanairship.liveupdate.data.LiveUpdateDatabase
import com.urbanairship.liveupdate.notification.LiveUpdatePayload
import com.urbanairship.push.PushManager
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.util.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Airship Live Updates.
 */
public class LiveUpdateManager

/** @hide */
@VisibleForTesting
internal constructor(
    context: Context,
    dataStore: PreferenceStore,
    config: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val pushManager: PushManager,
    channel: AirshipChannel,
    db: LiveUpdateDatabase = LiveUpdateDatabase.createDatabase(context, config),
    private val registrar: LiveUpdateRegistrar = LiveUpdateRegistrar(context, channel, db.liveUpdateDao()),
) : AirshipComponent(context, dataStore) {

    private val isFeatureEnabled: Boolean
        get() = privacyManager.isEnabled(PrivacyManager.Feature.PUSH)

    public constructor(
        context: Context,
        dataStore: PreferenceStore,
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
    public fun register(type: String, handler: LiveUpdateHandler) {
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
        timestamp: Instant = Clock.DEFAULT_CLOCK.now(),
        dismissTimestamp: Instant? = null,
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
     * @param dismissTimestamp Optional timestamp, when to end this Live Update (default: null).
     */
    @JvmOverloads
    public fun update(
        name: String,
        content: JsonMap,
        timestamp: Instant = Clock.DEFAULT_CLOCK.now(),
        dismissTimestamp: Instant? = null,
    ) {
        if (isFeatureEnabled) {
            registrar.update(name, content, timestamp, dismissTimestamp)
        }
    }

    /**
     * Ends tracking for the Live Update with the given [name].
     *
     * @param name The live update name.
     * @param content A [JsonMap] with final updated content.
     * @param timestamp The end timestamp, used to filter out-of-order events (default: now).
     * @param dismissTimestamp Optional timestamp, when to end this Live Update (default: null).
     */
    @JvmOverloads
    public fun end(
        name: String,
        content: JsonMap? = null,
        timestamp: Instant = Clock.DEFAULT_CLOCK.now(),
        dismissTimestamp: Instant? = null,
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
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public suspend fun getAllActiveUpdates(): List<LiveUpdate> = registrar.getAllActiveUpdates()

    /**
     * Returns a list with all active live updates
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun getAllActiveUpdatesPendingResult(): PendingResult<List<LiveUpdate>> {
        val pendingResult = PendingResult<List<LiveUpdate>>()
        CoroutineScope(AirshipDispatchers.IO).launch {
            pendingResult.setResult(getAllActiveUpdates())
        }
        return pendingResult
    }

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
    public override fun init() {
        super.init()

        // Clear local Live Update state if the Push feature is turned off.
        privacyManager.addListener { clearIfFeatureDisabled() }

        pushManager.addPushListener { message, _ ->
            message.liveUpdatePayload
                ?.let { LiveUpdatePayload.fromJson(it) }
                ?.let { registrar.onLiveUpdatePushReceived(message, it) }
        }

        clearIfFeatureDisabled()
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun onAirshipReady() {
        super.onAirshipReady()

        if (isFeatureEnabled) {
            // Clean up any Live Updates that have gone stale and are no longer displayed.
            // We need handlers to be registered before this call, which is why this is in
            // onAirshipReady instead of the init method above.
            registrar.endStaleLiveUpdates()
        }
    }

    private fun clearIfFeatureDisabled() {
        if (!isFeatureEnabled) {
            registrar.clearAll()
        }
    }

    public companion object {
        /**
         * Gets the shared [LiveUpdateManager] instance.
         *
         * This method is the static entry point for Java clients. It delegates
         * access to the primary [Airship] singleton, ensuring the component is available
         * and fully initialized before returning.
         *
         * @return the shared instance of `LiveUpdateManager`.
         * @throws IllegalStateException if [Airship.takeOff] has not been called.
         *
         * @see Airship.liveUpdateManager For the corresponding Kotlin extension property.
         */
        @JvmStatic
        public fun shared(): LiveUpdateManager =
            Airship.liveUpdateManager
    }
}

/**
 * Provides access to the [LiveUpdateManager] module features via the main [Airship] singleton.
 *
 *
 * Access is thread-safe. Calling this property before Airship is finished taking off
 * will block the calling thread until initialization is complete.
 *
 * @return The LiveUpdateManager instance.
 * @throws IllegalStateException if [Airship.takeOff] has not been called.
 *
 * @see LiveUpdateManager.shared For the corresponding Java static access pattern.
 */
public val Airship.liveUpdateManager: LiveUpdateManager
    get() {
        return Airship.requireComponent(LiveUpdateManager::class.java)
    }
