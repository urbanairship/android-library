/* Copyright 2017 Urban Airship and Contributors */

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
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class TagUtilsTest extends BaseTestCase {

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
     * Test converting a null and non-JsonMap returns null.
     */
    @Test
    public void testConvertNullToTagsMap() throws JsonException {
        assertNull(TagUtils.convertToTagsMap(null));

        JsonValue jsonString = JsonValue.parseString("non-JsonMap");
        assertNull(TagUtils.convertToTagsMap(jsonString));
    }
}
