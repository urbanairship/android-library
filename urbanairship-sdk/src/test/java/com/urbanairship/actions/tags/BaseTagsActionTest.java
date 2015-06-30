/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.actions.tags;

import com.urbanairship.BaseTestCase;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionTestUtils;
import com.urbanairship.actions.Situation;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class BaseTagsActionTest extends BaseTestCase {

    BaseTagsAction action;
    private Situation[] acceptedSituations;

    @Before
    public void setup() {
        action = new BaseTagsAction() {
            @Override
            public ActionResult perform(ActionArguments arguments) {
                return null;
            }
        };

        acceptedSituations = new Situation[] {
                Situation.PUSH_OPENED,
                Situation.MANUAL_INVOCATION,
                Situation.WEB_VIEW_INVOCATION,
                Situation.PUSH_RECEIVED
        };
    }

    /**
     * Test accepts arguments accepts any arguments that it can pass
     * a set of tags from
     */
    @Test
    public void testAcceptsArguments() {
        ActionArguments args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "tag1");
        assertTrue("Single tag is an acceptable argument", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "[tag1,tag2,tag3]");
        assertTrue("JSON string of tags is an acceptable argument", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, 1);
        assertFalse("Integer object is invalid arguments", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, null);
        assertFalse(action.acceptsArguments(args));

        // Check every accepted situation
        for (Situation situation : acceptedSituations) {
            args = ActionTestUtils.createArgs(situation, "tag1");
            assertTrue("Should accept arguments in situation " + situation,
                    action.acceptsArguments(args));
        }
    }

    /**
     * Test get tags parses the arguments correctly
     */
    @Test
    public void testGetTags() {
        ActionArguments singleTagArg = ActionTestUtils.createArgs(Situation.PUSH_OPENED, "tag1");
        Set<String> tags = action.getTags(singleTagArg);
        assertEquals(1, tags.size());
        assertTrue(tags.contains("tag1"));


        ActionArguments collectionArgs = ActionTestUtils.createArgs(Situation.PUSH_RECEIVED,
                Arrays.asList("tag1", "tag2", "tag3"));

        tags = action.getTags(collectionArgs);
        assertEquals(3, tags.size());
        assertTrue(tags.contains("tag1"));
        assertTrue(tags.contains("tag2"));
        assertTrue(tags.contains("tag3"));

        ActionArguments badArgs = ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, 1);
        assertNull(action.getTags(badArgs));

        ActionArguments nullArgs = ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, null);
        assertNull(action.getTags(nullArgs));
    }

}
