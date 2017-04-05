/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.json;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

public class JsonPredicateTest extends BaseTestCase {

    JsonSerializable catJson;
    JsonMatcher legMatcher;
    JsonMatcher weightMatcher;
    JsonMatcher nameMatcher;
    JsonMatcher sleepMatcher;

    @Before
    public void setup() {
        JsonMap schedule = JsonMap.newBuilder()
                                  .put("sleep", "all day")
                                  .build();

        catJson = JsonMap.newBuilder()
                         .put("legs", 4)
                         .put("weight", 9.8)
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        legMatcher = JsonMatcher.newBuilder()
                                .setKey("legs")
                                .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(4)))
                                .build();

        weightMatcher = JsonMatcher.newBuilder()
                                   .setKey("weight")
                                   .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(9.8)))
                                   .build();

        nameMatcher = JsonMatcher.newBuilder()
                                 .setKey("name")
                                 .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("mittens")))
                                 .build();
        sleepMatcher = JsonMatcher.newBuilder()
                                  .setScope("schedule")
                                  .setKey("sleep")
                                  .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("all day")))
                                  .build();
    }

    @Test
    public void testAnd() {

        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                               .addMatcher(legMatcher)
                                               .addMatcher(weightMatcher)
                                               .addMatcher(nameMatcher)
                                               .addMatcher(sleepMatcher)
                                               .build();

        assertTrue(predicate.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("legs", 4)
                         .build();

        assertFalse(predicate.apply(catJson));
    }

    @Test
    public void testOr() {
        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .setPredicateType(JsonPredicate.OR_PREDICATE_TYPE)
                                               .addMatcher(legMatcher)
                                               .addMatcher(weightMatcher)
                                               .addMatcher(nameMatcher)
                                               .addMatcher(sleepMatcher)
                                               .build();

        catJson = JsonMap.newBuilder()
                         .put("legs", 4)
                         .build();

        assertTrue(predicate.apply(catJson));
    }

    @Test
    public void testNot() {
        JsonPredicate predicate = JsonPredicate.newBuilder()
                                               .setPredicateType(JsonPredicate.NOT_PREDICATE_TYPE)
                                               .addMatcher(JsonMatcher.newBuilder()
                                                                      .setKey("legs")
                                                                      .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(5)))
                                                                      .build())
                                               .build();
        assertTrue(predicate.apply(catJson));

        predicate = JsonPredicate.newBuilder()
                                 .setPredicateType(JsonPredicate.NOT_PREDICATE_TYPE)
                                 .addMatcher(legMatcher)
                                 .build();

        assertFalse(predicate.apply(catJson));
    }

    @Test
    public void testAndOr() {
        JsonPredicate or = JsonPredicate.newBuilder()
                                        .setPredicateType(JsonPredicate.OR_PREDICATE_TYPE)
                                        .addMatcher(legMatcher)
                                        .addMatcher(weightMatcher)
                                        .build();

        JsonPredicate and = JsonPredicate.newBuilder()
                                         .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                         .addMatcher(nameMatcher)
                                         .addMatcher(sleepMatcher)
                                         .addPredicate(or)
                                         .build();

        JsonMap schedule = JsonMap.newBuilder()
                                  .put("sleep", "all day")
                                  .build();

        catJson = JsonMap.newBuilder()
                         .put("weight", 9.8)
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        assertTrue(and.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("weight", 8.8)
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        assertFalse(and.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("legs", 4)
                         .put("schedule", schedule)
                         .build();

        assertFalse(and.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        assertFalse(and.apply(catJson));
    }

    @Test
    public void testOrAnd() {
        JsonPredicate and = JsonPredicate.newBuilder()
                                         .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                         .addMatcher(nameMatcher)
                                         .addMatcher(sleepMatcher)
                                         .build();

        JsonPredicate or = JsonPredicate.newBuilder()
                                        .setPredicateType(JsonPredicate.OR_PREDICATE_TYPE)
                                        .addMatcher(legMatcher)
                                        .addMatcher(weightMatcher)
                                        .addPredicate(and)
                                        .build();

        JsonMap schedule = JsonMap.newBuilder()
                                  .put("sleep", "all day")
                                  .build();

        assertTrue(or.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        assertTrue(or.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("weight", 9.8)
                         .build();

        assertTrue(or.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("schedule", schedule)
                         .build();

        assertFalse(or.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("name", "paws")
                         .put("legs", 6)
                         .build();

        assertFalse(or.apply(catJson));
    }

    @Test
    public void testAndOrNot() {
        JsonPredicate or = JsonPredicate.newBuilder()
                                        .setPredicateType(JsonPredicate.OR_PREDICATE_TYPE)
                                        .addMatcher(legMatcher)
                                        .addMatcher(weightMatcher)
                                        .build();

        JsonPredicate not = JsonPredicate.newBuilder()
                .setPredicateType(JsonPredicate.NOT_PREDICATE_TYPE)
                .addMatcher(JsonMatcher.newBuilder()
                           .setKey("tail")
                           .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                           .build())
                .build();

        JsonPredicate and = JsonPredicate.newBuilder()
                                         .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                         .addMatcher(nameMatcher)
                                         .addMatcher(sleepMatcher)
                                         .addPredicate(or)
                                         .addPredicate(not)
                                         .build();

        JsonMap schedule = JsonMap.newBuilder()
                                  .put("sleep", "all day")
                                  .build();

        catJson = JsonMap.newBuilder()
                         .put("legs", 4)
                         .put("weight", 9.8)
                         .put("name", "mittens")
                         .put("tail", 1.0)
                         .put("schedule", schedule)
                         .build();

        assertFalse(and.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("legs", 4)
                         .put("weight", 9.8)
                         .put("name", "mittens")
                         .put("tail", 3.0)
                         .put("schedule", schedule)
                         .build();

        assertTrue(and.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("weight", 9.8)
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        assertTrue(and.apply(catJson));
    }

    @Test
    public void testNestedNot() {

        JsonPredicate andOr = JsonPredicate.newBuilder()
                                           .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                           .addMatcher(JsonMatcher.newBuilder()
                                                                  .setKey("head")
                                                                  .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                                                                  .build())
                                           .addMatcher(JsonMatcher.newBuilder()
                                                                  .setKey("ears")
                                                                  .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(2)))
                                                                  .build())
                                           .build();

        JsonPredicate notOr = JsonPredicate.newBuilder()
                                           .setPredicateType(JsonPredicate.OR_PREDICATE_TYPE)
                                           .addMatcher(JsonMatcher.newBuilder()
                                                                  .setKey("tail")
                                                                  .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                                                                  .build())
                                           .addPredicate(andOr)
                                           .build();


        JsonPredicate not = JsonPredicate.newBuilder()
                                         .setPredicateType(JsonPredicate.NOT_PREDICATE_TYPE)
                                         .addPredicate(notOr)
                                         .build();

        JsonPredicate or = JsonPredicate.newBuilder()
                                        .setPredicateType(JsonPredicate.OR_PREDICATE_TYPE)
                                        .addMatcher(legMatcher)
                                        .addMatcher(weightMatcher)
                                        .addPredicate(not)
                                        .build();

        JsonPredicate and = JsonPredicate.newBuilder()
                                         .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                         .addMatcher(nameMatcher)
                                         .addMatcher(sleepMatcher)
                                         .addPredicate(or)
                                         .build();

        JsonMap schedule = JsonMap.newBuilder()
                                  .put("sleep", "all day")
                                  .build();

        catJson = JsonMap.newBuilder()
                         .put("weight", 9.8)
                         .put("head", 1.0)
                         .put("ears", 2)
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        assertTrue(and.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("ears", 2)
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        assertTrue(and.apply(catJson));

        catJson = JsonMap.newBuilder()
                         .put("head", 1.0)
                         .put("ears", 2)
                         .put("name", "mittens")
                         .put("schedule", schedule)
                         .build();

        assertFalse(and.apply(catJson));
    }

    @Test
    public void testParse() throws JsonException {
        JsonPredicate andOr = JsonPredicate.newBuilder()
                                           .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                           .addMatcher(JsonMatcher.newBuilder()
                                                                  .setKey("head")
                                                                  .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                                                                  .build())
                                           .addMatcher(JsonMatcher.newBuilder()
                                                                  .setKey("ears")
                                                                  .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(2)))
                                                                  .build())
                                           .build();

        JsonPredicate notOr = JsonPredicate.newBuilder()
                                           .setPredicateType(JsonPredicate.OR_PREDICATE_TYPE)
                                           .addMatcher(JsonMatcher.newBuilder()
                                                                  .setKey("tail")
                                                                  .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                                                                  .build())
                                           .addPredicate(andOr)
                                           .build();


        JsonPredicate not = JsonPredicate.newBuilder()
                                         .setPredicateType(JsonPredicate.NOT_PREDICATE_TYPE)
                                         .addPredicate(notOr)
                                         .build();

        JsonPredicate or = JsonPredicate.newBuilder()
                                        .setPredicateType(JsonPredicate.OR_PREDICATE_TYPE)
                                        .addMatcher(legMatcher)
                                        .addMatcher(weightMatcher)
                                        .addPredicate(not)
                                        .build();

        JsonPredicate predicate = JsonPredicate.newBuilder()
                                         .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                         .addMatcher(nameMatcher)
                                         .addMatcher(sleepMatcher)
                                         .addPredicate(or)
                                         .build();

        assertEquals(predicate, JsonPredicate.parse(predicate.toJsonValue()));
    }

    /**
     * Test parsing a JsonMatcher directly produces a JsonPredicate.
     */
    @Test
    public void testParseJsonMatcher() throws JsonException {
        JsonMatcher matcher = JsonMatcher.newBuilder()
                .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("mittens")))
                .build();

        JsonPredicate predicate = JsonPredicate.parse(matcher.toJsonValue());
        assertNotNull(predicate);
        assertTrue(predicate.apply(JsonValue.wrap("mittens")));
    }

    /**
     * Test parsing an empty JsonMap throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseEmptyMap() throws JsonException {
        JsonPredicate.parse(JsonMap.EMPTY_MAP.toJsonValue());
    }

    /**
     * Test parsing an invalid JsonValue throws a JsonException.
     */
    @Test(expected = JsonException.class)
    public void testParseInvalidJson() throws JsonException {
        JsonPredicate.parse(JsonValue.wrap("not valid"));
    }
}