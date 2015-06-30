package com.urbanairship.analytics;

import android.os.Build;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppForegroundEventTest extends BaseTestCase {

    private AppForegroundEvent event;
    private JSONObject data;

    private PushManager mockPush;
    private Analytics analytics;

    @Before
    public void setUp() {
        event = new AppForegroundEvent(1000);
        data = event.getEventData();

        mockPush = Mockito.mock(PushManager.class);
        TestApplication.getApplication().setPushManager(mockPush);

        analytics = mock(Analytics.class);
        TestApplication.getApplication().setAnalytics(analytics);
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
    public void testNotificationTypes() throws JSONException {
        JSONObject data = event.getEventData();
        JSONArray typesJSON = (JSONArray) data.get(AppForegroundEvent.NOTIFICATION_TYPES_KEY);
        ArrayList<String> typesList = new ArrayList<>();
        for (int i = 0; i < typesJSON.length(); i++) {
            typesList.add((String) typesJSON.get(i));
        }
        assertEquals(typesList, event.getNotificationTypes());
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
    public void testPushId() throws JSONException {
        when(analytics.getConversionSendId()).thenReturn("send id");
        EventTestUtils.validateEventValue(event, Event.PUSH_ID_KEY, "send id");
    }

    /**
     * Tests that the last send id is included in the app foreground event
     * data
     */
    @Test
    public void testLastSendId() throws JSONException {
        when(mockPush.getLastReceivedSendId()).thenReturn("last send id");
        EventTestUtils.validateEventValue(event, Event.LAST_SEND_ID_KEY, "last send id");
    }
}
