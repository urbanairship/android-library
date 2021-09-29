/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.util.Clock;

import java.util.List;

/**
 * Airship contact. A contact is distinct from a channel and represents a "user"
 * within Airship. Contacts may be named and have channels associated with it.
 */
public class Contact extends AirshipComponent {

    private final PreferenceDataStore preferenceDataStore;
    private final AirshipChannel airshipChannel;
    private final PrivacyManager privacyManager;

    /**
     * Creates a Contact.
     *
     * @param context The application context.
     * @param preferenceDataStore The preferences data store.
     */
    public Contact(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                   @NonNull AirshipRuntimeConfig runtimeConfig, @NonNull PrivacyManager privacyManager,
                   @NonNull AirshipChannel airshipChannel){
        this(context, preferenceDataStore, privacyManager, airshipChannel);
    }

    /**
     * @hide
     */
    @VisibleForTesting
    Contact(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
            @NonNull PrivacyManager privacyManager, @NonNull AirshipChannel airshipChannel) {
        super(context, preferenceDataStore);
        this.preferenceDataStore = preferenceDataStore;
        this.privacyManager = privacyManager;
        this.airshipChannel = airshipChannel;
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.CONTACT;
    }


    /**
     * Associates the contact with the given named user identifier.
     *
     * @param externalId The channel's identifier.
     */
    public void identify(@NonNull @Size(min=1) String externalId) {

    }

    /**
     * Disassociate the channel from its current contact, and create a new
     * un-named contact.
     */
    public void reset() {

    }

    /**
     * Edit the tags associated with this Contact.
     *
     * @return a {@link TagGroupsEditor}.
     */
    public TagGroupsEditor editTags() {
        return new TagGroupsEditor();
    }

    /**
     * Edit the attributes associated with this Contact.
     *
     * @return An {@link AttributeEditor}.
     */
    public AttributeEditor editAttributes() {
        return new AttributeEditor(Clock.DEFAULT_CLOCK) {
            @Override
            protected void onApply(@NonNull List<AttributeMutation> collapsedMutations) {

            }
        };
    }
}
