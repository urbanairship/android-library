package com.urbanairship;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Autopilot allows you to defer calling UAirship.takeOff until after the application has been created. Typically, UAirship.takeOff must
 * be called in Application.onCreate() so that the Urban Airship library is ready to handle incoming events before intents are delivered to
 * any application components. Calling takeOff directly is the simplest integration, however some application frameworks do not provide a
 * way to extend the Application class. Autopilot allows you to provide your bootstrapping code in a way that allows the library to
 * lazily execute it.
 * <p/>
 * Extend this class and implement the abstract methods, then call Autopilot.automaticTakeOff() at <em>all</em> application
 * entry points (i.e., in the onCreate() method of all registered Broadcast Receivers, Activities and Services). The default
 * {@link com.urbanairship.AirshipConfigOptions} will be created from the airshipconfig.properties file from the assets. To provide
 * a custom {@link com.urbanairship.AirshipConfigOptions}, override {@link #createAirshipConfigOptions}.
 * <p/>
 * Your subclass <em>must</em> not be abstract and <em>may</em> provide a default constructor that will be invoked with Class.newInstance().
 * <p/>
 * Add an entry to the application block of your manifest containing the fully qualified class name of your Autopilot implementation.
 * The meta-data name must be set to "com.urbanairship.autopilot" (Autopilot.AUTOPILOT_MANIFEST_KEY).
 * e.g.,
 * <pre>{@code <meta-data android:name="com.urbanairship.autopilot" android:value="com.urbanairship.push.sample.SampleAutopilot" /> }</pre>
 * <p/>
 * If your app uses Proguard obfuscation, you will need to add an exclusion to proguard.cfg for your Autopilot class:
 * <pre>{@code -keep public class * extends com.urbanairship.Autopilot}</pre>
 */
public abstract class Autopilot implements UAirship.OnReadyCallback {

    /**
     * The name of the AndroidManifest meta-data element used to hold the fully qualified class
     * name of the application's Autopilot implementation.
     */
    public static final String AUTOPILOT_MANIFEST_KEY = "com.urbanairship.autopilot";

    private static final String TAG = "Urban Airship Autopilot";

    /**
     * Starts the auto pilot takeOff process. This method must be called at every application entry
     * point (e.g., the onCreate() method of all broadcast receivers, launch activities and services.).
     *
     * @param application The application.
     */
    /*
     * automaticTakeOff might be called from different threads so we need to synchronize on the method
     * to prevent onCreateAirshipConfig from being called multiple times.
     */
    public static synchronized void automaticTakeOff(@NonNull Application application) {
        if (UAirship.isFlying() || UAirship.isTakingOff()) {
            return;
        }

        String classname = null;

        // Retrieve the class name from the manifest
        try {
            ApplicationInfo ai = application.getPackageManager().getApplicationInfo(application.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            classname = bundle.getString(AUTOPILOT_MANIFEST_KEY);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Failed to load meta-data, NameNotFound: " + e.getMessage());
        } catch (NullPointerException e) {
            Log.e(TAG, "Failed to load meta-data, NullPointer: " + e.getMessage());
        }

        if (classname == null) {
            Log.e(TAG, "Unable to takeOff automatically");
            return;
        }

        // Attempt to load the class
        Class<?> autopilotClass;
        try {
            autopilotClass = Class.forName(classname);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Unable to load the defined Autopilot instance. ClassNotFoundException: " + e.getMessage());
            return;
        }

        // Attempt to instantiate
        final Autopilot instance;
        try {
            instance = (Autopilot) autopilotClass.newInstance();
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Unable to instantiate the defined Autopilot instance. IllegalAccessException: " + e.getMessage());
            return;
        } catch (InstantiationException e) {
            Log.e(TAG, "Unable to instantiate the defined Autopilot instance. InstantiationException: " + e.getMessage());
            return;
        } catch (ClassCastException e) {
            Log.e(TAG, "Unable to instantiate the defined Autopilot instance. ClassCastException: " + e.getMessage());
            return;
        }

        if (instance == null) {
            Log.e(TAG, "Unable to instantiate the defined Autopilot instance. Instance is null.");
            return;
        }

        AirshipConfigOptions options = instance.createAirshipConfigOptions(application);

        if (UAirship.isFlying() || UAirship.isTakingOff()) {
            Log.e(TAG, "Airship is flying before autopilot is able to take off. Make sure" +
                    "AutoPilot.onCreateAirshipConfig is not calling takeOff directly.");
        }

        UAirship.takeOff(application, options, instance);
    }

    /**
     * Starts the auto pilot takeOff process. This method must be called at every application entry
     * point (e.g., the onCreate() method of all broadcast receivers, launch activities and services.).
     *
     * @param context The application context.
     */
    public static void automaticTakeOff(@NonNull Context context) {
        automaticTakeOff((Application) context.getApplicationContext());
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
}
