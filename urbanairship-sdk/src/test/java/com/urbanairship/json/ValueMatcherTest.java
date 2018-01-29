/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.json;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ValueMatcher} tests.
 */
public class ValueMatcherTest extends BaseTestCase {

    @Test
    public void testValueMatcher() {
        JsonValue value = JsonValue.wrap(5);
        ValueMatcher matcher = ValueMatcher.newValueMatcher(value);

        assertTrue(matcher.apply(value));

        value = JsonValue.wrap(6);
        assertFalse(matcher.apply(value));

        value = JsonValue.wrap(true);
        matcher = ValueMatcher.newValueMatcher(value);

        assertTrue(matcher.apply(value));

        value = JsonValue.wrap(false);
        assertFalse(matcher.apply(value));

        value = JsonValue.wrap("test");
        matcher = ValueMatcher.newValueMatcher(value);

        assertTrue(matcher.apply(value));

        value = JsonValue.wrap("wrong");
        assertFalse(matcher.apply(value));

        value = JsonValue.wrap(5.0);
        matcher = ValueMatcher.newValueMatcher(value);

        assertTrue(matcher.apply(value));

        value = JsonValue.wrap(6.0);
        assertFalse(matcher.apply(value));
    }

    @Test
    public void testNumberRangeMatcher() {
        Double min = 5.0;
        Double max = null;
        JsonValue value = JsonValue.wrap(6.0);
        ValueMatcher matcher = ValueMatcher.newNumberRangeMatcher(min, max);
        assertTrue(matcher.apply(value));

        value = JsonValue.wrap(4.0);
        assertFalse(matcher.apply(value));

        min = 5.0;
        max = 7.0;
        value = JsonValue.wrap(6.0);
        matcher = ValueMatcher.newNumberRangeMatcher(min, max);
        assertTrue(matcher.apply(value));

        value = JsonValue.wrap(4.0);
        assertFalse(matcher.apply(value));

        min = null;
        max = 7.0;
        value = JsonValue.wrap(6.0);
        matcher = ValueMatcher.newNumberRangeMatcher(min, max);
        assertTrue(matcher.apply(value));

        value = JsonValue.wrap(8.0);
        assertFalse(matcher.apply(value));
    }

    @Test
    public void testAbsenceMatcher() {
        JsonValue value = JsonValue.NULL;
        ValueMatcher matcher = ValueMatcher.newIsAbsentMatcher();
        assertTrue(matcher.apply(value));

        matcher = ValueMatcher.newIsPresentMatcher();
        assertFalse(matcher.apply(value));

        value = JsonValue.wrap("value");
        assertTrue(matcher.apply(value));

        matcher = ValueMatcher.newIsAbsentMatcher();
        assertFalse(matcher.apply(value));
    }

    @Test
    public void testVersionMatcher() {
        ValueMatcher matcher = ValueMatcher.newVersionMatcher("1.+");

        assertTrue(matcher.apply(JsonValue.wrap("1.2.4")));
        assertFalse(matcher.apply(JsonValue.wrap("2.0.0")));
    }

    @Test
    public void testArrayContainsMatcher() {
        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt("bingo")))
                                                                      .build())
                                               .build();

        JsonValue elements = JsonValue.wrapOpt(Arrays.asList("that's", "a", "bingo"));


        assertTrue(ValueMatcher.newArrayContainsMatcher(predicate).apply(elements));
        assertTrue(ValueMatcher.newArrayContainsMatcher(predicate, 2).apply(elements));

        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 1).apply(elements));
        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 0).apply(elements));
        assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, -1).apply(elements));
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
                      .put("version", "1.2.4")
                      .build()
                      .toJsonValue();

        matcher = ValueMatcher.newVersionMatcher("1.2.4");
        assertEquals(matcher, ValueMatcher.parse(json));
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
}
