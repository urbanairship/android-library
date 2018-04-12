/* Copyright 2018 Urban Airship and Contributors */
package com.urbanairship.automation;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

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
public class AutomationUtils {

    static final String AMAZON_VERSION_KEY = "amazon";
    static final String ANDROID_VERSION_KEY = "android";
    static final String VERSION_KEY = "version";

    /**
     * Generates the version object.
     *
     * @return The version object.
     */
    public static JsonSerializable createVersionObject() {
        // Get the version code
        int currentAppVersion = UAirship.shared().getApplicationMetrics().getCurrentAppVersion();
        String platform = UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? AutomationUtils.AMAZON_VERSION_KEY : AutomationUtils.ANDROID_VERSION_KEY;

        return JsonMap.newBuilder()
                      .put(platform, JsonMap.newBuilder()
                                            .put(VERSION_KEY, currentAppVersion)
                                            .build())
                      .build()
                      .toJsonValue();
    }

    /**
     * Creates the version predicate.
     * @param versionMatcher The value matcher.
     * @return The version predicate.
     */
    public static JsonPredicate createVersionPredicate(@NonNull ValueMatcher versionMatcher) {
        String platform = UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM ? AutomationUtils.AMAZON_VERSION_KEY : AutomationUtils.ANDROID_VERSION_KEY;

        return JsonPredicate.newBuilder()
                            .addMatcher(JsonMatcher.newBuilder()
                                                   .setScope(platform)
                                                   .setKey(AutomationUtils.VERSION_KEY)
                                                   .setValueMatcher(versionMatcher)
                                                   .build())
                            .build();
    }
}
