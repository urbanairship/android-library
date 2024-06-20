/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppBackgroundEventTest extends BaseTestCase {

    @Test
    public void testEventData() throws JsonException {
        AppBackgroundEvent event = new AppBackgroundEvent(100);

        ConversionData conversionData = new ConversionData("send id", " send metadata", "last metadata");
        JsonMap eventData = event.getEventData(conversionData);

        assertEquals(eventData.require(Event.CONNECTION_TYPE_KEY).optString(), event.getConnectionType());
        assertEquals(eventData.require(Event.PUSH_ID_KEY).optString(), conversionData.getConversionSendId());
        assertEquals(eventData.require(Event.METADATA_KEY).optString(), conversionData.getConversionMetadata());
    }

}
