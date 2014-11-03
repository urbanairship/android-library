/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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

    private Context context;

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
     * @param notifyForDescendents If <code>true</code> changes to URIs beginning with <code>uri</code>
     * will also cause notifications to be sent. If <code>false</code> only changes to the exact URI
     * specified by <em>uri</em> will cause notifications to be sent. If true, than any URI values
     * at or below the specified URI will also trigger a match.
     * @param observer The ContentObserver you want to alert when the supplied URI is updated.
     */
    public void registerContentObserver(Uri uri, boolean notifyForDescendents, ContentObserver observer) {
        this.getResolver().registerContentObserver(uri, notifyForDescendents, observer);
    }

    /**
     * Unregister the supplied ContentObserver.
     *
     * @param observer The ContentObserver you wish to unregister
     */
    public void unregisterContentObserver(ContentObserver observer) {
        this.getResolver().unregisterContentObserver(observer);
    }

    // helpers

    private ContentResolver getResolver() {
        return this.context.getContentResolver();
    }
}
