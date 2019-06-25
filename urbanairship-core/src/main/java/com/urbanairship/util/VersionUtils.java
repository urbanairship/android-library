/* Copyright Airship and Contributors */

package com.urbanairship.util;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonMatcher;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.ValueMatcher;

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

    /**
     * Generates the version object.
     *
     * @return The version object.
     */
    @NonNull
    public static JsonSerializable createVersionObject() {
        return createVersionObject(UAirship.shared().getApplicationMetrics().getCurrentAppVersion());
    }

    /**
     * Generates the version object.
     *
     * @param appVersion The app version.
     * @return The version object.
     */
    @NonNull
    public static JsonSerializable createVersionObject(int appVersion) {
        // Get the version code
        String platform = UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? VersionUtils.AMAZON_VERSION_KEY : VersionUtils.ANDROID_VERSION_KEY;

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
        String platform = UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? VersionUtils.AMAZON_VERSION_KEY : VersionUtils.ANDROID_VERSION_KEY;

        return JsonPredicate.newBuilder()
                            .addMatcher(JsonMatcher.newBuilder()
                                                   .setScope(platform)
                                                   .setKey(VersionUtils.VERSION_KEY)
                                                   .setValueMatcher(versionMatcher)
                                                   .build())
                            .build();
    }

}
