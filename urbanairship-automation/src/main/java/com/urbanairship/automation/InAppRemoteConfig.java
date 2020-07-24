/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.automation.tags.TagGroupManager;
import com.urbanairship.iam.InAppMessageManager;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Remote config data for {@link InAppMessageManager}.
 */
class InAppRemoteConfig {

    private static final String TAG_GROUPS_CONFIG_KEY = "tag_groups";

    @NonNull
    public final TagGroupsConfig tagGroupsConfig;

    private InAppRemoteConfig(@NonNull TagGroupsConfig tagGroupsConfig) {
        this.tagGroupsConfig = tagGroupsConfig;
    }

    /**
     * Creates an instance with all values defaulted
     *
     * @return A default in-app remote config
     */
    @VisibleForTesting
    @NonNull
    static InAppRemoteConfig defaultConfig() {
        TagGroupsConfig config = TagGroupsConfig.defaultConfig();
        return new InAppRemoteConfig(config);
    }

    /**
     * Parses generic remote config into an in-app config.
     *
     * @param remoteConfig The generic remote config as a JsonMap.
     * @return An in-app remote config, or the default in-app remote config if the JSON is empty or null.
     */
    @NonNull
    public static InAppRemoteConfig fromJsonMap(@Nullable JsonMap remoteConfig) {
        if (remoteConfig == null) {
            return InAppRemoteConfig.defaultConfig();
        }

        TagGroupsConfig config = null;
        if (remoteConfig.containsKey(TAG_GROUPS_CONFIG_KEY)) {
            config = TagGroupsConfig.fromJsonValue(remoteConfig.opt(TAG_GROUPS_CONFIG_KEY));
        }

        return (config != null) ? new InAppRemoteConfig(config) : InAppRemoteConfig.defaultConfig();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InAppRemoteConfig that = (InAppRemoteConfig) o;

        return tagGroupsConfig.equals(that.tagGroupsConfig);
    }

    @Override
    public int hashCode() {
        return tagGroupsConfig.hashCode();
    }

    /**
     * The in-app tag groups config.
     */
    public static class TagGroupsConfig {

        @NonNull
        private static final String TAG_GROUP_FETCH_ENABLED_KEY = "enabled";
        @NonNull
        private static final String TAG_GROUP_CACHE_MAX_AGE_SECONDS = "cache_max_age_seconds";
        @NonNull
        private static final String TAG_GROUP_CACHE_STALE_READ_TIME_SECONDS = "cache_stale_read_age_seconds";
        @NonNull
        private static final String TAG_GROUP_CACHE_PREFER_LOCAL_UNTIL_SECONDS = "cache_prefer_local_until_seconds";

        private static final boolean DEFAULT_FETCH_ENABLED = true;
        private static final long DEFAULT_CACHE_MAX_AGE_TIME_SEC = TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_CACHE_MAX_AGE_TIME_MS);
        private static final long DEFAULT_CACHE_STALE_READ_TIME_SEC = TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_CACHE_STALE_READ_TIME_MS);
        private static final long DEFAULT_PREFER_LOCAL_DATA_TIME_SEC = TimeUnit.MILLISECONDS.toSeconds(TagGroupManager.DEFAULT_PREFER_LOCAL_DATA_TIME_MS);

        /**
         * Enable/Disable tag group fetches.
         */
        public final boolean isEnabled;

        /**
         * Max tag group cache in seconds.
         */
        public final long cacheMaxAgeInSeconds;

        /**
         * Max time to read the stale cache in seconds.
         */
        public final long cacheStaleReadTimeSeconds;

        /**
         * Prefer local data time in seconds.
         */
        public final long cachePreferLocalTagDataTimeSeconds;

        private TagGroupsConfig(boolean isEnabled,
                                long cacheMaxAgeInSeconds,
                                long cacheStaleReadTimeSeconds,
                                long cachePreferLocalTagDataTimeSeconds) {

            this.isEnabled = isEnabled;
            this.cacheMaxAgeInSeconds = cacheMaxAgeInSeconds;
            this.cacheStaleReadTimeSeconds = cacheStaleReadTimeSeconds;
            this.cachePreferLocalTagDataTimeSeconds = cachePreferLocalTagDataTimeSeconds;
        }

        @NonNull
        private TagGroupsConfig combine(TagGroupsConfig config) {
            return new TagGroupsConfig(this.isEnabled && config.isEnabled,
                    Math.max(this.cacheMaxAgeInSeconds, config.cacheMaxAgeInSeconds),
                    Math.max(this.cacheStaleReadTimeSeconds, config.cacheStaleReadTimeSeconds),
                    Math.max(this.cachePreferLocalTagDataTimeSeconds, config.cachePreferLocalTagDataTimeSeconds));
        }

        @NonNull
        private static TagGroupsConfig defaultConfig() {
            return new TagGroupsConfig(DEFAULT_FETCH_ENABLED,
                    DEFAULT_CACHE_MAX_AGE_TIME_SEC,
                    DEFAULT_CACHE_STALE_READ_TIME_SEC,
                    DEFAULT_PREFER_LOCAL_DATA_TIME_SEC);
        }

        @NonNull
        private static TagGroupsConfig fromJsonValue(@NonNull JsonValue jsonValue) {
            JsonMap map = jsonValue.optMap();

            return new TagGroupsConfig(map.opt(TAG_GROUP_FETCH_ENABLED_KEY).getBoolean(DEFAULT_FETCH_ENABLED),
                    map.opt(TAG_GROUP_CACHE_MAX_AGE_SECONDS).getLong(DEFAULT_CACHE_MAX_AGE_TIME_SEC),
                    map.opt(TAG_GROUP_CACHE_STALE_READ_TIME_SECONDS).getLong(DEFAULT_CACHE_STALE_READ_TIME_SEC),
                    map.opt(TAG_GROUP_CACHE_PREFER_LOCAL_UNTIL_SECONDS).getLong(DEFAULT_PREFER_LOCAL_DATA_TIME_SEC));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TagGroupsConfig that = (TagGroupsConfig) o;

            if (isEnabled != that.isEnabled) return false;
            if (cacheMaxAgeInSeconds != that.cacheMaxAgeInSeconds) return false;
            if (cacheStaleReadTimeSeconds != that.cacheStaleReadTimeSeconds) return false;
            return cachePreferLocalTagDataTimeSeconds == that.cachePreferLocalTagDataTimeSeconds;
        }

        @Override
        public int hashCode() {
            int result = (isEnabled ? 1 : 0);
            result = 31 * result + (int) (cacheMaxAgeInSeconds ^ (cacheMaxAgeInSeconds >>> 32));
            result = 31 * result + (int) (cacheStaleReadTimeSeconds ^ (cacheStaleReadTimeSeconds >>> 32));
            result = 31 * result + (int) (cachePreferLocalTagDataTimeSeconds ^ (cachePreferLocalTagDataTimeSeconds >>> 32));
            return result;
        }

    }

}
