/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.iam.tags.TagGroupManager;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Remote config data for {@link InAppMessageManager}.
 */
class InAppRemoteConfig {

    private static final String TAG_GROUPS_CONFIG_KEY = "tag_groups";

    @Nullable
    public final TagGroupsConfig tagGroupsConfig;

    private InAppRemoteConfig(@Nullable TagGroupsConfig tagGroupsConfig) {
        this.tagGroupsConfig = tagGroupsConfig;
    }

    @NonNull
    private InAppRemoteConfig combine(@NonNull InAppRemoteConfig config) {
        TagGroupsConfig tagGroupsConfig;
        if (this.tagGroupsConfig != null && config.tagGroupsConfig != null) {
            tagGroupsConfig = this.tagGroupsConfig.combine(config.tagGroupsConfig);
        } else if (this.tagGroupsConfig == null) {
            tagGroupsConfig = config.tagGroupsConfig;
        } else {
            tagGroupsConfig = this.tagGroupsConfig;
        }

        return new InAppRemoteConfig(tagGroupsConfig);
    }

    /**
     * Parses and collapses config from list of in-app configs.
     *
     * @param jsonList The json list.
     * @return A in-app remote config, or null if the JSON is empty.
     */
    @Nullable
    public static InAppRemoteConfig fromJsonList(@NonNull JsonList jsonList) {
        List<InAppRemoteConfig> configList = new ArrayList<>();
        for (JsonValue jsonValue : jsonList) {
            configList.add(InAppRemoteConfig.fromJsonValue(jsonValue));
        }

        if (configList.size() == 0) {
            return null;
        }

        if (configList.size() == 1) {
            return configList.get(0);
        }

        InAppRemoteConfig config = configList.remove(0);

        while (!configList.isEmpty()) {
            InAppRemoteConfig next = configList.remove(0);
            config = config.combine(next);
        }

        return config;
    }

    private static InAppRemoteConfig fromJsonValue(JsonValue jsonValue) {
        TagGroupsConfig config = null;
        if (jsonValue.optMap().containsKey(TAG_GROUPS_CONFIG_KEY)) {
            config = TagGroupsConfig.fromJsonValue(jsonValue.optMap().opt(TAG_GROUPS_CONFIG_KEY));
        }

        return new InAppRemoteConfig(config);
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
        private static TagGroupsConfig fromJsonValue(@NonNull JsonValue jsonValue) {
            JsonMap map = jsonValue.optMap();

            boolean isTagGroupFetchEnabled = map.opt(TAG_GROUP_FETCH_ENABLED_KEY).getBoolean(true);

            long tagGroupCacheMaxAgeInSeconds = map.opt(TAG_GROUP_CACHE_MAX_AGE_SECONDS).getLong(TagGroupManager.DEFAULT_CACHE_MAX_AGE_TIME_MS);
            long tagGroupCacheStaleReadTimeSeconds = map.opt(TAG_GROUP_CACHE_STALE_READ_TIME_SECONDS).getLong(TagGroupManager.DEFAULT_CACHE_STALE_READ_TIME_MS);
            long tagGroupCachePreferLocalTagDataTimeSeconds = map.opt(TAG_GROUP_CACHE_PREFER_LOCAL_UNTIL_SECONDS).getLong(TagGroupManager.DEFAULT_PREFER_LOCAL_DATA_TIME_MS);

            return new TagGroupsConfig(isTagGroupFetchEnabled, tagGroupCacheMaxAgeInSeconds, tagGroupCacheStaleReadTimeSeconds, tagGroupCachePreferLocalTagDataTimeSeconds);
        }

    }

}
