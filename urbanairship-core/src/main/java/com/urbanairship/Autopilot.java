/* Copyright Airship and Contributors */

package com.urbanairship;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Autopilot allows UAirship.takeOff to be called without overriding the Application class. Typically,
 * UAirship.takeOff must be called in Application.onCreate() so that the Airship library is ready to
 * handle incoming events before intents are delivered to any application components. Calling takeOff
 * directly is the simplest integration, however some application frameworks do not provide a way to
 * extend the Application class. Autopilot allows you to provide your bootstrapping code in a way
 * that allows the library to lazily execute it.
 * <p>
 * Autopilot will be called before {@link Application#onCreate()} on the main process. If this is too early
 * for the application to handle takeOff, it can be delayed by overriding {@link #allowEarlyTakeOff(Context)}.
 * If delayed or if the application uses multiple processes, {@link #automaticTakeOff(Context)} must be called
 * at <em>all</em> application entry points (i.e., in the onCreate() method of all registered
 * Broadcast Receivers, Activities and Services).
 * <p>
 * The default {@link com.urbanairship.AirshipConfigOptions} will be created from the
 * {@code airshipconfig.properties} file from the assets. To provide a different config,
 * override {@link #createAirshipConfigOptions}.
 * <p>
 * The default Autopilot behavior will call takeOff and load airship config options from the {@code airshipconfig.properties}
 * file in the assets directory. To use autopilot, add the following entry to the application block
 * in the Application AndroidManifest.xml:
 * <pre>{@code
 *  <meta-data android:name="com.urbanairship.autopilot"
 *           android:value="com.urbanairship.Autopilot" /> }</pre>
 * <p>
 * <p>
 * Autopilot can be customized in order to load config from a different source or to customize the Airship
 * instance when it is ready. To customize Autopilot, extend the class and override either {@link #allowEarlyTakeOff(Context)},
 * {@link #onAirshipReady(UAirship)}, or {@link #createAirshipConfigOptions(Context)} methods. The class
 * must be non-abstract, public, and it should only have a single public, no-argument constructor.
 * Register the class by adding an entry to the application block of your manifest containing the
 * fully qualified class name of your Autopilot implementation:
 * <pre>{@code
 *  <meta-data android:name="com.urbanairship.autopilot"
 *           android:value="com.urbanairship.push.sample.SampleAutopilot" /> }</pre>
 */
public class Autopilot implements UAirship.OnReadyCallback {

    /**
     * The name of the AndroidManifest meta-data element used to hold the fully qualified class
     * name of the application's Autopilot implementation.
     */
    @NonNull
    public static final String AUTOPILOT_MANIFEST_KEY = "com.urbanairship.autopilot";

    private static final String TAG = "Airship Autopilot";

    private static boolean instanceCreationAttempted;

    private static Autopilot instance;


    /**
     * Starts the auto pilot takeOff process.
     *
     * @param context The application context.
     */
    public static void automaticTakeOff(@NonNull Context context) {
        automaticTakeOff((Application) context.getApplicationContext(), false);
    }

    /**
     * Starts the auto pilot takeOff process.
     *
     * @param application The application.
     */
    /*
     * automaticTakeOff might be called from different threads so we need to synchronize on the method
     * to prevent onCreateAirshipConfig from being called multiple times.
     */
    public static synchronized void automaticTakeOff(@NonNull Application application) {
        automaticTakeOff(application, false);
    }

    /**
     * Starts the auto pilot takeOff process.
     *
     * @param application The application.
     * @param earlyTakeoff Flag indicating if its an early takeOff or not (before Application.onCreate).
     */
    static synchronized void automaticTakeOff(@NonNull Application application, boolean earlyTakeoff) {
        if (UAirship.isFlying() || UAirship.isTakingOff()) {
            return;
        }

        AirshipAppBootstrap.init(application);

        if (!instanceCreationAttempted) {
            ApplicationInfo ai;
            try {
                ai = application.getPackageManager().getApplicationInfo(application.getPackageName(), PackageManager.GET_META_DATA);
                if (ai == null || ai.metaData == null) {
                    Log.e(TAG, "Unable to load app info.");
                    return;
                }
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Failed to get app info.", e);
                return;
            }

            instance = createAutopilotInstance(ai);
            instanceCreationAttempted = true;
        }

        if (instance == null) {
            // Not configured for autopilot
            return;
        }

        if (earlyTakeoff && !instance.allowEarlyTakeOff(application)) {
            return;
        }

        if (!instance.isReady(application)) {
            return;
        }

        AirshipConfigOptions options = instance.createAirshipConfigOptions(application);

        if (UAirship.isFlying() || UAirship.isTakingOff()) {
            Log.e(TAG, "Airship is flying before autopilot is able to take off. Make sure" +
                    "Autopilot.onCreateAirshipConfig is not calling takeOff directly.");
        }

        UAirship.takeOff(application, options, instance);
        instance = null;

    }

    /**
     * Creates the app's auto pilot instance.
     *
     * @param applicationInfo The application info.
     * @return An autopilot instance, or {@code null} if the app is not configured to use auto pilot
     * or if the class is unable to be created.
     */
    @Nullable
    private static Autopilot createAutopilotInstance(@NonNull ApplicationInfo applicationInfo) {
        String classname = applicationInfo.metaData.getString(AUTOPILOT_MANIFEST_KEY);

        if (classname == null) {
            return null;
        }

        try {
            Class<?> autopilotClass = Class.forName(classname);
            return (Autopilot) autopilotClass.newInstance();
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Class not found: " + classname);
        } catch (InstantiationException e) {
            Log.e(TAG, "Unable to create class: " + classname);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Unable to access class: " + classname);
        }

        return null;
    }

    /**
     * Implement this method to provide {@link com.urbanairship.AirshipConfigOptions} for takeOff. This method
     * may return null if the config should be loaded asynchronously from the {@code airshipconfig.properties}
     * file.
     *
     * @return The launch options. If null, the options will be loaded from
     * the <code>airshipconfig.properties</code> file.
     */
    @Nullable
    public AirshipConfigOptions createAirshipConfigOptions(@NonNull Context context) {
        return null;
    }

    /**
     * Checks if Autopilot is able to takeOff before {@link Application#onCreate()}.
     * <p>
     * Early takeOff will only be called on the main process. Apps that use multiple processes need
     * to make sure {@link #automaticTakeOff(Context)} is called in any other processes that use
     * Airship. If early takeOff is disabled, {@link #automaticTakeOff(Context)} must be called
     * at <em>all</em> application entry points (i.e., in the onCreate() method of all registered
     * Broadcast Receivers, Activities and Services).
     *
     * @param context The application context.
     * @return {@code true} to allow early takeOff, otherwise {@code false}.
     */
    public boolean allowEarlyTakeOff(@NonNull Context context) {
        return true;
    }

    /**
     * Called before {@link #automaticTakeOff(Context)} to make sure Autopilot is ready to takeOff.
     * <p>
     * Warning: If {@code false}, takeOff will not be called. Any synchronous access to UAirship
     * will throw an exception.
     *
     * @param context The application context.
     * @return {@code true} to allow takeOff, otherwise {@code false}.
     */
    public boolean isReady(@NonNull Context context) {
        return true;
    }

    /**
     * Called before the airship instance is returned in {@link UAirship#shared()}. Use this method
     * to perform any Airship customizations. This method is called on a background thread, but if airship
     * takes longer than 5 seconds to be ready it could cause ANRs within the application.
     *
     * @param airship The UAirship instance.
     */
    @Override
    public void onAirshipReady(@NonNull UAirship airship) {
        Logger.debug("Airship ready!");
    }

}
