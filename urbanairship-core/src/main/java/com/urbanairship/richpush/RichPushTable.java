/* Copyright Airship and Contributors */

package com.urbanairship.richpush;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Rich Push Message table definition
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class RichPushTable {

    @NonNull
    public static final String COLUMN_NAME_MESSAGE_ID = "message_id";

    @NonNull
    public static final String COLUMN_NAME_MESSAGE_URL = "message_url";

    @NonNull
    public static final String COLUMN_NAME_MESSAGE_BODY_URL = "message_body_url";

    @NonNull
    public static final String COLUMN_NAME_MESSAGE_READ_URL = "message_read_url";

    @NonNull
    public static final String COLUMN_NAME_TITLE = "title";

    @NonNull
    public static final String COLUMN_NAME_EXTRA = "extra";

    @NonNull
    public static final String COLUMN_NAME_UNREAD = "unread";

    @NonNull
    public static final String COLUMN_NAME_UNREAD_ORIG = "unread_orig";

    @NonNull
    public static final String COLUMN_NAME_DELETED = "deleted";

    @NonNull
    public static final String COLUMN_NAME_KEY = "_id";

    @NonNull
    public static final String COLUMN_NAME_TIMESTAMP = "timestamp";

    @NonNull
    public static final String COLUMN_NAME_RAW_MESSAGE_OBJECT = "raw_message_object";

    @NonNull
    public static final String COLUMN_NAME_EXPIRATION_TIMESTAMP = "expiration_timestamp";

    @NonNull
    public static final String TABLE_NAME = "richpush";

}
