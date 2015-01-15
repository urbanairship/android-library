package com.urbanairship.actions.tags;/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.actions.Situation;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class RemoveTagsActionTest {

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
        ActionArguments args = ActionTestUtils.createArgs(Situation.PUSH_RECEIVED, Arrays.asList("tagOne", "tagThree"));
        ActionResult result = action.perform(args);

        assertNull("Remove tags action should return null", result.getValue());

        // Verify we only have tagTwo
        Set<String> expectedTags = new HashSet<>();
        expectedTags.add("tagTwo");
        verify(pushManager).setTags(expectedTags);
    }
}
