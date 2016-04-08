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
