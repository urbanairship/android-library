/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import android.os.Build;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.PushManager;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppForegroundEventTest extends BaseTestCase {

    private AppForegroundEvent event;

    @Before
    public void setUp() {
        event = new AppForegroundEvent(1000);
    }

    @Test
    public void testConnectionType() throws JSONException {
        EventTestUtils.validateEventValue(event, Event.CONNECTION_TYPE_KEY, event.getConnectionType());
    }

    @Test
    public void testConnectionSubType() throws JSONException {
        EventTestUtils.validateEventValue(event, Event.CONNECTION_SUBTYPE_KEY, event.getConnectionSubType());
    }

    @Test
    public void testCarrier() throws JSONException {
        EventTestUtils.validateEventValue(event, Event.CARRIER_KEY, event.getCarrier());
    }

    @Test
    public void testTimezone() throws JSONException {
        EventTestUtils.validateEventValue(event, Event.TIME_ZONE_KEY, event.getTimezone());
    }

    @Test
    public void testDaylightSavingsTime() throws JSONException {
        EventTestUtils.validateEventValue(event, Event.DAYLIGHT_SAVINGS_KEY, event.isDaylightSavingsTime());
    }

    @Test
    public void testOsVersion() throws JSONException {
        EventTestUtils.validateEventValue(event, Event.OS_VERSION_KEY, Build.VERSION.RELEASE);
    }

    @Test
    public void testLibVersion() throws JSONException {
        EventTestUtils.validateEventValue(event, Event.LIB_VERSION_KEY, UAirship.getVersion());
    }

    @Test
    public void testPackageVersion() throws JSONException {
        EventTestUtils.validateEventValue(event, Event.PACKAGE_VERSION_KEY, UAirship.getPackageInfo().versionName);
    }

    @Test
    public void testPushId() throws JsonException {
        ConversionData conversionData = new ConversionData("send id", null, null);
        assertEquals(event.getEventData(conversionData).require(Event.PUSH_ID_KEY).requireString(), "send id");
    }

    @Test
    public void testPushMetadata() throws JsonException {
        ConversionData conversionData = new ConversionData(null, "metadata", null);
        assertEquals(event.getEventData(conversionData).require(Event.METADATA_KEY).requireString(), "metadata");
    }

    /**
     * Tests that the last metadata is included in the app foreground event
     * data
     */
    @Test
    public void testLastSendMetadata() throws JsonException {
        ConversionData conversionData = new ConversionData(null, "metadata", "last metadata");
        assertEquals(event.getEventData(conversionData).require(Event.LAST_METADATA_KEY).requireString(), "last metadata");
    }

}
