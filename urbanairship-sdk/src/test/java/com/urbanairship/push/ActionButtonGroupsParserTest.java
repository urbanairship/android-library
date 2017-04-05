/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;

import com.urbanairship.BaseTestCase;
import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionButtonGroupsParserTest extends BaseTestCase {

    private Context context;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
    }

    /**
     * Test loading a resources that is not found returns an empty map.
     */
    @Test
    @SuppressWarnings("ResourceType")
    public void testLoadXmlMissingResource() {
        Map<String, NotificationActionButtonGroup> groups = ActionButtonGroupsParser.fromXml(context, -1);
        assertTrue(groups.isEmpty());
    }

    /**
     * Test loading groups.
     */
    @Test
    public void testDefaultGroups() {
        Map<String, NotificationActionButtonGroup> groups = ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_buttons);

        // We provide 37 groups
        assertEquals(37, groups.size());

        // Verify a random group
        NotificationActionButtonGroup yesNoGroup = groups.get("ua_yes_no_foreground");
        assertEquals(2, yesNoGroup.getNotificationActionButtons().size());

        NotificationActionButton yes  = yesNoGroup.getNotificationActionButtons().get(0);
        assertEquals("yes", yes.getId());
        assertEquals(R.string.ua_notification_button_yes, yes.getLabel());
        assertEquals(R.drawable.ua_ic_notification_button_accept, yes.getIcon());
        assertTrue(yes.isForegroundAction());

        NotificationActionButton no  = yesNoGroup.getNotificationActionButtons().get(1);
        assertEquals("no", no.getId());
        assertEquals(R.string.ua_notification_button_no, no.getLabel());
        assertEquals(R.drawable.ua_ic_notification_button_decline, no.getIcon());
        assertFalse(no.isForegroundAction());
    }
}