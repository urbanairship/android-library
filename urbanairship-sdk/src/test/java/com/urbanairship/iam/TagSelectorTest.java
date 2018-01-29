/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * {@link TagSelector} tests.
 */
public class TagSelectorTest extends BaseTestCase {

    @Test
    public void testJson() throws JsonException {
        TagSelector original = TagSelector.or(
                TagSelector.and(
                        TagSelector.tag("some-tag"),
                        TagSelector.not(TagSelector.tag("not-tag"))),
                TagSelector.tag("some-other-tag"));


        TagSelector fromJson = TagSelector.parseJson(original.toJsonValue());
        assertEquals(original, fromJson);
        assertEquals(original.hashCode(), fromJson.hashCode());
    }

    @Test
    public void testSelector() throws JsonException {
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