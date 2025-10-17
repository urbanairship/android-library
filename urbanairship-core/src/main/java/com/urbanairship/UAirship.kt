/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.content.Context
import android.os.Looper
import com.urbanairship.actions.ActionRegistry
import com.urbanairship.actions.DeepLinkListener
import com.urbanairship.analytics.Analytics
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.contacts.Contact
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.push.PushManager
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import com.urbanairship.ChannelCapture;

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
@Suppress("DEPRECATION")
public class UAirship {

    /**
     * @see Airship.deepLinkListener
     * @deprecated Use [Airship.deepLinkListener] instead.
     */
    @Deprecated("Use Airship.deepLinkListener instead.", replaceWith = ReplaceWith("Airship.deepLinkListener"))
    public var deepLinkListener: DeepLinkListener?
        get() = Airship.deepLinkListener
        set(value) { Airship.deepLinkListener = value }

    /**
     * @see Airship.airshipConfigOptions
     * @deprecated Use [Airship.airshipConfigOptions] instead.
     */
    @Deprecated("Use Airship.airshipConfigOptions instead.", replaceWith = ReplaceWith("Airship.airshipConfigOptions"))
    public val airshipConfigOptions: AirshipConfigOptions get() = Airship.airshipConfigOptions

    /**
     * @see Airship.actionRegistry
     * @deprecated Use [Airship.actionRegistry] instead.
     */
    @Deprecated("Use Airship.actionRegistry instead.", replaceWith = ReplaceWith("Airship.actionRegistry"))
    public val actionRegistry: ActionRegistry get() = Airship.actionRegistry

    /**
     * @see Airship.analytics
     * @deprecated Use [Airship.analytics] instead.
     */
    @Deprecated("Use Airship.analytics instead.", replaceWith = ReplaceWith("Airship.analytics"))
    public val analytics: Analytics get() = Airship.analytics

    /**
     * @see Airship.push
     * @deprecated Use [Airship.push] instead.
     */
    @Deprecated("Use Airship.push instead.", replaceWith = ReplaceWith("Airship.push"))
    public val pushManager: PushManager get() = Airship.push

    /**
     * @see Airship.channel
     * @deprecated Use [Airship.channel] instead.
     */
    @Deprecated("Use Airship.channel instead.", replaceWith = ReplaceWith("Airship.channel"))
    public val channel: AirshipChannel get() = Airship.channel

    /**
     * @see Airship.urlAllowList
     * @deprecated Use [Airship.urlAllowList] instead.
     */
    @Deprecated("Use Airship.urlAllowList instead.", replaceWith = ReplaceWith("Airship.urlAllowList"))
    public val urlAllowList: UrlAllowList get() = Airship.urlAllowList

    /**
     * @see Airship.privacyManager
     * @deprecated Use [Airship.privacyManager] instead.
     */
    @Deprecated("Use Airship.privacyManager instead.", replaceWith = ReplaceWith("Airship.privacyManager"))
    public val privacyManager: PrivacyManager get() = Airship.privacyManager

    /**
     * @see Airship.contact
     * @deprecated Use [Airship.contact] instead.
     */
    @Deprecated("Use Airship.contact instead.", replaceWith = ReplaceWith("Airship.contact"))
    public val contact: Contact get() = Airship.contact

    /**
     * @see Airship.permissionsManager
     * @deprecated Use [Airship.permissionsManager] instead.
     */
    @Deprecated("Use Airship.permissionsManager instead.", replaceWith = ReplaceWith("Airship.permissionsManager"))
    public val permissionsManager: PermissionsManager get() = Airship.permissionsManager

    /**
     * @see Airship.channelCapture
     * @deprecated Use [Airship.channelCapture] instead.
     */
    @Deprecated("Use Airship.channelCapture instead.", replaceWith = ReplaceWith("Airship.channelCapture"))
    public val channelCapture: ChannelCapture get() = Airship.channelCapture

    /**
     * @see Airship.platform
     * @deprecated Use [Airship.platform] instead.
     */
    @Deprecated("Use Airship.platform instead.", replaceWith = ReplaceWith("Airship.platform"))
    public val platformType: Platform get() = Airship.platform

    /**
     * @see Airship.deepLink
     * @deprecated Use [Airship.deepLink] instead.
     */
    @Deprecated("Use Airship.deepLink() instead.", replaceWith = ReplaceWith("Airship.deepLink(deepLink)"))
    public fun deepLink(deepLink: String): Boolean = Airship.deepLink(deepLink)

    @Deprecated("Use Airship.localeManager.setLocaleOverride(locale) instead")
    public fun setLocaleOverride(locale: Locale?): Unit = Airship.localeManager.setLocaleOverride(locale)

    @Deprecated("Use Airship.localeManager.locale instead")
    public val locale: Locale get() = Airship.localeManager.locale

    /**
     * @see Airship.OnReadyCallback
     * @deprecated Use [Airship.OnReadyCallback] instead.
     */
    public fun interface OnReadyCallback {
        /**
         * @see Airship.OnReadyCallback.onAirshipReady
         * @deprecated Use [Airship.OnReadyCallback.onAirshipReady] instead.
         */
        public fun onAirshipReady(airship: UAirship)
    }

    @Deprecated(
        message = "UAirship has been renamed to Airship. Please migrate.",
        replaceWith = ReplaceWith("Airship")
    )
    public companion object {
        /**
         * @see Airship.ACTION_AIRSHIP_READY
         * @deprecated Use [Airship.ACTION_AIRSHIP_READY] instead.
         */
        public const val ACTION_AIRSHIP_READY: String = Airship.ACTION_AIRSHIP_READY
        /**
         * @see Airship.EXTRA_CHANNEL_ID_KEY
         * @deprecated Use [Airship.EXTRA_CHANNEL_ID_KEY] instead.
         */
        public const val EXTRA_CHANNEL_ID_KEY: String = Airship.EXTRA_CHANNEL_ID_KEY
        /**
         * @see Airship.EXTRA_PAYLOAD_VERSION_KEY
         * @deprecated Use [Airship.EXTRA_PAYLOAD_VERSION_KEY] instead.
         */
        public const val EXTRA_PAYLOAD_VERSION_KEY: String = Airship.EXTRA_PAYLOAD_VERSION_KEY
        /**
         * @see Airship.EXTRA_APP_KEY_KEY
         * @deprecated Use [Airship.EXTRA_APP_KEY_KEY] instead.
         */
        public const val EXTRA_APP_KEY_KEY: String = Airship.EXTRA_APP_KEY_KEY
        /**
         * @see Airship.EXTRA_AIRSHIP_DEEP_LINK_SCHEME
         * @deprecated Use [Airship.EXTRA_AIRSHIP_DEEP_LINK_SCHEME] instead.
         */
        public const val EXTRA_AIRSHIP_DEEP_LINK_SCHEME: String = Airship.EXTRA_AIRSHIP_DEEP_LINK_SCHEME

        /**
         * @see Airship.isFlying
         * @deprecated Use [Airship.isFlying] instead.
         */
        @Deprecated("Use Airship.isFlying instead.", replaceWith = ReplaceWith("Airship.isFlying"))
        public val isFlying: Boolean
            get() = Airship.status == AirshipStatus.IS_FLYING

        /**
         * @see Airship.isTakingOff
         * @deprecated Use [Airship.isTakingOff] instead.
         */
        @Deprecated("Use Airship.isTakingOff instead.", replaceWith = ReplaceWith("Airship.isTakingOff"))
        public val isTakingOff: Boolean
            get() = Airship.status == AirshipStatus.TAKING_OFF

        /**
         * @see Airship.application
         * @deprecated Use [Airship.application] instead.
         */
        @Deprecated("Use Airship.application instead.", replaceWith = ReplaceWith("Airship.application"))
        public val applicationContext: Context
            get() = Airship.application

        private val instance = UAirship()

        /**
         * @see Airship
         * @deprecated Use [Airship] instead.
         */
        @Deprecated("Use Airship instead.", replaceWith = ReplaceWith("Airship"))
        @JvmStatic
        @Throws(IllegalStateException::class)
        public fun shared(): UAirship {
            check(Airship.status != AirshipStatus.TAKEOFF_NOT_CALLED) { "Takeoff not called!" }
            return instance
        }

        /**
         * @see Airship.onReady
         * @deprecated Use [Airship.onReady] instead.
         */
        @Deprecated("Use Airship.onReady() instead.", replaceWith = ReplaceWith("Airship.onReady(callback)"))
        @JvmStatic
        public fun shared(callback: OnReadyCallback): Cancelable {
            val cancelableOperation: CancelableOperation = object : CancelableOperation(null) {
                public override fun onRun() {
                    callback.onAirshipReady(shared())
                }
            }

            Airship.onReady {
                cancelableOperation.run()
            }
            return cancelableOperation
        }

        /**
         * @see Airship.onReady
         * @deprecated Use [Airship.onReady] instead.
         */
        @Deprecated("Use Airship.onReady() instead.", replaceWith = ReplaceWith("Airship.onReady(callback)"))
        @JvmStatic
        public fun shared(looper: Looper?, callback: OnReadyCallback): Cancelable {
            val cancelableOperation: CancelableOperation = object : CancelableOperation(null) {
                public override fun onRun() {
                    callback.onAirshipReady(shared())
                }
            }

            Airship.onReady {
                cancelableOperation.run()
            }
            return cancelableOperation
        }

        /**
         * @see Airship.waitForReadyBlocking
         * @deprecated Use [Airship.waitForReadyBlocking] instead.
         */
        @Deprecated("Use Airship.waitForReadyBlocking() instead.", replaceWith = ReplaceWith("Airship.waitForReadyBlocking(millis.milliseconds)"))
        @JvmStatic
        public fun waitForTakeOff(millis: Long): UAirship? {
            return if (Airship.waitForReadyBlocking(millis.milliseconds)) {
                shared()
            } else {
                null
            }
        }

        /**
         * @see Airship.takeOff
         * @deprecated Use [Airship.takeOff] instead.
         */
        @Deprecated("Use Airship.takeOff() instead.", replaceWith = ReplaceWith("Airship.takeOff(application)"))
        @JvmStatic
        public fun takeOff(application: Application) {
            Airship.takeOff(application)
        }

        /**
         * @see Airship.takeOff
         * @deprecated Use [Airship.takeOff] instead.
         */
        @Deprecated("Use Airship.takeOff() instead.", replaceWith = ReplaceWith("Airship.takeOff(application, onReady = {})"))
        @JvmStatic
        public fun takeOff(application: Application, readyCallback: OnReadyCallback?) {
            Airship.takeOff(application) {
                readyCallback?.onAirshipReady(shared())
            }
        }

        /**
         * @see Airship.takeOff
         * @deprecated Use [Airship.takeOff] instead.
         */
        @Deprecated("Use Airship.takeOff() instead.", replaceWith = ReplaceWith("Airship.takeOff(application, options)"))
        @JvmStatic
        public fun takeOff(application: Application, options: AirshipConfigOptions?) {
            Airship.takeOff(application, options)
        }

        /**
         * @see Airship.takeOff
         * @deprecated Use [Airship.takeOff] instead.
         */
        @Deprecated("Use Airship.takeOff() instead.", replaceWith = ReplaceWith("Airship.takeOff(application, options, onReady = {})"))
        @JvmStatic
        public fun takeOff(
            application: Application,
            options: AirshipConfigOptions?,
            readyCallback: OnReadyCallback?
        ) {
            Airship.takeOff(application, options) {
                readyCallback?.onAirshipReady(shared())
            }
        }

        /**
         * @see Airship.version
         * @deprecated Use [Airship.version] instead.
         */
        @Deprecated("Use Airship.version instead.", replaceWith = ReplaceWith("Airship.version"))
        @JvmStatic
        public fun getVersion(): String = Airship.version
    }
}
