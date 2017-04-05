/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.location.UALocationManager;
import com.urbanairship.push.NamedUser;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FetchDeviceInfoActionTest extends BaseTestCase {

    private PushManager pushManager;
    private NamedUser namedUser;
    private UALocationManager locationManager;
    private FetchDeviceInfoAction action;

    @Action.Situation
    private int[] acceptedSituations;

    @Before
    public void setUp() {
        pushManager = mock(PushManager.class);
        namedUser = mock(NamedUser.class);
        locationManager = mock(UALocationManager.class);
        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setNamedUser(namedUser);
        TestApplication.getApplication().setLocationManager(locationManager);

        this.action = new FetchDeviceInfoAction();

        // Accepted situations
        acceptedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_PUSH_RECEIVED,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_AUTOMATION
        };
    }

    /**
     * Test accepts arguments.
     */
    @Test
    public void testAcceptsArguments() {
        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, null);
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test perform with valid JSON.
     */
    @Test
    public void testPerform() throws JsonException {
        List<String> tags = Arrays.asList("tag1", "tag2");
        String channelId = "channel_id";
        String namedUserId = "named_user";

        when(pushManager.getChannelId()).thenReturn(channelId);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.getTags()).thenReturn(new HashSet<>(tags));
        when(locationManager.isLocationUpdatesEnabled()).thenReturn(true);
        when(namedUser.getId()).thenReturn(namedUserId);

        JsonMap result = action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, null)).getValue().getMap();
        assertEquals(channelId, result.get(FetchDeviceInfoAction.CHANNEL_ID_KEY).getString());
        assertEquals(true, result.get(FetchDeviceInfoAction.PUSH_OPT_IN_KEY).getBoolean(false));
        assertEquals(JsonValue.wrap(tags), result.get(FetchDeviceInfoAction.TAGS_KEY));
        assertEquals(true, result.get(FetchDeviceInfoAction.LOCATION_ENABLED_KEY).getBoolean(false));
        assertEquals(namedUserId, result.get(FetchDeviceInfoAction.NAMED_USER_ID_KEY).getString());
    }

    /**
     * Test perform with valid JSON.
     */
    @Test
    public void testPerformWithoutTags() throws JsonException {
        String channelId = "channel_id";
        String namedUserId = "named_user";

        when(pushManager.getChannelId()).thenReturn(channelId);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.getTags()).thenReturn(new HashSet<String>());
        when(locationManager.isLocationUpdatesEnabled()).thenReturn(true);
        when(namedUser.getId()).thenReturn(namedUserId);

        JsonMap result = action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, null)).getValue().getMap();
        assertEquals(channelId, result.get(FetchDeviceInfoAction.CHANNEL_ID_KEY).getString());
        assertEquals(true, result.get(FetchDeviceInfoAction.PUSH_OPT_IN_KEY).getBoolean(false));
        assertNull(result.get(FetchDeviceInfoAction.TAGS_KEY));
        assertEquals(true, result.get(FetchDeviceInfoAction.LOCATION_ENABLED_KEY).getBoolean(false));
        assertEquals(namedUserId, result.get(FetchDeviceInfoAction.NAMED_USER_ID_KEY).getString());
    }
}
