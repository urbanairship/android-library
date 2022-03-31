/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.SubscriptionListEditor;
import com.urbanairship.channel.SubscriptionListMutation;
import com.urbanairship.contacts.Contact;
import com.urbanairship.contacts.ScopedSubscriptionListEditor;
import com.urbanairship.contacts.ScopedSubscriptionListMutation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubscriptionListActionTest extends BaseTestCase {

    private SubscriptionListAction action;
    private AirshipChannel channel;
    private Contact contact;
    private TestClock clock;

    @Before
    public void setup() {
        channel = mock(AirshipChannel.class);
        contact = mock(Contact.class);
        action = new SubscriptionListAction();
        clock = new TestClock();
        clock.currentTimeMillis = 1000;

        TestApplication.getApplication().setChannel(channel);
        TestApplication.getApplication().setContact(contact);
    }

    @Test
    public void testAcceptsArguments() throws ActionValueException, JSONException {
        JSONArray payloadArray = new JSONArray();
        JSONObject payload = new JSONObject();
        payload.put("type", "contact");
        payload.put("action", "subscribe");
        payload.put("list", "mylist");
        payload.put("scope", "app");

        JSONObject payload2 = new JSONObject();
        payload2.put("type", "channel");
        payload2.put("action", "unsubscribe");
        payload2.put("list", "thelist");
        payload2.put("scope", "app");

        payloadArray.put(payload);
        payloadArray.put(payload2);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(payloadArray));

        assertTrue(action.acceptsArguments(args));
    }

    @Test
    public void testPerform() throws JSONException, ActionValueException {
        final List<SubscriptionListMutation> expected = new ArrayList<>();
        final List<SubscriptionListMutation> mutations = new ArrayList<>();
        final List<ScopedSubscriptionListMutation> contactMutations = new ArrayList<>();

        JSONArray payloadArray = new JSONArray();
        JSONObject payload = new JSONObject();
        payload.put("type", "channel");
        payload.put("action", "subscribe");
        payload.put("list", "mylist");
        payload.put("scope", "app");

        JSONObject payload2 = new JSONObject();
        payload2.put("type", "channel");
        payload2.put("action", "unsubscribe");
        payload2.put("list", "thelist");
        payload2.put("scope", "app");

        payloadArray.put(payload);
        payloadArray.put(payload2);

        SubscriptionListEditor channelEditor = new SubscriptionListEditor(clock) {
            @Override
            protected void onApply(@NonNull List<SubscriptionListMutation> collapsedMutations) {
                mutations.addAll(collapsedMutations);
            }
        };

        ScopedSubscriptionListEditor contactEditor = new ScopedSubscriptionListEditor(clock) {
            @Override
            protected void onApply(@NonNull List<ScopedSubscriptionListMutation> collapsedMutations) {
                contactMutations.addAll(collapsedMutations);
            }
        };

        expected.addAll(Arrays.asList(
                    SubscriptionListMutation.newSubscribeMutation("mylist", 1000),
                SubscriptionListMutation.newUnsubscribeMutation("thelist", 1000)));

        when(channel.editSubscriptionLists()).thenReturn(channelEditor);
        when(contact.editSubscriptionLists()).thenReturn(contactEditor);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(payloadArray));
        ActionResult result = action.perform(args);

        assertEquals(result.getValue(), args.getValue());
        assertEquals(expected, mutations);
    }
}
