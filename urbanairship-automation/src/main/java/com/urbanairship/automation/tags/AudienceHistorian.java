/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AttributeListener;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.NamedUser;
import com.urbanairship.channel.TagGroupListener;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.util.Clock;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Tracks uploaded tags and attributes.
 */
class AudienceHistorian {

    private final Clock clock;
    private final AirshipChannel channel;
    private final NamedUser namedUser;
    private final List<MutationRecord<TagGroupsMutation>> tagRecords = new ArrayList<>();
    private final List<MutationRecord<AttributeMutation>> attributeRecords = new ArrayList<>();

    /**
     * Default constructor.
     *
     * @param channel The channel.
     * @param namedUser The named user.
     * @param clock The clock;
     */
    AudienceHistorian(@NonNull AirshipChannel channel, @NonNull NamedUser namedUser, @NonNull Clock clock) {
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
                recordTagGroup(new MutationRecord<>(MutationRecord.SOURCE_CHANNEL, identifier, clock.currentTimeMillis(), tagGroupsMutation));
            }
        });

        channel.addAttributeListener(new AttributeListener() {
            @Override
            public void onAttributeMutationsUploaded(@NonNull String identifier, @NonNull List<AttributeMutation> attributes) {
                long time = clock.currentTimeMillis();
                for (AttributeMutation mutation : attributes) {
                    recordAttribute(new MutationRecord<>(MutationRecord.SOURCE_CHANNEL, identifier, time, mutation));
                }
            }
        });

        namedUser.addTagGroupListener(new TagGroupListener() {
            @Override
            public void onTagGroupsMutationUploaded(@NonNull String identifier, @NonNull TagGroupsMutation tagGroupsMutation) {
                recordTagGroup(new MutationRecord<>(MutationRecord.SOURCE_NAMED_USER, identifier, clock.currentTimeMillis(), tagGroupsMutation));
            }
        });
        namedUser.addAttributeListener(new AttributeListener() {
            @Override
            public void onAttributeMutationsUploaded(@NonNull String identifier, @NonNull List<AttributeMutation> attributes) {
                long time = clock.currentTimeMillis();
                for (AttributeMutation mutation : attributes) {
                    recordAttribute(new MutationRecord<>(MutationRecord.SOURCE_NAMED_USER, identifier, time, mutation));
                }
            }
        });
    }

    /**
     * Gets tag group mutations that have been applied since the app launched.
     *
     * @param sinceDate Time filter for tag groups in milliseconds.
     * @return The list of recorded tag group mutations.
     */
    @NonNull
    public List<TagGroupsMutation> getTagGroupHistory(long sinceDate) {
        synchronized (tagRecords) {
            return filterHistory(tagRecords, sinceDate);
        }
    }

    /**
     * Gets attribute mutations that have been applied since the app launched.
     *
     * @param sinceDate Time filter for attributes in milliseconds.
     * @return The list of recorded attribute mutations.
     */
    @NonNull
    public List<AttributeMutation> getAttributeHistory(long sinceDate) {
        synchronized (attributeRecords) {
            return filterHistory(attributeRecords, sinceDate);
        }
    }

    private <T> List<T> filterHistory(List<MutationRecord<T>> history, long sinceDate) {
        List<T> mutations = new ArrayList<>();
        String namedUserId = namedUser.getId();

        for (MutationRecord<T> record : history) {
            if (record.time >= sinceDate && (record.source == MutationRecord.SOURCE_CHANNEL || record.identifier.equals(namedUserId))) {
                mutations.add(record.mutation);
            }
        }

        return mutations;
    }

    private void recordTagGroup(@NonNull MutationRecord<TagGroupsMutation> record) {
        synchronized (tagRecords) {
            tagRecords.add(record);
        }
    }

    private void recordAttribute(@NonNull MutationRecord<AttributeMutation> record) {
        synchronized (attributeRecords) {
            attributeRecords.add(record);
        }
    }

    /**
     * Defines a mutation, timestamp, and an optional named user Id.
     */
    private static class MutationRecord<T> {

        @IntDef({ SOURCE_CHANNEL, SOURCE_NAMED_USER})
        @Retention(RetentionPolicy.SOURCE)
        @interface Source {
        }

        final static int SOURCE_CHANNEL = 0;
        final static int SOURCE_NAMED_USER = 1;

        @Source final int source;
        final long time;
        final T mutation;
        final String identifier;

        MutationRecord(@Source int source, @Nullable String identifier, long time, @NonNull T mutation) {
            this.source = source;
            this.time = time;
            this.identifier = identifier;
            this.mutation = mutation;
        }
    }

}
