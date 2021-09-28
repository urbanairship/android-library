/* Copyright Airship and Contributors */

package com.urbanairship.automation.tags;

import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AttributeListener;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupListener;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.contacts.Contact;
import com.urbanairship.contacts.ContactChangeListener;
import com.urbanairship.util.Clock;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

/**
 * Tracks uploaded tags and attributes.
 */
class AudienceHistorian {

    private final Clock clock;
    private final AirshipChannel channel;
    private final Contact contact;
    private final List<MutationRecord<TagGroupsMutation>> tagRecords = new ArrayList<>();
    private final List<MutationRecord<AttributeMutation>> attributeRecords = new ArrayList<>();

    /**
     * Default constructor.
     *
     * @param channel The channel.
     * @param contact The contact.
     * @param clock The clock;
     */
    AudienceHistorian(@NonNull AirshipChannel channel, @NonNull Contact contact, @NonNull Clock clock) {
        this.channel = channel;
        this.contact = contact;
        this.clock = clock;
    }

    /**
     * Initializes the historian.
     */
    void init() {
        channel.addTagGroupListener(new TagGroupListener() {
            @Override
            public void onTagGroupsMutationUploaded(@NonNull List<TagGroupsMutation> tagGroupsMutations) {
                recordTags(tagGroupsMutations, MutationRecord.SOURCE_CONTACT);
            }
        });

        channel.addAttributeListener(new AttributeListener() {
            @Override
            public void onAttributeMutationsUploaded(@NonNull List<AttributeMutation> attributes) {
                recordAttributes(attributes, MutationRecord.SOURCE_CONTACT);
            }
        });

        contact.addTagGroupListener(new TagGroupListener() {
            @Override
            public void onTagGroupsMutationUploaded(@NonNull List<TagGroupsMutation> tagGroupsMutations) {
                recordTags(tagGroupsMutations, MutationRecord.SOURCE_CONTACT);
            }
        });

        contact.addAttributeListener(new AttributeListener() {
            @Override
            public void onAttributeMutationsUploaded(@NonNull List<AttributeMutation> attributes) {
                recordAttributes(attributes, MutationRecord.SOURCE_CONTACT);
            }
        });

        contact.addContactChangeListener(new ContactChangeListener() {
            @Override
            public void onContactChanged() {
                clearContactHistory();
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

        for (MutationRecord<T> record : history) {
            if (record.time >= sinceDate) {
                mutations.add(record.mutation);
            }
        }

        return mutations;
    }

    private void recordTags(@NonNull List<TagGroupsMutation> mutations, @MutationRecord.Source int source) {
        synchronized (tagRecords) {
            long time = clock.currentTimeMillis();
            for (TagGroupsMutation mutation : mutations) {
                tagRecords.add(new MutationRecord<>(source, time, mutation));
            }
        }
    }

    private void recordAttributes(@NonNull List<AttributeMutation> mutations, @MutationRecord.Source int source) {
        synchronized (attributeRecords) {
            long time = clock.currentTimeMillis();
            for (AttributeMutation mutation : mutations) {
                attributeRecords.add(new MutationRecord<>(source, time, mutation));
            }
        }
    }

    private void clearContactHistory() {
        synchronized (tagRecords) {
            for (MutationRecord<TagGroupsMutation> record : new ArrayList<>(tagRecords)) {
                if (record.source == MutationRecord.SOURCE_CONTACT) {
                    tagRecords.remove(record);
                }
            }
        }

        synchronized (attributeRecords) {
            for (MutationRecord<AttributeMutation> record : new ArrayList<>(attributeRecords)) {
                if (record.source == MutationRecord.SOURCE_CONTACT) {
                    attributeRecords.remove(record);
                }
            }
        }
    }

    /**
     * Defines a mutation and a timestamp.
     */
    private static class MutationRecord<T> {

        @IntDef({ SOURCE_CHANNEL, SOURCE_CONTACT})
        @Retention(RetentionPolicy.SOURCE)
        @interface Source {
        }

        final static int SOURCE_CHANNEL = 0;
        final static int SOURCE_CONTACT = 1;

        @Source final int source;
        final long time;
        final T mutation;

        MutationRecord(@Source int source, long time, @NonNull T mutation) {
            this.source = source;
            this.time = time;
            this.mutation = mutation;
        }
    }

}
