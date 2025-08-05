/* Copyright Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonMatcher;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.ValueMatcher;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Utils for automation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class VersionUtils {

    static final String AMAZON_VERSION_KEY = "amazon";
    static final String ANDROID_VERSION_KEY = "android";
    static final String VERSION_KEY = "version";

    private static final String IVY_PATTERN_GREATER_THAN = "]%s,)";
    private static final String IVY_PATTERN_GREATER_THAN_OR_EQUAL_TO = "[%s,)";


    /**
     * Generates the version object.
     *
     * @param appVersion The app version.
     * @return The version object.
     */
    @NonNull
    public static JsonSerializable createVersionObject(long appVersion) {
        // Get the version code
        String platform = UAirship.shared().getPlatformType() == UAirship.Platform.AMAZON ? VersionUtils.AMAZON_VERSION_KEY : VersionUtils.ANDROID_VERSION_KEY;

        return JsonMap.newBuilder()
                      .put(platform, JsonMap.newBuilder()
                                            .put(VERSION_KEY, appVersion)
                                            .build())
                      .build()
                      .toJsonValue();
    }

    /**
     * Creates the version predicate.
     *
     * @param versionMatcher The value matcher.
     * @return The version predicate.
     */
    @NonNull
    public static JsonPredicate createVersionPredicate(@NonNull ValueMatcher versionMatcher) {
        String platform = UAirship.shared().getPlatformType() == UAirship.Platform.AMAZON ? VersionUtils.AMAZON_VERSION_KEY : VersionUtils.ANDROID_VERSION_KEY;

        return JsonPredicate.newBuilder()
                            .addMatcher(JsonMatcher.newBuilder()
                                                   .setScope(platform)
                                                   .setKey(VersionUtils.VERSION_KEY)
                                                   .setValueMatcher(versionMatcher)
                                                   .build())
                            .build();
    }

    /**
     * Returns {@code true} if the v1 version is newer than the v2 version.
     * If either version is invalid, returns {@code false}.
     */
    public static boolean isVersionNewer(String v1, String v2) {
        try {
            return IvyVersionMatcher.newMatcher(String.format(IVY_PATTERN_GREATER_THAN, v1)).apply(v2);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns {@code true} if the v1 version is newer than or equal to the v2 version.
     * If either version is invalid, returns {@code false}.
     */
    public static boolean isVersionNewerOrEqualTo(String v1, String v2) {
        try {
            return IvyVersionMatcher.newMatcher(String.format(IVY_PATTERN_GREATER_THAN_OR_EQUAL_TO, v1)).apply(v2);
        } catch (Exception e) {
            return false;
        }
    }
}
