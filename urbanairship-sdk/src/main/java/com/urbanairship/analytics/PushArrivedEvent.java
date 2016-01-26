/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.analytics;

import com.urbanairship.Logger;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Analytics event when a push arrives.
 *
 * @hide
 */
public class PushArrivedEvent extends Event {

    static final String TYPE = "push_arrived";

    /**
     * Default send ID assigned when absent from the push payload.
     */
    private static final String DEFAULT_SEND_ID = "MISSING_SEND_ID";

    private final String pushId;
    private final String metadata;

    /**
     * Constructor for PushArrivedEvent. You should not instantiate this class directly.
     *
     * @param message The associated PushMessage.
     */
    public PushArrivedEvent(PushMessage message) {
        super();
        this.pushId = message.getSendId();
        this.metadata = message.getMetadata();
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JSONObject getEventData() {

        JSONObject data = new JSONObject();

        try {
            if (!UAStringUtil.isEmpty(pushId)) {
                data.put(PUSH_ID_KEY, pushId);
            } else {
                data.put(PUSH_ID_KEY, DEFAULT_SEND_ID);
            }

            data.putOpt(METADATA_KEY, metadata);

            //connection info
            data.put(CONNECTION_TYPE_KEY, getConnectionType());

            String subtype = getConnectionSubType();
            if (!UAStringUtil.isEmpty(subtype)) {
                data.put(CONNECTION_SUBTYPE_KEY, subtype);
            }

            data.put(CARRIER_KEY, getCarrier());

        } catch (JSONException e) {
            Logger.error("PushArrivedEvent - Error constructing JSON data.", e);
        }

        return data;
    }
}
