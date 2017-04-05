/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;

/**
 * An ContentResolver wrapper used to access data from the
 * {@link com.urbanairship.UrbanAirshipProvider}.
 */
public class UrbanAirshipResolver {

    private final Context context;

    public UrbanAirshipResolver(Context context) {
        this.context = context;
    }

    protected Cursor query(Uri uri, String[] projection, String whereClause, String[] whereArgs, String sortOrder) {

        try {
            return this.getResolver().query(uri, projection, whereClause, whereArgs, sortOrder);
        } catch (Exception e) {
            Logger.error("Failed to query the UrbanAirshipProvider.", e);
            return null;
        }
    }

    protected int delete(Uri uri, String whereClause, String[] whereArgs) {
        try {
            return this.getResolver().delete(uri, whereClause, whereArgs);
        } catch (Exception e) {
            Logger.error("Failed to perform a delete in UrbanAirshipProvider.", e);
            return -1;
        }
    }

    protected int update(Uri uri, ContentValues values, String whereClause, String[] whereArgs) {
        try {
            return this.getResolver().update(uri, values, whereClause, whereArgs);
        } catch (Exception e) {
            Logger.error("Failed to perform an update in UrbanAirshipProvider.", e);
            return 0;
        }
    }

    protected Uri insert(Uri uri, ContentValues values) {
        try {
            return this.getResolver().insert(uri, values);
        } catch (Exception e) {
            Logger.error("Failed to insert in UrbanAirshipProvider.", e);
            return null;
        }
    }

    protected int bulkInsert(Uri uri, ContentValues[] values) {
        try {
            return this.getResolver().bulkInsert(uri, values);
        } catch (Exception e) {
            Logger.error("Failed to bulk insert in UrbanAirshipProvider.", e);
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
    public void registerContentObserver(Uri uri, boolean notifyForDescendants, ContentObserver observer) {
        try {
            this.getResolver().registerContentObserver(uri, notifyForDescendants, observer);
        } catch (IllegalArgumentException e) {
            Logger.warn("Unable to register content observer for uri: " + uri);
        }
    }

    /**
     * Unregister the supplied ContentObserver.
     *
     * @param observer The ContentObserver you wish to unregister
     */
    public void unregisterContentObserver(ContentObserver observer) {
        this.getResolver().unregisterContentObserver(observer);
    }

    public void notifyChange(Uri uri, ContentObserver observer) {
        try {
            this.getResolver().notifyChange(uri, observer);
        } catch (IllegalArgumentException ex) {
            Logger.warn("Unable to notify observers of change for uri: " + uri);
        }
    }

    // helpers

    private ContentResolver getResolver() {
        return this.context.getContentResolver();
    }

}
