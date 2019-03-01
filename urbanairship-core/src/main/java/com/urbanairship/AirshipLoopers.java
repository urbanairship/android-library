package com.urbanairship;

import android.os.Looper;

import com.urbanairship.util.AirshipHandlerThread;

/**
 * Shared SDK looopers.
 */
public class AirshipLoopers {

    private static Looper backgroundLooper;

    /**
     * Gets the background looper.
     *
     * @return The background looper.
     */
    public static Looper getBackgroundLooper() {
        if (backgroundLooper == null) {
            synchronized (AirshipLoopers.class) {
                if (backgroundLooper == null) {
                    AirshipHandlerThread thread = new AirshipHandlerThread("background");
                    thread.start();
                    backgroundLooper = thread.getLooper();
                }
            }
        }
        return backgroundLooper;
    }

}
