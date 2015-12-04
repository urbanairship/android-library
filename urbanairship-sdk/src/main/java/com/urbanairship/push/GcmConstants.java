/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.push;

/**
 * Common GCM Constants used by Urban Airship.
 */
public interface GcmConstants {
    /**
     * This intent action indicates a push notification has been received from GCM.
     */
    String ACTION_GCM_RECEIVE = "com.google.android.c2dm.intent.RECEIVE";

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
