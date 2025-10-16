/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.urbanairship.Autopilot.Companion.automaticTakeOff

/**
 * Autopilot allows Airship.takeOff to be called without overriding the Application class. Typically,
 * Airship.takeOff must be called in Application.onCreate() so that the Airship library is ready to
 * handle incoming events before intents are delivered to any application components. Calling takeOff
 * directly is the simplest integration, however some application frameworks do not provide a way to
 * extend the Application class. Autopilot allows you to provide your bootstrapping code in a way
 * that allows the library to lazily execute it.
 *
 *
 * Autopilot will be called before [Application.onCreate] on the main process. If this is too early
 * for the application to handle takeOff, it can be delayed by overriding [allowEarlyTakeOff].
 * If delayed or if the application uses multiple processes, [automaticTakeOff] must be called
 * at *all* application entry points (i.e., in the onCreate() method of all registered
 * Broadcast Receivers, Activities and Services).
 *
 *
 * The default [com.urbanairship.AirshipConfigOptions] will be created from the
 * `airshipconfig.properties` file from the assets. To provide a different config,
 * override [createAirshipConfigOptions].
 *
 *
 * The default Autopilot behavior will call takeOff and load airship config options from the `airshipconfig.properties`
 * file in the assets directory. To use autopilot, add the following entry to the application block
 * in the Application AndroidManifest.xml:
 * <pre>`<meta-data android:name="com.urbanairship.autopilot"
 * android:value="com.urbanairship.Autopilot" /> `</pre>
 *
 *
 *
 *
 * Autopilot can be customized in order to load config from a different source or to customize Airship
 * i when it is ready. To customize Autopilot, extend the class and override either [allowEarlyTakeOff],
 * [onAirshipReady], or [createAirshipConfigOptions] methods. The class
 * must be non-abstract, public, and it should only have a single public, no-argument constructor.
 * Register the class by adding an entry to the application block of your manifest containing the
 * fully qualified class name of your Autopilot implementation:
 * <pre>`<meta-data android:name="com.urbanairship.autopilot"
 * android:value="com.urbanairship.push.sample.SampleAutopilot" /> `</pre>
 */
public open class Autopilot public constructor() {

    /**
     * Implement this method to provide [com.urbanairship.AirshipConfigOptions] for takeOff. This method
     * may return null if the config should be loaded asynchronously from the `airshipconfig.properties`
     * file.
     *
     * @return The launch options. If null, the options will be loaded from
     * the `airshipconfig.properties` file.
     */
    public open fun createAirshipConfigOptions(context: Context): AirshipConfigOptions? {
        return null
    }

    /**
     * Checks if Autopilot is able to takeOff before [Application.onCreate].
     *
     *
     * Early takeOff will only be called on the main process. Apps that use multiple processes need
     * to make sure [automaticTakeOff] is called in any other processes that use
     * Airship. If early takeOff is disabled, [automaticTakeOff] must be called
     * at *all* application entry points (i.e., in the onCreate() method of all registered
     * Broadcast Receivers, Activities and Services).
     *
     * @param context The application context.
     * @return `true` to allow early takeOff, otherwise `false`.
     */
    public open fun allowEarlyTakeOff(context: Context): Boolean {
        return true
    }

    /**
     * Called before [automaticTakeOff] to make sure Autopilot is ready to takeOff.
     *
     *
     * Warning: If `false`, takeOff will not be called. Any synchronous access to Airship
     * will throw an exception.
     *
     * @param context The application context.
     * @return `true` to allow takeOff, otherwise `false`.
     */
    public open fun isReady(context: Context): Boolean {
        return true
    }

    /**
     * Called before the airship instance is used. Use this method to perform any Airship customizations.
     * This method is called on a background thread, but if airship
     * takes longer than 5 seconds to be ready it could cause ANRs within the application.
     *
     * @param airship The Airship instance.
     */
    @Deprecated("Use onAirshipReady(context: Context) instead.", ReplaceWith("onAirshipReady(context: Context)"))
    @Suppress("DEPRECATION")
    public open fun onAirshipReady(airship: UAirship) {
        UALog.d("Airship ready!")
    }

    /**
     * Called before the airship instance is used. Use this method to perform any Airship customizations.
     * This method is called on a background thread, but if airship takes longer than 5 seconds to be
     * ready it could cause ANRs within the application.y.
     *
     * @param context The application context.
     */
    public open fun onAirshipReady(context: Context) {
        // For backward compatibility, call the old method.
        @Suppress("DEPRECATION")
        onAirshipReady(UAirship.shared())
    }

    public companion object {

        /**
         * The name of the AndroidManifest meta-data element used to hold the fully qualified class
         * name of the application's Autopilot implementation.
         */
        public const val AUTOPILOT_MANIFEST_KEY: String = "com.urbanairship.autopilot"

        private const val TAG = "Airship Autopilot"

        private var instanceCreationAttempted = false

        private var instance: Autopilot? = null

        /**
         * Starts the auto pilot takeOff process.
         *
         * @param context The application context.
         */
        @JvmStatic
        public fun automaticTakeOff(context: Context) {
            automaticTakeOff(context.applicationContext as Application, false)
        }

        /**
         * Starts the auto pilot takeOff process.
         *
         * @param application The application.
         * automaticTakeOff might be called from different threads so we need to synchronize on the method
         * to prevent onCreateAirshipConfig from being called multiple times.
         */
        @Synchronized
        public fun automaticTakeOff(application: Application) {
            automaticTakeOff(application, false)
        }

        /**
         * Starts the auto pilot takeOff process.
         *
         * @param application The application.
         * @param earlyTakeoff Flag indicating if its an early takeOff or not (before Application.onCreate).
         */
        @Synchronized
        public fun automaticTakeOff(application: Application, earlyTakeoff: Boolean) {
            if (Airship.status != AirshipStatus.TAKEOFF_NOT_CALLED) {
                return
            }

            AirshipAppBootstrap.init(application)

            if (!instanceCreationAttempted) {
                val ai: ApplicationInfo
                try {
                    ai = application.packageManager.getApplicationInfo(
                        application.packageName, PackageManager.GET_META_DATA
                    )
                    if (ai.metaData == null) {
                        Log.e(TAG, "Unable to load app info.")
                        return
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(TAG, "Failed to get app info.", e)
                    return
                }

                instance = createAutopilotInstance(ai)
                instanceCreationAttempted = true
            }

            if (instance == null) {
                // Not configured for autopilot
                return
            }

            if (earlyTakeoff && instance?.allowEarlyTakeOff(application) == false) {
                return
            }

            if (instance?.isReady(application) == false) {
                return
            }

            val options = instance?.createAirshipConfigOptions(application)

            if (Airship.isFlyingOrTakingOff) {
                Log.e(
                    TAG,
                    "Airship is flying before autopilot is able to take off. Make sure" + "Autopilot.onCreateAirshipConfig is not calling takeOff directly."
                )
            }

            val callbackInstance = instance
            Airship.takeOff(application, options) {
                callbackInstance?.onAirshipReady(application)
            }
            instance = null
        }

        /**
         * Creates the app's auto pilot instance.
         *
         * @param applicationInfo The application info.
         * @return An autopilot instance, or `null` if the app is not configured to use auto pilot
         * or if the class is unable to be created.
         */
        private fun createAutopilotInstance(applicationInfo: ApplicationInfo): Autopilot? {
            val classname = applicationInfo.metaData.getString(AUTOPILOT_MANIFEST_KEY)
                ?: return null

            try {
                val autopilotClass = Class.forName(classname)
                return autopilotClass.getDeclaredConstructor().newInstance() as Autopilot
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Class not found: $classname")
            } catch (e: InstantiationException) {
                Log.e(TAG, "Unable to create class: $classname")
            } catch (e: IllegalAccessException) {
                Log.e(TAG, "Unable to access class: $classname")
            }

            return null
        }
    }
}
