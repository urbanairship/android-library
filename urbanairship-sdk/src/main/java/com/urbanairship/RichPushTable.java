/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

/**
 * Rich Push Message table definition
 * @hide
 */
public final class RichPushTable {
    public static final String COLUMN_NAME_MESSAGE_ID = "message_id";
    public static final String COLUMN_NAME_MESSAGE_URL = "message_url";
    public static final String COLUMN_NAME_MESSAGE_BODY_URL = "message_body_url";
    public static final String COLUMN_NAME_MESSAGE_READ_URL = "message_read_url";
    public static final String COLUMN_NAME_TITLE = "title";
    public static final String COLUMN_NAME_EXTRA = "extra";
    public static final String COLUMN_NAME_UNREAD = "unread";
    public static final String COLUMN_NAME_UNREAD_ORIG = "unread_orig";
    public static final String COLUMN_NAME_DELETED = "deleted";
    public static final String COLUMN_NAME_KEY = "_id";
    public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    public static final String COLUMN_NAME_RAW_MESSAGE_OBJECT = "raw_message_object";
    public static final String COLUMN_NAME_EXPIRATION_TIMESTAMP = "expiration_timestamp";

    public static final String TABLE_NAME = "richpush";
}
