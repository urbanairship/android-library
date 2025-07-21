/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.util.VersionUtils
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class JsonPredicateTest {

    private var catJson = jsonMapOf(
        "legs" to 4,
        "weight" to 9.8,
        "name" to "mittens",
        "schedule" to jsonMapOf("sleep" to "all day")
    )
    private val legMatcher = JsonMatcher.newBuilder()
        .setKey("legs")
        .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(4)))
        .build()

    private val weightMatcher = JsonMatcher.newBuilder()
        .setKey("weight")
        .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(9.8)))
        .build()

    private val nameMatcher = JsonMatcher.newBuilder()
        .setKey("name")
        .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("mittens")))
        .build()

    private val sleepMatcher = JsonMatcher.newBuilder()
        .setScope("schedule")
        .setKey("sleep")
        .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("all day")))
        .build()

    @Test
    fun testAnd() {
        val predicate = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(legMatcher)
            .addMatcher(weightMatcher)
            .addMatcher(nameMatcher)
            .addMatcher(sleepMatcher)
            .build()

        Assert.assertTrue(predicate.apply(catJson))

        catJson = jsonMapOf("legs" to 4)
        Assert.assertFalse(predicate.apply(catJson))
    }

    @Test
    fun testJSONPredicateArrayLength() {
        // This JSON is flawed as you cant have an array of matchers for value. However it shows
        // order of matcher parsing and its the same test on web, so we are using it.
        val json = """{
          "value": {
            "array_contains": {
              "value": {
                "equals": 2
              }
            },
            "array_length": {
              "value": {
                "equals": 1
              }
            }
          }
        }"""

        val predicate = JsonPredicate.parse(JsonValue.parseString(json))
        Assert.assertTrue(predicate.apply(JsonValue.wrap(listOf(2))))
        Assert.assertFalse(predicate.apply(JsonValue.wrap(mutableListOf(0, 1, 2))))
    }

    @Test
    fun testOr() {
        val predicate = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.OR)
            .addMatcher(legMatcher)
            .addMatcher(weightMatcher)
            .addMatcher(nameMatcher)
            .addMatcher(sleepMatcher)
            .build()

        catJson = jsonMapOf("legs" to 4)
        Assert.assertTrue(predicate.apply(catJson))
    }

    @Test
    fun testNot() {
        var predicate = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.NOT)
            .addMatcher(
                matcher =  JsonMatcher.newBuilder()
                    .setKey("legs")
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(5)))
                    .build()
            )
            .build()
        Assert.assertTrue(predicate.apply(catJson))

        predicate = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.NOT)
            .addMatcher(legMatcher)
            .build()

        Assert.assertFalse(predicate.apply(catJson))
    }

    @Test
    fun testAndOr() {
        val or = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.OR)
            .addMatcher(legMatcher)
            .addMatcher(weightMatcher)
            .build()

        val and = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(nameMatcher)
            .addMatcher(sleepMatcher)
            .addPredicate(or)
            .build()

        val schedule = jsonMapOf("sleep" to "all day")

        catJson = jsonMapOf("weight" to 9.8, "name" to "mittens", "schedule" to schedule)
        Assert.assertTrue(and.apply(catJson))

        catJson = jsonMapOf("weight" to 8.8, "name" to "mittens", "schedule" to schedule)
        Assert.assertFalse(and.apply(catJson))

        catJson = jsonMapOf("legs" to 4, "schedule" to schedule)
        Assert.assertFalse(and.apply(catJson))

        catJson = jsonMapOf("name" to "mittens", "schedule" to schedule)
        Assert.assertFalse(and.apply(catJson))
    }

    @Test
    fun testOrAnd() {
        val and = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(nameMatcher)
            .addMatcher(sleepMatcher)
            .build()

        val or = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.OR)
            .addMatcher(legMatcher)
            .addMatcher(weightMatcher)
            .addPredicate(and)
            .build()

        val schedule = jsonMapOf("sleep" to "all day")
        Assert.assertTrue(or.apply(catJson))

        catJson = jsonMapOf("name" to "mittens", "schedule" to schedule)
        Assert.assertTrue(or.apply(catJson))

        catJson = jsonMapOf("weight" to 9.8)
        Assert.assertTrue(or.apply(catJson))

        catJson = jsonMapOf("schedule" to schedule)
        Assert.assertFalse(or.apply(catJson))

        catJson = jsonMapOf("name" to "paws", "legs" to 6)
        Assert.assertFalse(or.apply(catJson))
    }

    @Test
    fun testAndOrNot() {
        val or = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.OR)
            .addMatcher(legMatcher)
            .addMatcher(weightMatcher)
            .build()

        val not = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.NOT)
            .addMatcher(
                matcher = JsonMatcher.newBuilder()
                    .setKey("tail")
                    .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                    .build()
            ).build()

        val and = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(nameMatcher)
            .addMatcher(sleepMatcher)
            .addPredicate(or)
            .addPredicate(not)
            .build()

        val schedule = jsonMapOf("sleep" to "all day")
        catJson = jsonMapOf(
            "legs" to 4,
            "weight" to 9.8,
            "name" to "mittens",
            "schedule" to schedule,
            "tail" to 1.0
        )
        Assert.assertFalse(and.apply(catJson))

        catJson = jsonMapOf(
            "legs" to 4,
            "weight" to 9.8,
            "name" to "mittens",
            "schedule" to schedule,
            "tail" to 3.0
        )
        Assert.assertTrue(and.apply(catJson))

        catJson = jsonMapOf(
            "weight" to 9.8,
            "name" to "mittens",
            "schedule" to schedule
        )
        Assert.assertTrue(and.apply(catJson))
    }

    @Test
    fun testNestedNot() {
        val andOr = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(
                matcher =  JsonMatcher.newBuilder()
                    .setKey("head")
                    .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                    .build()
            ).addMatcher(
                matcher =  JsonMatcher.newBuilder()
                    .setKey("ears")
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(2)))
                    .build()
            ).build()

        val notOr = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.OR)
            .addMatcher(
                matcher =  JsonMatcher.newBuilder()
                    .setKey("tail")
                    .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                    .build()
            )
            .addPredicate(andOr)
            .build()

        val not = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.NOT)
            .addPredicate(notOr)
            .build()

        val or = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.OR)
            .addMatcher(legMatcher)
            .addMatcher(weightMatcher)
            .addPredicate(not)
            .build()

        val and = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(nameMatcher)
            .addMatcher(sleepMatcher)
            .addPredicate(or)
            .build()

        val schedule = jsonMapOf("sleep" to "all day")
        catJson = jsonMapOf(
            "weight" to 9.8,
            "head" to 1.0,
            "ears" to 2,
            "name" to "mittens",
            "schedule" to schedule
        )
        Assert.assertTrue(and.apply(catJson))

        catJson = jsonMapOf(
            "ears" to 2,
            "name" to "mittens",
            "schedule" to schedule
        )
        Assert.assertTrue(and.apply(catJson))

        catJson = jsonMapOf(
            "head" to 1.0,
            "ears" to 2,
            "name" to "mittens",
            "schedule" to schedule
        )
        Assert.assertFalse(and.apply(catJson))
    }

    @Test
    fun testParse() {
        val andOr = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(
                matcher =  JsonMatcher.newBuilder()
                    .setKey("head")
                    .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                    .build()
            )
            .addMatcher(
                matcher =  JsonMatcher.newBuilder()
                    .setKey("ears")
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(2)))
                    .build()
            )
            .build()

        val notOr = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.OR)
            .addMatcher(
                matcher =  JsonMatcher.newBuilder()
                    .setKey("tail")
                    .setValueMatcher(ValueMatcher.newNumberRangeMatcher(0.0, 2.0))
                    .build()
            )
            .addPredicate(andOr)
            .build()

        val not = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.NOT)
            .addPredicate(notOr)
            .build()

        val or = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.OR)
            .addMatcher(legMatcher)
            .addMatcher(weightMatcher)
            .addPredicate(not)
            .build()

        val predicate = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(nameMatcher)
            .addMatcher(sleepMatcher)
            .addPredicate(or)
            .build()

        Assert.assertEquals(predicate, JsonPredicate.parse(predicate.toJsonValue()))
    }

    @Test
    fun testJSONPredicateNotNoArray() {
        val json = """
            {
              "not": {
                "scope": [
                  "foo"
                ],
                "value": {
                  "equals": "bar"
                }
              }
            }
        """.trimIndent()

        val stringMatcher = JsonMatcher.newBuilder()
            .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("bar")))
            .setScope("foo")
            .build()

        val expected = JsonPredicate.newBuilder()
            .setPredicateType(JsonPredicate.PredicateType.NOT)
            .addMatcher(stringMatcher)
            .build()

        val actual = JsonPredicate.parse(JsonValue.parseString(json))
        Assert.assertEquals(expected, actual)
    }

    /**
     * Test parsing a JsonMatcher directly produces a JsonPredicate.
     */
    @Test
    fun testParseJsonMatcher() {
        val matcher = JsonMatcher.newBuilder()
            .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("mittens")))
            .build()

        val predicate = JsonPredicate.parse(matcher.toJsonValue())
        Assert.assertNotNull(predicate)
        Assert.assertTrue(predicate.apply(JsonValue.wrap("mittens")))
    }

    /**
     * Test parsing an empty JsonMap throws a JsonException.
     */
    @Test(expected = JsonException::class)
    fun testParseEmptyMap() {
        JsonPredicate.parse(JsonMap.EMPTY_MAP.toJsonValue())
    }

    /**
     * Test parsing an invalid JsonValue throws a JsonException.
     */
    @Test(expected = JsonException::class)
    fun testParseInvalidJson() {
        JsonPredicate.parse(JsonValue.wrap("not valid"))
    }

    @Test
    fun testAndroidVersionTest() {
        val versionObject = VersionUtils.createVersionObject(2008200331)
        val predicate = JsonPredicate.newBuilder()
            .addMatcher(
                matcher =  JsonMatcher.newBuilder()
                    .setScope(listOf("android", "version"))
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt(2008200331)))
                    .build()
        ).build()

        Assert.assertTrue(predicate.apply(versionObject))
    }
}
