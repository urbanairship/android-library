/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.channel.TagGroupListener;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.util.Clock;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Tracks pending and sent mutations.
 */
class TagGroupHistorian {

    private final Object recordLock = new Object();

    private final Clock clock;
    private final AirshipChannel channel;
    private final NamedUser namedUser;
    private final List<MutationRecord> records = new ArrayList<>();

    /**
     * Default constructor.
     *
     * @param channel The channel.
     * @param namedUser The named user.
     * @param clock The clock;
     */
    TagGroupHistorian(@NonNull AirshipChannel channel, @NonNull NamedUser namedUser, @NonNull Clock clock) {
        this.channel = channel;
        this.namedUser = namedUser;
        this.clock = clock;
    }

    /**
     * Initializes the historian.
     */
    void init() {
        channel.addTagGroupListener(new TagGroupListener() {
            @Override
            public void onTagGroupsMutationUploaded(@NonNull String identifier, @NonNull TagGroupsMutation tagGroupsMutation) {
                record(MutationRecord.newChannelRecord(clock.currentTimeMillis(), tagGroupsMutation));
            }
        });

        namedUser.addTagGroupListener(new TagGroupListener() {
            @Override
            public void onTagGroupsMutationUploaded(@NonNull String identifier, @NonNull TagGroupsMutation tagGroupsMutation) {
                record(MutationRecord.newNamedUserRecord(clock.currentTimeMillis(), identifier, tagGroupsMutation));
            }
        });
    }

    /**
     * Gets tag group mutations that have been applied since the app launched.
     *
     * @param sinceDate Time filter to for tag groups in milliseconds.
     * @return The list of recorded tag group mutations.
     */
    public List<TagGroupsMutation> getTagGroupHistory(long sinceDate) {
        List<TagGroupsMutation> mutations = new ArrayList<>();
        String namedUserId = namedUser.getId();

        synchronized (recordLock) {
            // Records
            for (MutationRecord record : records) {
                if (record.time >= sinceDate && (record.namedUserId == null || record.namedUserId.equals(namedUserId))) {
                    mutations.add(record.mutation);
                }
            }
        }

        return mutations;
    }

    private void record(@NonNull MutationRecord record) {
        synchronized (recordLock) {
            records.add(record);
        }
    }

    /**
     * Defines a mutation, timestamp, and an optional named user Id.
     */
    private static class MutationRecord {

        final long time;
        final TagGroupsMutation mutation;
        final String namedUserId;

        private MutationRecord(long time, @Nullable String namedUserId, @NonNull TagGroupsMutation mutation) {
            this.time = time;
            this.namedUserId = namedUserId;
            this.mutation = mutation;
        }

        static MutationRecord newChannelRecord(long time, @NonNull TagGroupsMutation mutation) {
            return new MutationRecord(time, null, mutation);
        }

        static MutationRecord newNamedUserRecord(long time, @NonNull String namedUserId, @NonNull TagGroupsMutation mutation) {
            return new MutationRecord(time, namedUserId, mutation);
        }

    }

}
