/* Copyright Airship and Contributors */

package com.urbanairship.util;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.UAirship;

import static com.urbanairship.UAirship.AMAZON_PLATFORM;
import static com.urbanairship.UAirship.ANDROID_PLATFORM;

/**
 * Platform utilities.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PlatformUtils {

    /**
     * Checks if the platform is valid.
     *
     * @param platform A platform int.
     * @return {@code true} If the integer matches the platform, otherwise {@code false}.
     */
    public static boolean isPlatformValid(int platform) {
        switch (platform) {
            case AMAZON_PLATFORM:
            case ANDROID_PLATFORM:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parses the platform int.
     *
     * @param platform The platform int.
     * @return The corresponding platform, or {@link UAirship#ANDROID_PLATFORM} if the int
     * does not match any platforms.
     */
    @UAirship.Platform
    public static int parsePlatform(int platform) {
        switch (platform) {
            case UAirship.AMAZON_PLATFORM:
                return AMAZON_PLATFORM;
            case UAirship.ANDROID_PLATFORM:
                return ANDROID_PLATFORM;
            default:
                return ANDROID_PLATFORM;
        }
    }

    /**
     * Converts a platform to a string.
     *
     * @param platform The platform.
     * @return The string representing the platform.
     */
    @NonNull
    public static String asString(@UAirship.Platform int platform) {
        switch (platform) {
            case UAirship.AMAZON_PLATFORM:
                return "amazon";
            case UAirship.ANDROID_PLATFORM:
                return "android";
            default:
                return "unknown";
        }
    }

}
