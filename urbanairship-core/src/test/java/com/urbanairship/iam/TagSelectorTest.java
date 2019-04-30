/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.JsonException;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.urbanairship.iam.tags.TestUtils.tagSet;
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
                        TagSelector.tag("some-group-tag", "some-group"),
                        TagSelector.not(TagSelector.tag("not-tag"))),
                TagSelector.tag("some-other-tag"));

        TagSelector fromJson = TagSelector.fromJson(original.toJsonValue());
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

    @Test
    public void testSelectorWithTagGroups() throws JsonException {
        TagSelector selector = TagSelector.and(
                TagSelector.tag("some-tag", "some-group-tag"),
                TagSelector.tag("some-tag"),
                TagSelector.not(TagSelector.tag("not-tag")));

        List<String> tags = new ArrayList<>();
        tags.add("some-tag");

        Map<String, Set<String>> tagGroups = new HashMap<>();
        tagGroups.put("wrong-group-tag", tagSet("some-tag"));
        tagGroups.put("some-group-tag", tagSet("wrong-tag"));

        assertFalse(selector.apply(tags, tagGroups));

        tagGroups.put("some-group-tag", tagSet("some-tag"));
        assertTrue(selector.apply(tags, tagGroups));
    }

    @Test
    public void testContainsTagGroups() throws JsonException {
        TagSelector selector = TagSelector.or(
                TagSelector.and(
                        TagSelector.tag("some-tag"),
                        TagSelector.not(TagSelector.tag("not-tag"))),
                TagSelector.tag("some-other-tag"));

        assertFalse(selector.containsTagGroups());
        assertTrue(selector.getTagGroups().isEmpty());

        TagSelector groupSelector = TagSelector.or(
                TagSelector.and(
                        TagSelector.tag("another-tag"),
                        TagSelector.tag("some-tag", "some-group"),
                        TagSelector.not(TagSelector.tag("not-tag", "some-other-group"))),
                TagSelector.tag("some-other-tag", "some-other-group"));

        assertTrue(groupSelector.containsTagGroups());
        assertFalse(groupSelector.getTagGroups().isEmpty());
    }

    @Test
    public void tesGetTagGroups() throws JsonException {
        TagSelector selector = TagSelector.or(
                TagSelector.and(
                        TagSelector.tag("another-tag"),
                        TagSelector.tag("some-tag", "some-group"),
                        TagSelector.not(TagSelector.tag("not-tag", "some-other-group"))),
                TagSelector.tag("some-other-tag", "some-other-group"));

        Map<String, Set<String>> tagGroups = selector.getTagGroups();

        assertEquals(2, tagGroups.size());

        // some-group
        assertTrue(tagGroups.containsKey("some-group"));
        assertEquals(1, tagGroups.get("some-group").size());
        assertTrue(tagGroups.get("some-group").contains("some-tag"));

        // some-other-group
        assertTrue(tagGroups.containsKey("some-other-group"));
        assertEquals(2, tagGroups.get("some-other-group").size());
        assertTrue(tagGroups.get("some-other-group").contains("some-other-tag"));
        assertTrue(tagGroups.get("some-other-group").contains("not-tag"));
    }

}