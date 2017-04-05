/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.iam;

import android.support.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.analytics.Event;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.UAStringUtil;

/**
 * An event that is sent the first time an {@link InAppMessage} is displayed.
 *
 * @hide
 */
public class DisplayEvent extends Event {
    private static final String TYPE = "in_app_display";

    private static final String ID = "id";
    private static final String CONVERSION_SEND_ID = "conversion_send_id";
    private static final String CONVERSION_METADATA = "conversion_metadata";

    private final String id;

    /**
     * Default constructor.
     *
     * @param message The in-app message.
     */
    public DisplayEvent(@NonNull InAppMessage message) {
        id = message.getId();
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JsonMap getEventData() {
        return JsonMap.newBuilder()
                .put(ID, id)
                .put(CONVERSION_SEND_ID, UAirship.shared().getAnalytics().getConversionSendId())
                .put(CONVERSION_METADATA, UAirship.shared().getAnalytics().getConversionMetadata())
                .build();
    }

    @Override
    public boolean isValid() {
        return !UAStringUtil.isEmpty(id);
    }
}
