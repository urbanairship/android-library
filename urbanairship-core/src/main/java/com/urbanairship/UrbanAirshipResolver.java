/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * A ContentResolver wrapper used to access data from the
 * {@link com.urbanairship.UrbanAirshipProvider}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class UrbanAirshipResolver {

    private final Context context;

    public UrbanAirshipResolver(@NonNull Context context) {
        this.context = context;
    }

    @Nullable
    protected Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String whereClause, @Nullable String[] whereArgs, @Nullable String sortOrder) {
        try {
            return this.getResolver().query(uri, projection, whereClause, whereArgs, sortOrder);
        } catch (Exception e) {
            Logger.error(e, "Failed to query the UrbanAirshipProvider.");
            return null;
        }
    }

    protected int delete(@NonNull Uri uri, @Nullable String whereClause, @Nullable String[] whereArgs) {
        try {
            return this.getResolver().delete(uri, whereClause, whereArgs);
        } catch (Exception e) {
            Logger.error(e, "Failed to perform a delete in UrbanAirshipProvider.");
            return -1;
        }
    }

    protected int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String whereClause, @Nullable String[] whereArgs) {
        try {
            return this.getResolver().update(uri, values, whereClause, whereArgs);
        } catch (Exception e) {
            Logger.error(e, "Failed to perform an update in UrbanAirshipProvider.");
            return 0;
        }
    }

    @Nullable
    protected Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        try {
            return this.getResolver().insert(uri, values);
        } catch (Exception e) {
            Logger.error(e, "Failed to insert in UrbanAirshipProvider.");
            return null;
        }
    }

    protected int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        try {
            return this.getResolver().bulkInsert(uri, values);
        } catch (Exception e) {
            Logger.error(e, "Failed to bulk insert in UrbanAirshipProvider.");
            return 0;
        }
    }

    /**
     * Register a ContentObserver to listen for updates to the supplied URI.
     *
     * @param uri The URI you want to listen to updates for.
     * @param notifyForDescendants If <code>true</code> changes to URIs beginning with <code>uri</code>
     * will also cause notifications to be sent. If <code>false</code> only changes to the exact URI
     * specified by <em>uri</em> will cause notifications to be sent. If true, than any URI values
     * at or below the specified URI will also trigger a match.
     * @param observer The ContentObserver you want to alert when the supplied URI is updated.
     */
    public void registerContentObserver(@NonNull Uri uri, boolean notifyForDescendants, @NonNull ContentObserver observer) {
        try {
            this.getResolver().registerContentObserver(uri, notifyForDescendants, observer);
        } catch (IllegalArgumentException e) {
            Logger.warn("Unable to register content observer for uri: %s", uri);
        }
    }

    /**
     * Unregister the supplied ContentObserver.
     *
     * @param observer The ContentObserver you wish to unregister
     */
    public void unregisterContentObserver(@NonNull ContentObserver observer) {
        this.getResolver().unregisterContentObserver(observer);
    }

    public void notifyChange(@NonNull Uri uri, @NonNull ContentObserver observer) {
        try {
            this.getResolver().notifyChange(uri, observer);
        } catch (IllegalArgumentException ex) {
            Logger.warn("Unable to notify observers of change for uri: %s", uri);
        }
    }

    // helpers

    @NonNull
    private ContentResolver getResolver() {
        return this.context.getContentResolver();
    }

}
