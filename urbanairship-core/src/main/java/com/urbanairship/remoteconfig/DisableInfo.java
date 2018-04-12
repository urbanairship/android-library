/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remoteconfig;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.IvyVersionMatcher;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Disable remote config info.
 *
 * @hide
 */
class DisableInfo {

    /**
     * Push module.
     */
    public final static String PUSH_MODULE = "push";

    /**
     * Analytics module.
     */
    public final static String ANALYTICS_MODULE = "analytics";

    /**
     * Message center module.
     */
    public final static String MESSAGE_CENTER = "message_center";

    /**
     * In-app module.
     */
    public final static String IN_APP_MODULE = "in_app_v2";

    /**
     * Automation module.
     */
    public final static String AUTOMATION_MODULE = "automation";

    /**
     * Named user module.
     */
    public final static String NAMED_USER_MODULE = "named_user";

    /**
     * Location module
     */
    public final static String LOCATION_MODULE = "location";

    final static List<String> ALL_MODULES = Arrays.asList(PUSH_MODULE, ANALYTICS_MODULE,
            MESSAGE_CENTER, IN_APP_MODULE, AUTOMATION_MODULE, NAMED_USER_MODULE, LOCATION_MODULE);

    // JSON keys
    private static final String MODULES_KEY = "modules";
    private static final String SDK_VERSIONS_KEY = "sdk_versions";
    private static final String REMOTE_DATA_REFRESH_INTERVAL_KEY = "remote_data_refresh_interval";
    private static final String MODULES_ALL_KEY = "all";


    private final Set<String> disabledModules;
    private final long remoteDataInterval;
    private final Set<String> sdkVersionConstraints;

    public DisableInfo(@NonNull Collection<String> modules, long remoteDataInterval, @NonNull Collection<String> sdkVersionConstraints) {
        this.disabledModules = new HashSet<>(modules);
        this.remoteDataInterval = remoteDataInterval;
        this.sdkVersionConstraints = new HashSet<>(sdkVersionConstraints);
    }

    /**
     * Collapses all the matching disable info into a single disable info.
     *
     * @param disableInfos The collection of disable infos.
     * @param sdkVersion The sdk version to filter disable infos.
     * @return A disable info.
     */
    public static DisableInfo collapse(@NonNull Collection<DisableInfo> disableInfos, @NonNull String sdkVersion) {
        Set<String> modules = new HashSet<>();
        Set<String> constraints = new HashSet<>();
        long remoteDataInterval = 0;


        for (DisableInfo info : disableInfos) {
            boolean isMatch = false;
            if (info.sdkVersionConstraints.isEmpty()) {
                isMatch = true;
            } else {
                for (String constraint : info.sdkVersionConstraints) {
                    try {
                        if (IvyVersionMatcher.newMatcher(constraint).apply(sdkVersion)) {
                            isMatch = true;
                            constraints.add(constraint);
                        }
                    } catch (IllegalArgumentException e) {
                        Logger.error("Unable to check version", e);
                    }
                }
            }

            if (isMatch) {
                modules.addAll(info.disabledModules);
                remoteDataInterval = Math.max(remoteDataInterval, info.remoteDataInterval);
            }
        }

        return new DisableInfo(modules, remoteDataInterval, constraints);
    }

    /**
     * Pareses a disable info from the json value.
     *
     * @param jsonValue The json value.
     * @return The disable info.
     * @throws JsonException If the json value is invalid.
     */
    public static DisableInfo parseJson(@NonNull JsonValue jsonValue) throws JsonException {
        JsonMap jsonMap = jsonValue.optMap();

        Collection<String> modules = new HashSet<>();
        Collection<String> sdkConstraints = new HashSet<>();
        long remoteDataInterval = 0;

        if (jsonMap.containsKey(MODULES_KEY)) {
            if (MODULES_ALL_KEY.equals(jsonMap.opt(MODULES_KEY).getString())) {
                modules.addAll(ALL_MODULES);
            } else {
                JsonList modulesList = jsonMap.opt(MODULES_KEY).getList();
                if (modulesList == null) {
                    throw new JsonException("Modules must be an array of strings: " + jsonMap.opt(MODULES_KEY));
                }

                for (JsonValue moduleValue : modulesList) {
                    if (!moduleValue.isString()) {
                        throw new JsonException("Modules must be an array of strings: " + jsonMap.opt(MODULES_KEY));
                    }

                    String module = moduleValue.getString();

                    // Avoid throwing an exception in this case so the SDK does not break
                    // when we introduce new modules in the future
                    if (ALL_MODULES.contains(module)) {
                        modules.add(moduleValue.getString());
                    }
                }
            }
        }

        if (jsonMap.containsKey(SDK_VERSIONS_KEY)) {
            JsonList constraintList = jsonMap.opt(SDK_VERSIONS_KEY).getList();
            if (constraintList == null) {
                throw new JsonException("SDK Versions must be an array of strings: " + jsonMap.opt(SDK_VERSIONS_KEY));
            }

            for (JsonValue constraintValue : constraintList) {
                if (!constraintValue.isString()) {
                    throw new JsonException("SDK Versions must be an array of strings: " + jsonMap.opt(SDK_VERSIONS_KEY));
                }

                sdkConstraints.add(constraintValue.getString());
            }
        }

        if (jsonMap.containsKey(REMOTE_DATA_REFRESH_INTERVAL_KEY)) {
            if (!jsonMap.get(REMOTE_DATA_REFRESH_INTERVAL_KEY).isNumber()) {
                throw new IllegalArgumentException("Remote data refresh interval must be a number: " + jsonMap.get(REMOTE_DATA_REFRESH_INTERVAL_KEY));
            }

            remoteDataInterval = TimeUnit.SECONDS.toMillis(jsonMap.get(REMOTE_DATA_REFRESH_INTERVAL_KEY).getInt(0));
        }

        return new DisableInfo(modules, remoteDataInterval, sdkConstraints);
    }

    /**
     * Returns the set of disabled modules.
     *
     * @return Set of disabled modules.
     */
    public Set<String> getSdkVersionConstraints() {
        return sdkVersionConstraints;
    }

    /**
     * Returns the set of disabled modules.
     *
     * @return Set of disabled modules.
     */
    public Set<String> getDisabledModules() {
        return disabledModules;
    }

    /**
     * Returns the set of enabled modules.
     *
     * @return Set of enabled modules.
     */
    public Set<String> getEnabledModules() {
        Set<String> modules = new HashSet<>(ALL_MODULES);
        modules.removeAll(disabledModules);
        return modules;
    }

    /**
     * Returns the remote data refresh interval.
     *
     * @return The remote data refresh interval in milliseconds.
     */
    public long getRemoteDataRefreshInterval() {
        return remoteDataInterval;
    }


}
