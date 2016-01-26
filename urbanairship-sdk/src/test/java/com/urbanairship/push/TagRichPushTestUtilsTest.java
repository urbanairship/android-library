/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.push;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class TagRichPushTestUtilsTest extends BaseTestCase {

    /**
     * Test converting a JsonValue to a tags map
     */
    @Test
    public void testConvertToTagsMap() throws JsonException {
        Map<String, Set<String>> tagGroups = new HashMap<>();
        Set<String> tags = new HashSet<>();
        tags.add("tag1");
        tags.add("tag2");
        tags.add("tag3");
        tagGroups.put("tagGroup", tags);

        JsonValue jsonValue = JsonValue.wrap(tagGroups);

        Map<String, Set<String>> map = TagUtils.convertToTagsMap(jsonValue);
        assertEquals("Map size mismatch", map.size(), tagGroups.size());
        assertTrue("Value mismatch", map.containsValue(tags));
    }

    /**
     * Test converting a null and non-JsonMap returns empty map
     */
    @Test
    public void testConvertNullToTagsMap() throws JsonException {
        Map<String, Set<String>> emptyMap = new HashMap<>();
        assertEquals("Should be emptyMap", emptyMap, TagUtils.convertToTagsMap(null));

        JsonValue jsonString = JsonValue.parseString("non-JsonMap");
        assertEquals("Should be emptyMap", emptyMap, TagUtils.convertToTagsMap(jsonString));
    }
}
