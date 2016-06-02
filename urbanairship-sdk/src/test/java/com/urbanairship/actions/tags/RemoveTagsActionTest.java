/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RemoveTagsActionTest extends BaseTestCase {

    RemoveTagsAction action;
    PushManager pushManager;

    @Before
    public void setup() {
        pushManager = mock(PushManager.class);
        TestApplication.getApplication().setPushManager(pushManager);

        action = new RemoveTagsAction();
    }

    /**
     * Test perform, should remove tags
     */
    @Test
    public void testPerform() {
        Set<String> existingTags = new HashSet<>();
        existingTags.add("tagOne");
        existingTags.add("tagTwo");

        when(pushManager.getTags()).thenReturn(existingTags);


        // Remove tagOne and tagThree
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, Arrays.asList("tagOne", "tagThree"));
        ActionResult result = action.perform(args);

        assertTrue("Remove tags action should return 'null' result", result.getValue().isNull());

        // Verify we only have tagTwo
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tagTwo");
        verify(pushManager).setTags(expectedTags);
    }
}
