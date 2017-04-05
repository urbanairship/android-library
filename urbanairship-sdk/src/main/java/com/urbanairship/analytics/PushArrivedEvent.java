/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

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
    protected final JsonMap getEventData() {

        return JsonMap.newBuilder()
                .put(PUSH_ID_KEY, !UAStringUtil.isEmpty(pushId) ? pushId : DEFAULT_SEND_ID)
                .put(METADATA_KEY, metadata)
                .put(CONNECTION_TYPE_KEY, getConnectionType())
                .put(CONNECTION_SUBTYPE_KEY, getConnectionSubType())
                .put(CARRIER_KEY, getCarrier())
                .build();
    }
}
