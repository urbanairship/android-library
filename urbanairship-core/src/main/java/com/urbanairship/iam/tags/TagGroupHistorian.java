/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import androidx.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.channel.TagGroupRegistrar;
import com.urbanairship.util.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tracks pending and sent mutations.
 */
class TagGroupHistorian {

    static final String RECORDS_KEY = "com.urbanairship.TAG_GROUP_HISTORIAN_RECORDS";

    private final Object recordLock = new Object();

    private final TagGroupRegistrar tagGroupRegistrar;
    private final PreferenceDataStore dataStore;
    private final Clock clock;
    private long maxRecordAge = Long.MAX_VALUE;

    /**
     * Default constructor.
     *
     * @param tagGroupRegistrar The tag group registrar.
     * @param dataStore The data store.
     */
    TagGroupHistorian(TagGroupRegistrar tagGroupRegistrar, PreferenceDataStore dataStore, Clock clock) {
        this.tagGroupRegistrar = tagGroupRegistrar;
        this.dataStore = dataStore;
        this.clock = clock;
    }

    /**
     * Initializes the historian.
     */
    void init() {
        tagGroupRegistrar.addListener(new TagGroupRegistrar.Listener() {
            @Override
            public void onMutationUploaded(@NonNull TagGroupsMutation mutation) {
                recordMutation(mutation);
            }
        });
    }

    /**
     * Sets the stored max record age.
     *
     * @param duration Duration.
     * @param unit The time unit.
     */
    void setMaxRecordAge(long duration, @NonNull TimeUnit unit) {
        this.maxRecordAge = unit.toMillis(duration);
    }

    /**
     * Applies any relevant tag data.
     *
     * @param tags The tags.
     * @param sinceDate Time filter to only apply data that happened since the specified time in milliseconds
     * since the epoch.
     */
    void applyLocalData(@NonNull Map<String, Set<String>> tags, long sinceDate) {
        // Records
        for (MutationRecord record : getMutationRecords()) {
            if (record.time >= sinceDate) {
                record.mutation.apply(tags);
            }
        }

        // Named User
        for (TagGroupsMutation mutation : tagGroupRegistrar.getPendingMutations(TagGroupRegistrar.NAMED_USER)) {
            mutation.apply(tags);
        }

        // Channel
        for (TagGroupsMutation mutation : tagGroupRegistrar.getPendingMutations(TagGroupRegistrar.CHANNEL)) {
            mutation.apply(tags);
        }
    }

    /**
     * Records an uploaded mutation. To be used later as local tag data.
     *
     * @param mutation The mutation.
     */
    private void recordMutation(@NonNull TagGroupsMutation mutation) {
        synchronized (recordLock) {
            List<MutationRecord> records = getMutationRecords();

            // Add new record
            records.add(new MutationRecord(clock.currentTimeMillis(), mutation));

            // Sort entries by oldest first
            Collections.sort(records, new Comparator<MutationRecord>() {
                @Override
                public int compare(@NonNull MutationRecord lh, @NonNull MutationRecord rh) {
                    if (lh.time == rh.time) {
                        return 0;
                    }
                    if (lh.time > rh.time) {
                        return 1;
                    }
                    return -1;
                }
            });

            dataStore.put(RECORDS_KEY, JsonValue.wrapOpt(records));
        }
    }

    /**
     * Gets the recorded mutations.
     *
     * @return The list of recorded mutations.
     */
    @NonNull
    private List<MutationRecord> getMutationRecords() {
        synchronized (recordLock) {
            // Grab entries, should already be sorted
            List<MutationRecord> allRecords = MutationRecord.fromJsonList(dataStore.getJsonValue(RECORDS_KEY).optList());

            // Remove any dated records
            List<MutationRecord> filteredRecords = new ArrayList<>();
            for (MutationRecord record : allRecords) {
                long age = clock.currentTimeMillis() - record.time;
                if (age <= maxRecordAge) {
                    filteredRecords.add(record);
                }
            }

            return filteredRecords;
        }
    }

    /**
     * Defines a mutation and a timestamp.
     */
    private static class MutationRecord implements JsonSerializable {

        private final static String TIME_KEY = "time";
        private final static String MUTATION = "mutation";

        final long time;
        final TagGroupsMutation mutation;

        /**
         * Default constructor.
         *
         * @param time The record time.
         * @param mutation The mutation.
         */
        MutationRecord(long time, @NonNull TagGroupsMutation mutation) {
            this.time = time;
            this.mutation = mutation;
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonMap.newBuilder()
                          .put(TIME_KEY, time)
                          .put(MUTATION, mutation)
                          .build()
                          .toJsonValue();
        }

        /**
         * Parses a mutation record from a JSON value.
         *
         * @param jsonValue The JSON value.
         * @return The mutation record.
         */
        @NonNull
        static MutationRecord fromJsonValue(@NonNull JsonValue jsonValue) throws JsonException {
            JsonMap jsonMap = jsonValue.optMap();

            long time = jsonMap.opt(TIME_KEY).getLong(0);
            if (time < 0) {
                throw new JsonException("Invalid record: " + jsonValue);
            }

            TagGroupsMutation mutation = TagGroupsMutation.fromJsonValue(jsonMap.opt(MUTATION));
            return new MutationRecord(time, mutation);
        }

        /**
         * Parses a list of mutation records from a JSON list.
         *
         * @param jsonList The JSON list.
         * @return A list of mutation records.
         */
        @NonNull
        static List<MutationRecord> fromJsonList(@NonNull JsonList jsonList) {
            List<MutationRecord> records = new ArrayList<>();

            for (JsonValue value : jsonList) {
                try {
                    MutationRecord record = fromJsonValue(value);
                    records.add(record);
                } catch (JsonException e) {
                    Logger.error(e, "Failed to parse tag group record.");
                }
            }

            return records;
        }

    }

}
