/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.content.Context
import android.os.Looper
import java.util.Locale
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.actions.DeepLinkListener
import com.urbanairship.analytics.Analytics
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.contacts.Contact
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.push.PushManager

/**
 * UAirship has been renamed to [Airship]. This class is a temporary compatibility layer and will be
 * removed in a future release.
 *
 * @see Airship
 * @deprecated Use [Airship] instead.
 */
@Deprecated(
    message = "UAirship has been renamed to Airship. Please migrate.",
    replaceWith = ReplaceWith("Airship")
)
public class UAirship private constructor(private val airship: Airship) {

    public var deepLinkListener: DeepLinkListener?
        get() = airship.deepLinkListener
        set(value) { airship.deepLinkListener = value }

    public var airshipConfigOptions: AirshipConfigOptions by airship::airshipConfigOptions

    public val actionRegistry: ActionRegistry get() = airship.actionRegistry
    public val analytics: Analytics get() = airship.analytics
    public val pushManager: PushManager get() = airship.pushManager
    public val channel: AirshipChannel get() = airship.channel
    public val urlAllowList: UrlAllowList get() = airship.urlAllowList
    public val privacyManager: PrivacyManager get() = airship.privacyManager
    public val contact: Contact get() = airship.contact
    public val permissionsManager: PermissionsManager get() = airship.permissionsManager
    public val platformType: Platform get() = Platform.fromAirshipPlatform(airship.platformType)
    public val locale: Locale get() = airship.locale

    public fun deepLink(deepLink: String): Boolean = airship.deepLink(deepLink)
    public fun setLocaleOverride(locale: Locale?) = airship.setLocaleOverride(locale)

    /**
     * @see Airship.Platform
     * @deprecated Use [Airship.Platform] instead.
     */
    @Deprecated("Use Airship.Platform instead.", replaceWith = ReplaceWith("Airship.Platform"))
    public enum class Platform(internal val airshipPlatform: Airship.Platform) {
        AMAZON(Airship.Platform.AMAZON),
        ANDROID(Airship.Platform.ANDROID),
        UNKNOWN(Airship.Platform.UNKNOWN);

        public val stringValue: String
            get() = airshipPlatform.stringValue

        internal companion object {
            fun fromAirshipPlatform(platform: Airship.Platform): Platform {
                return entries.firstOrNull { it.airshipPlatform == platform } ?: UNKNOWN
            }
        }
    }

    /**
     * @see Airship.OnReadyCallback
     * @deprecated Use [Airship.OnReadyCallback] instead.
     */
    @Deprecated("Use Airship.OnReadyCallback instead.", replaceWith = ReplaceWith("Airship.OnReadyCallback"))
    public fun interface OnReadyCallback {
        public fun onAirshipReady(airship: UAirship)
    }

    @Deprecated(
        message = "UAirship has been renamed to Airship. Please migrate.",
        replaceWith = ReplaceWith("Airship")
    )
    public companion object {
        public const val ACTION_AIRSHIP_READY: String = Airship.ACTION_AIRSHIP_READY
        public const val EXTRA_CHANNEL_ID_KEY: String = Airship.EXTRA_CHANNEL_ID_KEY
        public const val EXTRA_PAYLOAD_VERSION_KEY: String = Airship.EXTRA_PAYLOAD_VERSION_KEY
        public const val EXTRA_APP_KEY_KEY: String = Airship.EXTRA_APP_KEY_KEY
        public const val EXTRA_AIRSHIP_DEEP_LINK_SCHEME: String = Airship.EXTRA_AIRSHIP_DEEP_LINK_SCHEME

        // PROXY COMPANION PROPERTIES
        public var isFlying: Boolean
            get() = Airship.isFlying
            set(value) { Airship.isFlying = value }

        public var isTakingOff: Boolean
            get() = Airship.isTakingOff
            set(value) { Airship.isTakingOff = value }

        public var isMainProcess: Boolean
            get() = Airship.isMainProcess
            set(value) { Airship.isMainProcess = value }

        public var LOG_TAKE_OFF_STACKTRACE: Boolean by Airship.Companion::LOG_TAKE_OFF_STACKTRACE

        public val applicationContext: Context
            get() = Airship.applicationContext

        @JvmStatic
        @Throws(IllegalStateException::class)
        public fun shared(): UAirship = UAirship(Airship.shared())

        @JvmStatic
        public fun shared(callback: OnReadyCallback): Cancelable {
            return Airship.shared { airship ->
                callback.onAirshipReady(UAirship(airship))
            }
        }

        @JvmStatic
        public fun shared(looper: Looper?, callback: OnReadyCallback?): Cancelable {
            return Airship.shared(looper) { airship ->
                callback?.onAirshipReady(UAirship(airship))
            }
        }

        @JvmStatic
        public fun waitForTakeOff(millis: Long): UAirship? {
            return Airship.waitForTakeOff(millis)?.let { UAirship(it) }
        }

        @JvmStatic
        public fun takeOff(application: Application) {
            Airship.takeOff(application)
        }

        @JvmStatic
        public fun takeOff(application: Application, readyCallback: OnReadyCallback?) {
            Airship.takeOff(application, readyCallback?.let { cb ->
                Airship.OnReadyCallback { airship -> cb.onAirshipReady(UAirship(airship)) }
            })
        }

        @JvmStatic
        public fun takeOff(application: Application, options: AirshipConfigOptions?) {
            Airship.takeOff(application, options)
        }

        @JvmStatic
        public fun takeOff(
            application: Application,
            options: AirshipConfigOptions?,
            readyCallback: OnReadyCallback?
        ) {
            Airship.takeOff(application, options, readyCallback?.let { callback ->
                Airship.OnReadyCallback { airship -> callback.onAirshipReady(UAirship(airship)) }
            })
        }

        @JvmStatic
        public fun getVersion(): String = Airship.getVersion()
    }
}
