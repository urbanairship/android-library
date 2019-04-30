/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.InAppMessageManager;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class OverlayRichPushMessageActionTest extends BaseTestCase {

    private OverlayRichPushMessageAction action;

    private InAppMessageManager inAppMessageManager;

    @Before
    public void setup() {
        action = new OverlayRichPushMessageAction();

        inAppMessageManager = mock(InAppMessageManager.class);
        getApplication().setInAppMessageManager(inAppMessageManager);
    }

    /**
     * Test accepts arguments with a message ID.
     */
    @Test
    public void testAcceptsArgumentMessageId() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "the_message_id");
        assertTrue(action.acceptsArguments(args));
    }

    /**
     * Test accepts arguments with "auto" placeholder when the push message metadata is available.
     */
    @Test
    public void testAcceptsArgumentsWithPlaceHolderPushMessageMetadata() {
        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, "messageId");

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "auto", metadata);
        assertTrue(action.acceptsArguments(args));
    }

    /**
     * Test accepts arguments with "auto" placeholder when the rich push message ID metadata is available.
     */
    @Test
    public void testAcceptsArgumentsWithPlaceHolderMessageIdMetadata() {

        Bundle metadata = new Bundle();
        metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, "the_message_id");

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "auto", metadata);
        assertTrue(action.acceptsArguments(args));
    }

    /**
     * Test rejects null argument value.
     */
    @Test
    public void testRejectsNullArgumentValue() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, null);
        assertFalse(action.acceptsArguments(args));
    }

    /**
     * Test rejects null argument value.
     */
    @Test
    public void testRejectsEmptyArgumentValue() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "");
        assertFalse(action.acceptsArguments(args));
    }

    /**
     * Test rejects null argument value.
     */
    @Test
    public void testRejectsPlaceHolderWithNoMetadata() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "auto", null);
        assertFalse(action.acceptsArguments(args));
    }

}
