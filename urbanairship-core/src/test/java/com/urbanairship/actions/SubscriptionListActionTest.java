/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;
import com.urbanairship.channel.SubscriptionListEditor;
import com.urbanairship.channel.SubscriptionListMutation;
import com.urbanairship.contacts.Scope;
import com.urbanairship.contacts.ScopedSubscriptionListEditor;
import com.urbanairship.contacts.ScopedSubscriptionListMutation;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class SubscriptionListActionTest extends BaseTestCase {

    private static final String VALID_ARG = "[\n" +
            "     {\n" +
            "        \"type\": \"contact\",\n" +
            "        \"action\": \"subscribe\",\n" +
            "        \"list\": \"mylist\",\n" +
            "        \"scope\": \"app\"\n" +
            "     },\n" +
            "     {\n" +
            "        \"type\": \"channel\",\n" +
            "        \"action\": \"unsubscribe\",\n" +
            "       \"list\": \"thelist\"\n" +
            "     }\n" +
            " ]";

    private final List<ScopedSubscriptionListMutation> contactMutations = new ArrayList<>();
    private final TestClock clock = new TestClock();
    final SubscriptionListEditor channelEditor = new SubscriptionListEditor(clock) {
        @Override
        protected void onApply(@NonNull List<SubscriptionListMutation> collapsedMutations) {
            channelMutations.addAll(collapsedMutations);
        }
    };

    private final List<SubscriptionListMutation> channelMutations = new ArrayList<>();
    private final ScopedSubscriptionListEditor contactEditor = new ScopedSubscriptionListEditor(clock) {
        @Override
        protected void onApply(@NonNull List<ScopedSubscriptionListMutation> collapsedMutations) {
            contactMutations.addAll(collapsedMutations);
        }
    };

    private final SubscriptionListAction action = new SubscriptionListAction(() -> channelEditor, () -> contactEditor);

    @Test
    public void testAcceptsArguments() throws ActionValueException, JsonException {
        int[] acceptedSituations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_AUTOMATION,
                Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        // Check every accepted situation
        for (@Action.Situation int situation : acceptedSituations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, ActionValue.wrap(JsonValue.parseString(VALID_ARG)));
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    @Test
    public void testRejectArguments() throws ActionValueException, JsonException {
        ActionArguments invalidSituation = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, ActionValue.wrap(JsonValue.parseString(VALID_ARG)));
        assertFalse(action.acceptsArguments(invalidSituation));

        ActionArguments emptyValue = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(
                JsonValue.NULL));
        assertFalse(action.acceptsArguments(emptyValue));
    }

    @Test
    public void testPerform() throws JsonException {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(JsonValue.parseString(VALID_ARG)));
        action.perform(args);

        ScopedSubscriptionListMutation expectedContactMutation = ScopedSubscriptionListMutation.newSubscribeMutation("mylist", Scope.APP, clock.currentTimeMillis);
        assertEquals(Collections.singletonList(expectedContactMutation), contactMutations);

        SubscriptionListMutation expectedChannelMutation = SubscriptionListMutation.newUnsubscribeMutation("thelist", clock.currentTimeMillis);
        assertEquals(Collections.singletonList(expectedChannelMutation), channelMutations);
    }

    @Test
    public void testPerformInvalidArg() throws JsonException {
        String invalidArg = "[\n" +
                "     {\n" +
                "        \"type\": \"contact\",\n" +
                "        \"action\": \"subscribe\",\n" +
                "        \"list\": \"mylist\",\n" +
                "        \"scope\": \"app\"\n" +
                "     },\n" +
                "     {\n" +
                "        \"action\": \"unsubscribe\",\n" +
                "       \"list\": \"thelist\"\n" +
                "     }\n" +
                " ]";

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, ActionValue.wrap(JsonValue.parseString(invalidArg)));
        action.perform(args);

        // Should skip all edits even if one is valid
        assertTrue(contactMutations.isEmpty());
        assertTrue(channelMutations.isEmpty());
    }

}
