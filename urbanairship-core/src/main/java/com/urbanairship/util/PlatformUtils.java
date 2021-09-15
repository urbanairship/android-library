/* Copyright Airship and Contributors */

package com.urbanairship.util;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.UAirship;
import com.urbanairship.http.RequestException;

import static com.urbanairship.UAirship.AMAZON_PLATFORM;
import static com.urbanairship.UAirship.ANDROID_PLATFORM;
import static com.urbanairship.UAirship.UNKNOWN_PLATFORM;

/**
 * Platform utilities.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PlatformUtils {

    public static final String AMAZON = "amazon";
    public static final String ANDROID = "android";
    public static final String UNKNOWN = "unknown";

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
            case UNKNOWN_PLATFORM:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parses the platform int.
     *
     * @param platform The platform int.
     * @return The corresponding platform, or {@link UAirship#UNKNOWN_PLATFORM} if the int
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
                return UAirship.UNKNOWN_PLATFORM;
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
                return AMAZON;
            case UAirship.ANDROID_PLATFORM:
                return ANDROID;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Converts a platform to a string
     *
     * @param platform The platform.
     * @return The string representing the platform.
     * @throws RequestException If the platform is unknown
     */
    @NonNull
    public static String getDeviceType(@UAirship.Platform int platform) throws RequestException {
        String deviceType = PlatformUtils.asString(platform);
        if (deviceType.equals(PlatformUtils.UNKNOWN)) {
            throw new RequestException("Invalid platform");
        }
        return deviceType;
    }

}
