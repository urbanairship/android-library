/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.contacts.Contact;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.util.Clock;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The named user is an alternate method of identifying the device. Once a named
 * user is associated to the device, it can be used to send push notifications
 * to the device.
 * @deprecated Use {@link com.urbanairship.contacts.Contact} instead.
 */
public class NamedUser extends AirshipComponent {
    private final Contact contact;

    /**
     * @hide
     */
    public NamedUser(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
              @NonNull Contact contact) {
        super(context, preferenceDataStore);
        this.contact = contact;
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.NAMED_USER;
    }

    /**
     * Returns the named user ID.
     *
     * @return The named user ID as a string or null if it does not exist.
     * @deprecated Use {@link Contact#getNamedUserId()} instead.
     */
    @Deprecated
    @Nullable
    public String getId() {
        return contact.getNamedUserId();
    }

    /**
     * Forces a named user update.
     * @deprecated No longer necessary
     */
    @Deprecated
    public void forceUpdate() {
        // no-op
    }

    /**
     * Sets the named user ID.
     * <p>
     * To associate the named user ID, its length must be greater than 0 and less than 129 characters.
     * To disassociate the named user ID, its value must be empty or null.
     *
     * @param namedUserId The named user ID string.
     * @deprecated Use {@link Contact#identify(String)} or {@link Contact#reset()} instead.
     */
    @Deprecated
    public void setId(@Nullable @Size(max = 128) String namedUserId) {
        if (namedUserId != null) {
            namedUserId = namedUserId.trim();
        }
        if (UAStringUtil.isEmpty(namedUserId)) {
            contact.reset();
        } else {
            contact.identify(namedUserId);
        }
    }

    /**
     * Edit the named user tags.
     *
     * @return The TagGroupsEditor.
     * @deprecated Use {@link Contact#editTagGroups()} instead.
     */
    @NonNull
    @Deprecated
    public TagGroupsEditor editTagGroups() {
        return contact.editTagGroups();
    }

    /**
     * Edit the attributes associated with the named user.
     *
     * @return An {@link AttributeEditor}.
     * @deprecated Use {@link Contact#editAttributes()} instead.
     */
    @NonNull
    public AttributeEditor editAttributes() {
        return contact.editAttributes();
    }
}
