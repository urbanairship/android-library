/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.automation.tags.TagSelector;
import com.urbanairship.json.JsonException;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.urbanairship.automation.tags.TestUtils.tagSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link TagSelector} tests.
 */
@RunWith(AndroidJUnit4.class)
public class TagSelectorTest {

    @Test
    public void testJson() throws JsonException {
        TagSelector original = TagSelector.or(
                TagSelector.and(
                        TagSelector.tag("some-tag"),
                        TagSelector.not(TagSelector.tag("not-tag"))),
                TagSelector.tag("some-other-tag"));

        TagSelector fromJson = TagSelector.fromJson(original.toJsonValue());
        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test
    public void testSelector() {
        TagSelector selector = TagSelector.or(
                TagSelector.and(
                        TagSelector.tag("some-tag"),
                        TagSelector.not(TagSelector.tag("not-tag"))),
                TagSelector.tag("some-other-tag"));

        List<String> tags = new ArrayList<>();

        // Empty list
        assertFalse(selector.apply(tags));

        tags.add("some-tag");
        // Contains "some-tag" and not "not-tag"
        assertTrue(selector.apply(tags));

        tags.add("not-tag");
        // Contains "some-tag" and "not-tag"
        assertFalse(selector.apply(tags));

        tags.add("some-other-tag");
        // Contains "some-other-tag"
        assertTrue(selector.apply(tags));
    }

}
