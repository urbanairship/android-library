/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

/**
 * Common GCM Constants used by Urban Airship.
 * @deprecated Will be removed in 9.0.0.
 */
@Deprecated
public interface GcmConstants {
    /**
     * This intent action indicates a push notification has been received from GCM.
     */
    String ACTION_GCM_RECEIVE = "com.google.android.c2dm.intent.RECEIVE";

    /**
     * This intent action indicates a registration from GCM.
     */
    String ACTION_GCM_REGISTRATION = "com.google.android.c2dm.intent.REGISTRATION";

    /**
     * This intent action indicates a registration change.
     */
    String ACTION_INSTANCE_ID = "com.google.android.gms.iid.InstanceID";

    /**
     * The GCM Message Type extra is set by GCM and indicates the type of the incoming message intent. It is used
     * to indicate that a message originated in the GCM service (e.g., due to pending deletion).
     */
    String EXTRA_GCM_MESSAGE_TYPE = "message_type";

    /**
     * The total deleted extra indicates the number of pending notifications deleted by the GCM service. It is sent
     * in conjunction with a {@link #EXTRA_GCM_MESSAGE_TYPE} set to {@link #GCM_DELETED_MESSAGES_VALUE}.
     */
    String EXTRA_GCM_TOTAL_DELETED = "total_deleted";

    /**
     * The message type value sent in {@link #EXTRA_GCM_MESSAGE_TYPE} when GCM deleted pending messages.
     */
    String GCM_DELETED_MESSAGES_VALUE = "deleted_messages";
}
