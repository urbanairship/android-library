/* Copyright Airship and Contributors */

package com.urbanairship.json;

import com.urbanairship.BaseTestCase;
import com.urbanairship.json.matchers.ExactValueMatcher;
import com.urbanairship.json.matchers.StringBeginsMatcher;
import com.urbanairship.json.matchers.StringContainsMatcher;
import com.urbanairship.json.matchers.StringEndsMatcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ValueMatcher} tests.
 */
public class ValueMatcherTest extends BaseTestCase {

    @Test
    public void testValueMatcher() throws JsonException {
        JsonValue value = JsonValue.wrap(5);
        ValueMatcher matcher = ValueMatcher.newValueMatcher(value);

        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap(6);
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));

        value = JsonValue.wrap(true);
        matcher = ValueMatcher.newValueMatcher(value);

        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap(false);
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));

        value = JsonValue.wrap("test");
        matcher = ValueMatcher.newValueMatcher(value);
        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap("TEST");
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap("wrong");
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));

        value = JsonValue.wrap(5.0);
        matcher = ValueMatcher.newValueMatcher(value);
        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap(6.0);
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));

        value = JsonValue.wrap(new Object[] { "first-value", "second-value", null });
        assertNotNull(value);
        matcher = ValueMatcher.newValueMatcher(value);
        assertNotNull(matcher);

        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap(new Object[] { "FIRST-Value", "Second-Value", null });
        assertNotNull(value);

        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        Map<String, Object> map = new HashMap<>();
        map.put("null-key", null);
        map.put("some-key", "some-value");
        map.put("another-key", "another-value");

        value = JsonValue.wrap(map);
        assertNotNull(value);
        matcher = ValueMatcher.newValueMatcher(value);
        assertNotNull(matcher);

        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        map = new HashMap<>();
        map.put("null-key", null);
        map.put("some-key", "Some-Value");
        map.put("another-key", "ANOTHER-VALUE");

        value = JsonValue.wrap(map);
        assertNotNull(value);

        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));
    }

    @Test
    public void testNumberRangeMatcher() {
        Double min = 5.0;
        Double max = null;
        JsonValue value = JsonValue.wrap(6.0);
        ValueMatcher matcher = ValueMatcher.newNumberRangeMatcher(min, max);
        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap(4.0);
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));

        min = 5.0;
        max = 7.0;
        value = JsonValue.wrap(6.0);
        matcher = ValueMatcher.newNumberRangeMatcher(min, max);
        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap(4.0);
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));

        min = null;
        max = 7.0;
        value = JsonValue.wrap(6.0);
        matcher = ValueMatcher.newNumberRangeMatcher(min, max);
        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        value = JsonValue.wrap(8.0);
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));
    }

    @Test
    public void testAbsenceMatcher() {
        JsonValue value = JsonValue.NULL;
        ValueMatcher matcher = ValueMatcher.newIsAbsentMatcher();
        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        matcher = ValueMatcher.newIsPresentMatcher();
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));

        value = JsonValue.wrap("value");
        assertTrue(matcher.apply(value));
        assertTrue(matcher.apply(value, false));
        assertTrue(matcher.apply(value, true));

        matcher = ValueMatcher.newIsAbsentMatcher();
        assertFalse(matcher.apply(value));
        assertFalse(matcher.apply(value, false));
        assertFalse(matcher.apply(value, true));
    }

    @Test
    public void testVersionMatcher() {
        ValueMatcher matcher = ValueMatcher.newVersionMatcher("1.+");

        assertTrue(matcher.apply(JsonValue.wrap("1.2.4")));
        assertTrue(matcher.apply(JsonValue.wrap("1.2.4"), false));
        assertTrue(matcher.apply(JsonValue.wrap("1.2.4"), true));

        assertFalse(matcher.apply(JsonValue.wrap("2.0.0")));
        assertFalse(matcher.apply(JsonValue.wrap("2.0.0"), false));
        assertFalse(matcher.apply(JsonValue.wrap("2.0.0"), true));
    }

    @Test
    public void testArrayContainsMatcher() {
        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt("bingo")))
                                                                      .build())
                                               .build();

        JsonPredicate ignoreCasePredicate = JsonPredicate.newBuilder()
                                                         .addMatcher(JsonMatcher.newBuilder()
                                                                                .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt("bingo")))
                                                                                .setIgnoreCase(true)
                                                                                .build())
                                                         .build();

        JsonValue elements = JsonValue.wrapOpt(Arrays.asList("that's", "a", "bingo"));

        assertTrue(ValueMatcher.newArrayContainsMatcher(predicate).apply(elements));
        assertTrue(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate).apply(elements));

        assertTrue(ValueMatcher.newArrayContainsMatcher(predicate, 2).apply(elements));
        assertTrue(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 2).apply(elements));

        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 1).apply(elements));
        assertFalse(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 1).apply(elements));

        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 0).apply(elements));
        assertFalse(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 0).apply(elements));

        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, -1).apply(elements));
        assertFalse(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, -1).apply(elements));

        elements = JsonValue.wrapOpt(Arrays.asList("that's", "a", "BINGO"));

        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate).apply(elements));
        assertTrue(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate).apply(elements));

        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 2).apply(elements));
        assertTrue(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 2).apply(elements));

        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 1).apply(elements));
        assertFalse(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 1).apply(elements));
    }

    @Test
    public void testArrayLengthMatcher() throws JsonException {
        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt(3)))
                                                                      .build())
                                               .build();

        JsonPredicate wrongPredicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt(4)))
                                                                      .build())
                                               .build();

        JsonValue elements = JsonValue.wrapOpt(Arrays.asList("toto", "titi", "tite"));
        JsonValue arrayString = JsonValue.parseString("[\"android\", \"ios\", \"unknown\"]");

        assertTrue(ValueMatcher.newArrayLengthMatcher(predicate).apply(elements));
        assertFalse(ValueMatcher.newArrayLengthMatcher(wrongPredicate).apply(elements));
        assertTrue(ValueMatcher.newArrayLengthMatcher(predicate).apply(arrayString));
        assertFalse(ValueMatcher.newArrayLengthMatcher(wrongPredicate).apply(arrayString));
    }

    @Test
    public void testParse() throws JsonException {
        Double min = 5.0;
        Double max = 7.0;
        JsonValue json = JsonMap.newBuilder()
                                .put("at_least", min)
                                .put("at_most", max)
                                .build()
                                .toJsonValue();

        ValueMatcher matcher = ValueMatcher.newNumberRangeMatcher(min, max);
        assertEquals(matcher, ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put("is_present", true)
                      .build()
                      .toJsonValue();

        matcher = ValueMatcher.newIsPresentMatcher();
        assertEquals(matcher, ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put("equals", "string")
                      .build()
                      .toJsonValue();

        matcher = ValueMatcher.newValueMatcher(JsonValue.wrap("string"));
        assertEquals(matcher, ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put("equals", "string")
                      .put("ignore_case", true)
                      .build()
                      .toJsonValue();

        matcher = ValueMatcher.newValueMatcher(JsonValue.wrap("string"));
        assertEquals(matcher, ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put("equals", "string")
                      .put("ignore_case", true)
                      .build()
                      .toJsonValue();

        matcher = ValueMatcher.newValueMatcher(JsonValue.wrap("string"));
        assertEquals(matcher, ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put("version_matches", "1.2.4")
                      .build()
                      .toJsonValue();

        matcher = ValueMatcher.newVersionMatcher("1.2.4");
        assertEquals(matcher, ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put("version", "1.2.4")
                      .build()
                      .toJsonValue();

        matcher = ValueMatcher.newVersionMatcher("1.2.4");
        assertEquals(matcher, ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put(StringBeginsMatcher.STRING_BEGINS, "hello")
                      .build()
                      .toJsonValue();
        assertEquals(new StringBeginsMatcher(JsonValue.wrap("hello")), ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put(StringEndsMatcher.STRING_ENDS, "goodbye")
                      .build()
                      .toJsonValue();
        assertEquals(new StringEndsMatcher(JsonValue.wrap("goodbye")), ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put(StringContainsMatcher.STRING_CONTAINS, "world")
                      .build()
                      .toJsonValue();
        assertEquals(new StringContainsMatcher(JsonValue.wrap("world")), ValueMatcher.parse(json));
    }

    @Test
    public void testParseArrayContainsMatcher() throws JsonException {
        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt("bingo")))
                                                                      .build())
                                               .build();

        JsonValue json = JsonMap.newBuilder()
                                .put("array_contains", predicate)
                                .build()
                                .toJsonValue();

        ValueMatcher matcher = ValueMatcher.newArrayContainsMatcher(predicate);
        assertEquals(matcher, ValueMatcher.parse(json));

        json = JsonMap.newBuilder()
                      .put("array_contains", predicate)
                      .put("index", 50)
                      .build()
                      .toJsonValue();

        matcher = ValueMatcher.newArrayContainsMatcher(predicate, 50);
        assertEquals(matcher, ValueMatcher.parse(json));
    }

    @Test
    public void testStringBeginsMatcher() {
        ValueMatcher matcher = new StringBeginsMatcher(JsonValue.wrap("foo"));
        assertTrue(matcher.apply(JsonValue.wrap("foobar")));
        assertTrue(matcher.apply(JsonValue.wrap("FOOBAR"), true));
        assertFalse(matcher.apply(JsonValue.wrap("FOOBAR")));
        assertFalse(matcher.apply(JsonValue.wrap("barfoo")));
    }

    @Test
    public void testStringEndsMatcher() {
        ValueMatcher matcher = new StringEndsMatcher(JsonValue.wrap("bar"));
        assertTrue(matcher.apply(JsonValue.wrap("foobar")));
        assertTrue(matcher.apply(JsonValue.wrap("FOOBAR"), true));
        assertFalse(matcher.apply(JsonValue.wrap("FOOBAR")));
        assertFalse(matcher.apply(JsonValue.wrap("barfoo")));
    }

    @Test
    public void testStringContainsMatcher() {
        ValueMatcher matcher = new StringContainsMatcher(JsonValue.wrap("oob"));
        assertTrue(matcher.apply(JsonValue.wrap("foobar")));
        assertTrue(matcher.apply(JsonValue.wrap("FOOBAR"), true));
        assertFalse(matcher.apply(JsonValue.wrap("FOOBAR")));
        assertFalse(matcher.apply(JsonValue.wrap("barfoo")));
    }

    @Test
    public void testStringContainsMatcherEdgeCase() {
        // this one fails if the implementation is
        // return containerComparableLowerCase.contains(containeeComparableLowerCase);
        ValueMatcher matcher = new StringContainsMatcher(JsonValue.wrap("iẞAR"));
        assertFalse(matcher.apply(JsonValue.wrap("fooİẞar")));
        assertTrue(matcher.apply(JsonValue.wrap("FOOİẞAR"), true));
    }

    @Test
    public void testStringEndsMatcherEdgeCase() {
        // this one fails if the implementation is
        // containerComparable.toLowerCase().endsWith(containeeComparable.toLowerCase());
        ValueMatcher matcher = new StringEndsMatcher(JsonValue.wrap("i"));
        assertFalse(matcher.apply(JsonValue.wrap("fooİ")));
        assertTrue(matcher.apply(JsonValue.wrap("fooİ"), true));
    }

    @Test
    public void testStringBeginsMatcherEdgeCase() {
        // this one fails if the implementation is
        // containerComparable.toLowerCase().startsWidth(containeeComparable.toLowerCase());
        ValueMatcher matcher = new StringBeginsMatcher(JsonValue.wrap("i"));
        assertFalse(matcher.apply(JsonValue.wrap("İfoo")));
        assertTrue(matcher.apply(JsonValue.wrap("İfoo"), true));
    }

    @Test
    public void testStringEqualsMatcherEdgeCase() {
        ValueMatcher matcher = new ExactValueMatcher(JsonValue.wrap("i"));
        assertFalse(matcher.apply(JsonValue.wrap("İ")));
        assertTrue(matcher.apply(JsonValue.wrap("İ"), true));
    }
}
