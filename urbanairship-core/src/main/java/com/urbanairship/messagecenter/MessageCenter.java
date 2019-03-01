/* Copyright Urban Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.urbanairship.AirshipComponent;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.richpush.RichPushInbox;

/**
 * Primary interface for configuring the default
 * Message Center implementation.
 */
public class MessageCenter extends AirshipComponent {

    private RichPushInbox.Predicate predicate;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public MessageCenter(@NonNull Context context, @NonNull PreferenceDataStore dataStore) {
        super(context, dataStore);
    }

    /**
     * Returns the default inbox predicate.
     *
     * @return The default inbox predicate.
     */
    @Nullable
    public RichPushInbox.Predicate getPredicate() {
        return predicate;
    }

    /**
     * Sets the default inbox predicate.
     *
     * @param predicate The default inbox predicate.
     */
    public void setPredicate(@Nullable RichPushInbox.Predicate predicate) {
        this.predicate = predicate;
    }

}
