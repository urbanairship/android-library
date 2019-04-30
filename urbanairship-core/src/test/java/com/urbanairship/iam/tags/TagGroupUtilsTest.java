/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.urbanairship.iam.tags.TestUtils.tagSet;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * {@link TagGroupUtils} tests.
 */
public class TagGroupUtilsTest extends BaseTestCase {

    /**
     * Tests union.
     */
    @Test
    public void testUnion() {
        Map<String, Set<String>> tagGroupsOne = new HashMap<>();
        tagGroupsOne.put("shared-group", tagSet("cool", "awesome"));
        tagGroupsOne.put("unique-group", tagSet("nice", "amazing"));

        Map<String, Set<String>> tagGroupsTwo = new HashMap<>();
        tagGroupsTwo.put("shared-group", tagSet("rad"));

        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("shared-group", tagSet("cool", "awesome", "rad"));
        expected.put("unique-group", tagSet("nice", "amazing"));

        assertEquals(expected, TagGroupUtils.union(tagGroupsOne, tagGroupsTwo));
        assertEquals(expected, TagGroupUtils.union(tagGroupsTwo, tagGroupsOne));
    }

    /**
     * Tests add all.
     */
    @Test
    public void testAddAll() {
        Map<String, Set<String>> tags = new HashMap<>();

        Map<String, Set<String>> tagGroupsOne = new HashMap<>();
        tagGroupsOne.put("shared-group", tagSet("cool", "awesome"));
        tagGroupsOne.put("unique-group", tagSet("nice", "amazing"));

        Map<String, Set<String>> tagGroupsTwo = new HashMap<>();
        tagGroupsTwo.put("shared-group", tagSet("rad"));

        TagGroupUtils.addAll(tags, tagGroupsOne);
        assertEquals(tags, tagGroupsOne);

        TagGroupUtils.addAll(tags, tagGroupsTwo);
        assertEquals(tags, TagGroupUtils.union(tagGroupsOne, tagGroupsTwo));
    }

    /**
     * Tests intersect.
     */
    @Test
    public void testIntersect() {
        Map<String, Set<String>> tagGroupsOne = new HashMap<>();
        tagGroupsOne.put("shared-group", tagSet("cool", "story"));
        tagGroupsOne.put("unique-group", tagSet("cool", "story"));

        Map<String, Set<String>> tagGroupsTwo = new HashMap<>();
        tagGroupsTwo.put("shared-group", tagSet("cool"));

        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("shared-group", tagSet("cool"));

        assertEquals(expected, TagGroupUtils.intersect(tagGroupsOne, tagGroupsTwo));
        assertEquals(expected, TagGroupUtils.intersect(tagGroupsTwo, tagGroupsOne));
    }

    /**
     * Tests contains all.
     */
    @Test
    public void testContainsAll() {
        Map<String, Set<String>> fullSet = new HashMap<>();
        fullSet.put("shared-group", tagSet("cool", "story"));
        fullSet.put("unique-group", tagSet("cool", "story"));

        Map<String, Set<String>> subSet = new HashMap<>();
        subSet.put("shared-group", tagSet("cool"));

        assertTrue(TagGroupUtils.containsAll(fullSet, subSet));
        assertFalse(TagGroupUtils.containsAll(subSet, fullSet));
    }

}