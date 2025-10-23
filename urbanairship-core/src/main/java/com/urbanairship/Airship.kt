/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.content.Intent
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.urbanairship.Airship.deepLink
import com.urbanairship.Airship.onReady
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.actions.DeepLinkListener
import com.urbanairship.analytics.Analytics
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.contacts.Contact
import com.urbanairship.images.ImageLoader
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.locale.LocaleManager
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.push.PushManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** The Airship singleton provides access to all of the Airship services. */
public object Airship {

    public fun interface OnReadyCallback {
        /**
         * Called when Airship is finished taking off and is ready for use.
         *
         * @param airship The Airship singleton instance.
         */
        public fun onAirshipReady(airship: Airship)
    }

    private val takeOffCompletedFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val _statusFlow: MutableStateFlow<AirshipStatus> = MutableStateFlow(AirshipStatus.TAKEOFF_NOT_CALLED)

    /** The current status of the Airship SDK. */
    public val statusFlow: StateFlow<AirshipStatus> = _statusFlow.asStateFlow()

    /**
     * If `true`, a stack trace will be logged when `takeOff` is called to help diagnose
     * issues with multiple takeOff calls. Defaults to `false`.
     */
    @JvmField
    @Volatile
    public var logTakeOffStackTrace: Boolean = false

    /** The current Airship SDK version. */
    @JvmStatic
    public val version: String
        get() = BuildConfig.AIRSHIP_VERSION

    // Used to prevent a deadlock if someone calls `waitForReady` within the takeOff onReady callback
    private val waitForReadyLock: ReentrantLock = ReentrantLock()
    private var skipWaitForReady: Boolean = false

    private val airshipLock = ReentrantLock()
    private var airshipInstance: AirshipInstance? = null
    internal fun requireReadyInstance(): AirshipInstance {
        airshipLock.withLock {
            val instance =
                airshipInstance ?: throw IllegalStateException("TakeOff must be called first.")

            if (status != AirshipStatus.IS_FLYING) {
                runBlocking {
                    statusFlow.first { it == AirshipStatus.IS_FLYING }
                }
            }
            return instance
        }
    }

    internal val components: List<AirshipComponent>
        get() = requireReadyInstance().components.value

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun <T : AirshipComponent?> requireComponent(clazz: Class<T>): T {
        return requireReadyInstance().requireComponent(clazz)
    }

    private val onReadyScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * The application's [AirshipConfigOptions].
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val airshipConfigOptions: AirshipConfigOptions
        get() = requireReadyInstance().airshipConfigOptions

    /**
     * The application instance.
     *
     * Access is thread-safe. Unlike other component accessors, this property will not block
     * until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val application: Application
        get() {
            // Special handler for Application to avoid blocking for ready
            airshipLock.withLock {
                val instance = airshipInstance
                    ?: throw IllegalStateException("TakeOff must be called first.")

                return instance.application
            }
        }

    /**
     * The shared [Analytics] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val analytics: Analytics
        get() = requireReadyInstance().requireComponent()

    /**
     * The shared [PushManager] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val push: PushManager
        get() = requireReadyInstance().requireComponent()

    /**
     * The shared [AirshipChannel] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val channel: AirshipChannel
        get() = requireReadyInstance().requireComponent()

    /**
     * The shared [Contact] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val contact: Contact
        get() = requireReadyInstance().requireComponent()

    /**
     * The shared [PrivacyManager] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val privacyManager: PrivacyManager
        get() = requireReadyInstance().privacyManager

    /**
     * The shared [PermissionsManager] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val permissionsManager: PermissionsManager
        get() = requireReadyInstance().permissionsManager

    /**
     * The shared [LocaleManager] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val localeManager: LocaleManager
        get() = requireReadyInstance().localeManager

    /**
     * The shared [AirshipInputValidation.Validator] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     *
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val inputValidator: AirshipInputValidation.Validator
        get() = requireReadyInstance().inputValidator

    /**
     * The shared [ImageLoader] instance.
     *
     * @hide
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val imageLoader: ImageLoader
        get() = requireReadyInstance().imageLoader

    /**
     * The shared [ChannelCapture] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val channelCapture: ChannelCapture
        get() = requireReadyInstance().requireComponent()

    /**
     * The shared [UrlAllowList] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val urlAllowList: UrlAllowList
        get() = requireReadyInstance().urlAllowList

    /**
     * The shared deep link listener.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public var deepLinkListener: DeepLinkListener?
        get() = requireReadyInstance().deepLinkListener
        set(value) {
            requireReadyInstance().deepLinkListener = value
        }

    /**
     * The shared [ActionRegistry] instance.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val actionRegistry: ActionRegistry
        get() = requireReadyInstance().actionRegistry

    /**
     * The detected platform.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val platform: Platform
        get() = requireReadyInstance().runtimeConfig.platform

    /** The current [status][AirshipStatus] of the Airship SDK. */
    public val status: AirshipStatus
        get() = statusFlow.value

    /** `true` if the SDK is fully initialized and ready, `false` otherwise. */
    @JvmStatic
    public val isFlying : Boolean
        get() = status == AirshipStatus.IS_FLYING

    /** `true` if `takeOff` has been called but the SDK is still initializing, `false` otherwise. */
    @JvmStatic
    public val isTakingOff : Boolean
        get() = status == AirshipStatus.TAKING_OFF

    /** `true` if the SDK is either initialized or initializing, `false` otherwise. */
    public val isFlyingOrTakingOff: Boolean
        get() {
            val status = status
            return status == AirshipStatus.IS_FLYING || status == AirshipStatus.TAKING_OFF
        }

    /**
     * The runtime configuration.
     *
     * Access is thread-safe. This property will block until Airship is ready.
     * @throws IllegalStateException if `takeOff` has not been called.
     */
    @JvmStatic
    public val runtimeConfig: AirshipRuntimeConfig
        get() = requireReadyInstance().runtimeConfig

    /**
     * Initializes the Airship SDK.
     *
     * Called manually during `Application.onCreate` or automatically using [Autopilot].
     *
     * @param application The application instance.
     * @param onReadyCallback Called on a background thread. The work done in this callback should be as quick as possible as
     * it will block Airship from being used including `Airship.waitForReady()` and other `onReady` callbacks
     * from executing.
     */
    @JvmStatic
    @MainThread
    public fun takeOff(
        application: Application,
        onReadyCallback: OnReadyCallback
    ) {
        actualTakeOff(application, null) {
            onReadyCallback.onAirshipReady(this)
        }
    }

    /**
     * Initializes the Airship SDK.
     *
     * Called manually during `Application.onCreate` or automatically using [Autopilot].
     *
     * @param application The application instance.
     * @param options The Airship config options.
     * @param onReadyCallback Called on a background thread. The work done in this callback should be as quick as possible as
     * it will block Airship from being used including `Airship.waitForReady()` and other `onReady` callbacks
     * from executing.
     */
    @JvmStatic
    @MainThread
    public fun takeOff(
        application: Application,
        options: AirshipConfigOptions?,
        onReadyCallback: OnReadyCallback
    ) {
        actualTakeOff(application, options) {
            onReadyCallback.onAirshipReady(this)
        }
    }

    /**
     * Initializes the Airship SDK.
     *
     * Called manually during `Application.onCreate` or automatically using [Autopilot].
     *
     * @param application The application instance.
     * @param options The Airship config options.
     * @param onReady Called on a background thread. The work done in this callback should be as quick as possible as
     * it will block Airship from being used including `Airship.waitForReady()` and other `onReady` callbacks
     * from executing.
     */
    @JvmStatic
    @JvmOverloads
    @MainThread
    public fun takeOff(
        application: Application,
        options: AirshipConfigOptions? = null,
        onReady: (Airship.() -> Unit)? = null
    ) {
        actualTakeOff(application, options,  onReady)
    }

    private fun actualTakeOff(
        application: Application,
        options: AirshipConfigOptions? = null,
        onReady: (Airship.() -> Unit)? = null
    ) {
        val instance = airshipLock.withLock {
            check(airshipInstance == null) { "Takeoff can only be called once!" }
            check(status == AirshipStatus.TAKEOFF_NOT_CALLED) { "Unexpected Airship status!" }

            _statusFlow.update { AirshipStatus.TAKING_OFF }

            AirshipInstance(application).also {
                airshipInstance = it
            }
        }

        instance.takeOff(
            configOptions = options,
            logTakeOffStackTrace = logTakeOffStackTrace,
        ) { config ->
            // Update status to IS_FLYING first to prevent blocking access to components
            _statusFlow.update { AirshipStatus.IS_FLYING }

            // Call onReady to allow customizing Airship
            waitForReadyLock.withLock {
                skipWaitForReady = true
                onReady?.invoke(Airship)
                skipWaitForReady = false
            }

            // Send ready broadcast, some partner plugins use this to integrate with our SDK
            sendReadyBroadcast(application, config.extendedBroadcastsEnabled)

            // Resume waitForReady and starts onReady callbacks
            takeOffCompletedFlow.update { true }
        }
    }

    private fun sendReadyBroadcast(application: Application, extendedIntent: Boolean) {
        val readyIntent = Intent(ACTION_AIRSHIP_READY)
            .setPackage(application.packageName)
            .addCategory(application.packageName)

        if (extendedIntent) {
            readyIntent.putExtra(EXTRA_CHANNEL_ID_KEY, channel.id)
                .putExtra(EXTRA_APP_KEY_KEY, runtimeConfig.configOptions.appKey)
                .putExtra(EXTRA_PAYLOAD_VERSION_KEY, 1)
        }

        application.sendBroadcast(readyIntent)
    }

    /**
     * Waits for the SDK to be ready.
     *
     * @param duration The max time to wait. If `null`, this method will wait indefinitely.
     * @return `true` if the SDK is ready, `false` if the timeout was reached.
     */
    @JvmSynthetic
    public suspend fun waitForReady(duration: Duration? = null): Boolean {
        if (takeOffCompletedFlow.value) {
            return true
        }

        return if (duration != null) {
            withTimeoutOrNull(duration) {
                takeOffCompletedFlow.first { it }
                true
            } ?: false
        } else {
            takeOffCompletedFlow.first { it  }
            true
        }
    }

    /**
     * Waits for the SDK to be ready. This method will block the calling thread.
     *
     * @param duration The max time to wait. If `null`, this method will wait indefinitely.
     * @return `true` if the SDK is ready, `false` if the timeout was reached.
     */
    @JvmStatic
    @WorkerThread
    public fun waitForReadyBlocking(duration: Duration? = null): Boolean {
        if (takeOffCompletedFlow.value) {
            return true
        }

        // Prevents a deadlock when calling waitForReadyBlocking from takeOff.onReady
        if (waitForReadyLock.withLock { skipWaitForReady }) {
            return true
        }

        return runBlocking {
            waitForReady(duration)
        }
    }

    /**
     * Adds a callback to be invoked when the SDK is ready. The callback will always be dispatched
     * on `Dispatchers.Main`.
     *
     * @param onReady The lambda to be invoked when Airship is ready.
     */
    @JvmSynthetic
    public fun onReady(onReady: Airship.() -> Unit) {
        onReadyScope.launch {
            takeOffCompletedFlow.first { it }
            onReady()
        }
    }

    /**
     * Adds a callback to be invoked when the SDK is ready. The callback will always be dispatched
     * on `Dispatchers.Main`.
     *
     * @param callback The callback to be notified when Airship is ready.
     */
    @JvmStatic
    public fun onReady(callback: OnReadyCallback) {
        onReady {
            callback.onAirshipReady(this)
        }
    }

    /**
     * Processes a deep link.
     *
     * @param deepLink The deep link.
     * @return `true` if the deep link was handled, `false` otherwise.
     */
    @MainThread
    @JvmStatic
    public fun deepLink(deepLink: String): Boolean {
        return requireReadyInstance().deepLink(deepLink)
    }

    @VisibleForTesting
    internal fun land() {
        airshipLock.withLock {
            airshipInstance?.tearDown()
            _statusFlow.update {  AirshipStatus.TAKEOFF_NOT_CALLED }
            airshipInstance = null
            takeOffCompletedFlow.update { false }
        }
    }

    /** Broadcast that is sent when Airship is finished taking off. */
    public const val ACTION_AIRSHIP_READY: String = "com.urbanairship.AIRSHIP_READY"

    public const val EXTRA_CHANNEL_ID_KEY: String = "channel_id"

    public const val EXTRA_PAYLOAD_VERSION_KEY: String = "payload_version"

    public const val EXTRA_APP_KEY_KEY: String = "app_key"

    public const val EXTRA_AIRSHIP_DEEP_LINK_SCHEME: String = "uairship"
}
