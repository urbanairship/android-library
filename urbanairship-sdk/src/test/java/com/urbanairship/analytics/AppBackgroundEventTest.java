/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppBackgroundEventTest extends BaseTestCase {

    private AppBackgroundEvent event;
    private Analytics analytics;

    @Before
    public void setUp() {
        analytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(analytics);

        event = new AppBackgroundEvent(100);
    }

    @Test
    public void testEventData() throws JSONException {
        when(analytics.getConversionSendId()).thenReturn("send id");
        when(analytics.getConversionMetadata()).thenReturn("send metadata");

        EventTestUtils.validateEventValue(event, Event.CONNECTION_TYPE_KEY, event.getConnectionType());
        EventTestUtils.validateEventValue(event, Event.CONNECTION_SUBTYPE_KEY, event.getConnectionSubType());
        EventTestUtils.validateEventValue(event, Event.PUSH_ID_KEY, "send id");
        EventTestUtils.validateEventValue(event, Event.METADATA_KEY, "send metadata");
    }
}