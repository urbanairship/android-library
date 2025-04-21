package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestClock;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AttributeEditor;
import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.contacts.Contact;
import com.urbanairship.json.JsonValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SetAttributesActionTest extends BaseTestCase {

    private SetAttributesAction action;
    private AirshipChannel channel;
    private Contact contact;
    private TestClock clock;

    @Before
    public void setup() {
        channel = mock(AirshipChannel.class);
        contact = mock(Contact.class);
        action = new SetAttributesAction();
        clock = new TestClock();
        clock.currentTimeMillis = 1000;

        TestApplication.getApplication().setChannel(channel);
        TestApplication.getApplication().setContact(contact);
    }

    @Test
    public void testAcceptsArguments() throws ActionValueException, JSONException {
        // Channel attributes
        JSONObject channelSet = new JSONObject();
        channelSet.put("channel_attribute_1", 1);
        channelSet.put("channel_attribute_2", "value2");

        JSONArray channelRemove = new JSONArray(new String[]{"nothing", "not_important"});

        JSONObject channelObject = new JSONObject();
        channelObject.put("set", channelSet);
        channelObject.put("remove", channelRemove);

        // Named User attributes
        JSONObject namedUserSet = new JSONObject();
        namedUserSet.put("named_user_attribute_1", 4.99);
        namedUserSet.put("named_user_attribute_2", new Date(clock.currentTimeMillis).getTime());

        JSONArray namedUserRemove = new JSONArray(new String[]{"useless", "not_needed"});

        JSONObject namedUserObject = new JSONObject();
        namedUserObject.put("set", namedUserSet);
        namedUserObject.put("remove", namedUserRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("channel", channelObject);
        actionValue.put("named_user", namedUserObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));

        assertTrue(action.acceptsArguments(args));
    }

    @Test
    public void testAcceptsArgumentsChannel() throws ActionValueException, JSONException {
        // Channel attributes
        JSONObject channelSet = new JSONObject();
        channelSet.put("channel_attribute_1", 1);
        channelSet.put("channel_attribute_2", "value2");

        JSONArray channelRemove = new JSONArray(new String[]{"nothing", "not_important"});

        JSONObject channelObject = new JSONObject();
        channelObject.put("set", channelSet);
        channelObject.put("remove", channelRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("channel", channelObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));

        assertTrue(action.acceptsArguments(args));
    }

    @Test
    public void testAcceptsArgumentsNamedUser() throws ActionValueException, JSONException {
        // Named User attributes
        JSONObject namedUserSet = new JSONObject();
        namedUserSet.put("named_user_attribute_1", 4.99);
        namedUserSet.put("named_user_attribute_2", new Date(clock.currentTimeMillis).getTime());

        JSONArray namedUserRemove = new JSONArray(new String[]{"useless", "not_needed"});

        JSONObject namedUserObject = new JSONObject();
        namedUserObject.put("set", namedUserSet);
        namedUserObject.put("remove", namedUserRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("named_user", namedUserObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));

        assertTrue(action.acceptsArguments(args));
    }

    @Test
    public void testAcceptsArgumentsBothMixed() throws ActionValueException, JSONException {
        // Channel attributes
        JSONObject channelSet = new JSONObject();
        channelSet.put("channel_attribute_1", 1);

        JSONObject channelObject = new JSONObject();
        channelObject.put("set", channelSet);

        JSONArray namedUserRemove = new JSONArray(new String[]{"useless"});

        JSONObject namedUserObject = new JSONObject();
        namedUserObject.put("remove", namedUserRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("channel", channelObject);
        actionValue.put("named_user", namedUserObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));

        assertTrue(action.acceptsArguments(args));
    }

    @Test
    public void testAcceptsArgumentsWrongKeyNames() throws ActionValueException, JSONException {
        // Channel attributes
        JSONObject channelSet = new JSONObject();
        channelSet.put("channel_attribute_1", 1);
        channelSet.put("channel_attribute_2", "value2");

        JSONArray channelRemove = new JSONArray(new String[]{"nothing", "not_important"});

        JSONObject channelObject = new JSONObject();
        channelObject.put("set", channelSet);
        channelObject.put("remove", channelRemove);

        // Named User attributes
        JSONObject namedUserSet = new JSONObject();
        namedUserSet.put("named_user_attribute_1", 4.99);
        namedUserSet.put("named_user_attribute_2", new Date(clock.currentTimeMillis).getTime());

        JSONArray namedUserRemove = new JSONArray(new String[]{"useless", "not_needed"});

        JSONObject namedUserObject = new JSONObject();
        namedUserObject.put("set", namedUserSet);
        namedUserObject.put("remove", namedUserRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("chanel", channelObject);
        actionValue.put("nameduser", namedUserObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));

        assertFalse(action.acceptsArguments(args));
    }

    @Test
    public void testAcceptsArgumentsWrongSetObjectType() throws ActionValueException, JSONException {
        // Channel attributes
        JSONArray channelSet = new JSONArray(new String[]{"channel_attribute_1", "channel_attribute_2"});

        JSONArray channelRemove = new JSONArray(new String[]{"nothing", "not_important"});

        JSONObject channelObject = new JSONObject();
        channelObject.put("set", channelSet);
        channelObject.put("remove", channelRemove);

        // Named User attributes
        JSONObject namedUserSet = new JSONObject();
        namedUserSet.put("named_user_attribute_1", 4.99);
        namedUserSet.put("named_user_attribute_2", new Date(clock.currentTimeMillis).getTime());

        JSONArray namedUserRemove = new JSONArray(new String[]{"useless", "not_needed"});

        JSONObject namedUserObject = new JSONObject();
        namedUserObject.put("set", namedUserSet);
        namedUserObject.put("remove", namedUserRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("channel", channelObject);
        actionValue.put("named_user", namedUserObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));

        assertFalse(action.acceptsArguments(args));
    }

    @Test
    public void testAcceptsArgumentsWrongRemoveObjectType() throws ActionValueException, JSONException {
        // Channel attributes
        JSONObject channelSet = new JSONObject();
        channelSet.put("channel_attribute_1", 1);
        channelSet.put("channel_attribute_2", "value2");

        JSONObject channelRemove = new JSONObject();
        channelSet.put("nothing", "not_important");

        JSONObject channelObject = new JSONObject();
        channelObject.put("set", channelSet);
        channelObject.put("remove", channelRemove);

        // Named User attributes
        JSONObject namedUserSet = new JSONObject();
        namedUserSet.put("named_user_attribute_1", 4.99);
        namedUserSet.put("named_user_attribute_2", new Date(clock.currentTimeMillis).getTime());

        JSONArray namedUserRemove = new JSONArray(new String[]{"useless", "not_needed"});

        JSONObject namedUserObject = new JSONObject();
        namedUserObject.put("set", namedUserSet);
        namedUserObject.put("remove", namedUserRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("channel", channelObject);
        actionValue.put("named_user", namedUserObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));

        assertFalse(action.acceptsArguments(args));
    }

    @Test
    public void testPerformWithChannelAttributes() throws ActionValueException, JSONException {
        final Set<AttributeMutation> attributeMutations = new HashSet<>();
        AttributeEditor attributeEditor = new AttributeEditor(clock) {
            @Override
            protected void onApply(@NonNull List<? extends AttributeMutation> collapsedMutations) {
                attributeMutations.addAll(collapsedMutations);
            }
        };

        when(channel.editAttributes()).thenReturn(attributeEditor);

        // Channel attributes
        JSONObject channelSet = new JSONObject();
        channelSet.put("channel_attribute_1", 1);
        channelSet.put("channel_attribute_2", "value2");

        JSONArray channelRemove = new JSONArray(new String[]{"nothing", "not_important"});

        JSONObject channelObject = new JSONObject();
        channelObject.put("set", channelSet);
        channelObject.put("remove", channelRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("channel", channelObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));
        ActionResult result = action.perform(args);

        assertTrue(result.getValue().isNull());

        Set<AttributeMutation> expectedAttributeMutations = new HashSet<>();
        expectedAttributeMutations.add(AttributeMutation.newSetAttributeMutation("channel_attribute_1", JsonValue.wrap(1), 1000));
        expectedAttributeMutations.add(AttributeMutation.newSetAttributeMutation("channel_attribute_2", JsonValue.wrap("value2"), 1000));
        expectedAttributeMutations.add(AttributeMutation.newRemoveAttributeMutation("nothing", 1000));
        expectedAttributeMutations.add(AttributeMutation.newRemoveAttributeMutation("not_important", 1000));

        assertEquals(expectedAttributeMutations, attributeMutations);
    }

    @Test
    public void testPerformWithNamedUserAttributes() throws ActionValueException, JSONException {
        TestApplication.getApplication().setContact(contact);

        final Set<AttributeMutation> attributeMutations = new HashSet<>();
        AttributeEditor attributeEditor = new AttributeEditor(clock) {
            @Override
            protected void onApply(@NonNull List<? extends AttributeMutation> collapsedMutations) {
                attributeMutations.addAll(collapsedMutations);
            }
        };

        when(contact.editAttributes()).thenReturn(attributeEditor);

        // Named User attributes
        JSONObject namedUserSet = new JSONObject();
        namedUserSet.put("named_user_attribute_1", 4.99);
        namedUserSet.put("named_user_attribute_2", new Date(clock.currentTimeMillis).getTime());

        JSONArray namedUserRemove = new JSONArray(new String[]{"useless", "not_needed"});

        JSONObject namedUserObject = new JSONObject();
        namedUserObject.put("set", namedUserSet);
        namedUserObject.put("remove", namedUserRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("named_user", namedUserObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));
        ActionResult result = action.perform(args);

        assertTrue(result.getValue().isNull());

        Set<AttributeMutation> expectedAttributeMutations = new HashSet<>();
        expectedAttributeMutations.add(AttributeMutation.newSetAttributeMutation("named_user_attribute_1", JsonValue.wrap(4.99), 1000));
        expectedAttributeMutations.add(AttributeMutation.newSetAttributeMutation("named_user_attribute_2", JsonValue.wrap(1000), 1000));
        expectedAttributeMutations.add(AttributeMutation.newRemoveAttributeMutation("useless", 1000));
        expectedAttributeMutations.add(AttributeMutation.newRemoveAttributeMutation("not_needed", 1000));

        assertEquals(expectedAttributeMutations, attributeMutations);
    }

    @Test
    public void testPerform() throws ActionValueException, JSONException {
        TestApplication.getApplication().setChannel(channel);
        TestApplication.getApplication().setContact(contact);

        final Set<AttributeMutation> attributeMutations = new HashSet<>();
        AttributeEditor attributeEditor = new AttributeEditor(clock) {
            protected void onApply(@NonNull List<? extends AttributeMutation> collapsedMutations) {
                attributeMutations.addAll(collapsedMutations);
            }
        };

        when(channel.editAttributes()).thenReturn(attributeEditor);
        when(contact.editAttributes()).thenReturn(attributeEditor);

        // Channel attributes
        JSONObject channelSet = new JSONObject();
        channelSet.put("channel_attribute_1", 1);
        channelSet.put("channel_attribute_2", "value2");

        JSONArray channelRemove = new JSONArray(new String[]{"nothing", "not_important"});

        JSONObject channelObject = new JSONObject();
        channelObject.put("set", channelSet);
        channelObject.put("remove", channelRemove);

        // Named User attributes
        JSONObject namedUserSet = new JSONObject();
        namedUserSet.put("named_user_attribute_1", 4.99);
        namedUserSet.put("named_user_attribute_2", new Date(clock.currentTimeMillis).getTime());

        JSONArray namedUserRemove = new JSONArray(new String[]{"useless", "not_needed"});

        JSONObject namedUserObject = new JSONObject();
        namedUserObject.put("set", namedUserSet);
        namedUserObject.put("remove", namedUserRemove);

        // Action Value
        JSONObject actionValue = new JSONObject();
        actionValue.put("channel", channelObject);
        actionValue.put("named_user", namedUserObject);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(actionValue));
        ActionResult result = action.perform(args);

        assertTrue(result.getValue().isNull());

        Set<AttributeMutation> expectedAttributeMutations = new HashSet<>();
        expectedAttributeMutations.add(AttributeMutation.newSetAttributeMutation("channel_attribute_1", JsonValue.wrap(1), 1000));
        expectedAttributeMutations.add(AttributeMutation.newSetAttributeMutation("channel_attribute_2", JsonValue.wrap("value2"), 1000));
        expectedAttributeMutations.add(AttributeMutation.newRemoveAttributeMutation("nothing", 1000));
        expectedAttributeMutations.add(AttributeMutation.newRemoveAttributeMutation("not_important", 1000));

        expectedAttributeMutations.add(AttributeMutation.newSetAttributeMutation("named_user_attribute_1", JsonValue.wrap(4.99), 1000));
        expectedAttributeMutations.add(AttributeMutation.newSetAttributeMutation("named_user_attribute_2", JsonValue.wrap(1000), 1000));
        expectedAttributeMutations.add(AttributeMutation.newRemoveAttributeMutation("useless", 1000));
        expectedAttributeMutations.add(AttributeMutation.newRemoveAttributeMutation("not_needed", 1000));

        assertEquals(expectedAttributeMutations, attributeMutations);
    }

}
