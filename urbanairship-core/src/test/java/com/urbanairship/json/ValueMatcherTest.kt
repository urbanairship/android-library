/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.matchers.ExactValueMatcher
import com.urbanairship.json.matchers.StringBeginsMatcher
import com.urbanairship.json.matchers.StringContainsMatcher
import com.urbanairship.json.matchers.StringEndsMatcher
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ValueMatcher] tests.
 */
@RunWith(AndroidJUnit4::class)
class ValueMatcherTest {

    @Test
    fun testValueMatcher() {
        var value = JsonValue.wrap(5)
        var matcher = ValueMatcher.newValueMatcher(value)

        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap(6)
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))

        value = JsonValue.wrap(true)
        matcher = ValueMatcher.newValueMatcher(value)

        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap(false)
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))

        value = JsonValue.wrap("test")
        matcher = ValueMatcher.newValueMatcher(value)
        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap("TEST")
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap("wrong")
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))

        value = JsonValue.wrap(5.0)
        matcher = ValueMatcher.newValueMatcher(value)
        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap(6.0)
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))

        value = JsonValue.wrap(arrayOf("first-value", "second-value", null))
        Assert.assertNotNull(value)
        matcher = ValueMatcher.newValueMatcher(value)
        Assert.assertNotNull(matcher)

        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap(arrayOf("FIRST-Value", "Second-Value", null))
        Assert.assertNotNull(value)

        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        var map = mapOf(
            "null-key" to null,
            "some-key" to "some-value",
            "another-key" to "another-value"
        )
        value = JsonValue.wrap(map)
        Assert.assertNotNull(value)
        matcher = ValueMatcher.newValueMatcher(value)
        Assert.assertNotNull(matcher)

        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        map = mapOf(
            "null-key" to null,
            "some-key" to "Some-Value",
            "another-key" to "ANOTHER-VALUE"
        )

        value = JsonValue.wrap(map)
        Assert.assertNotNull(value)

        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))
    }

    @Test
    fun testNumberRangeMatcher() {
        var min: Double? = 5.0
        var max: Double? = null
        var value = JsonValue.wrap(6.0)
        var matcher = ValueMatcher.newNumberRangeMatcher(min, max)
        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap(4.0)
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))

        min = 5.0
        max = 7.0
        value = JsonValue.wrap(6.0)
        matcher = ValueMatcher.newNumberRangeMatcher(min, max)
        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap(4.0)
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))

        min = null
        max = 7.0
        value = JsonValue.wrap(6.0)
        matcher = ValueMatcher.newNumberRangeMatcher(min, max)
        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        value = JsonValue.wrap(8.0)
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))
    }

    @Test
    fun testAbsenceMatcher() {
        var value = JsonValue.NULL
        var matcher = ValueMatcher.newIsAbsentMatcher()
        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        matcher = ValueMatcher.newIsPresentMatcher()
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))

        value = JsonValue.wrap("value")
        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(matcher.apply(value, false))
        Assert.assertTrue(matcher.apply(value, true))

        matcher = ValueMatcher.newIsAbsentMatcher()
        Assert.assertFalse(matcher.apply(value))
        Assert.assertFalse(matcher.apply(value, false))
        Assert.assertFalse(matcher.apply(value, true))
    }

    @Test
    fun testVersionMatcher() {
        val matcher = ValueMatcher.newVersionMatcher("1.+")

        Assert.assertTrue(matcher.apply(JsonValue.wrap("1.2.4")))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("1.2.4"), false))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("1.2.4"), true))

        Assert.assertFalse(matcher.apply(JsonValue.wrap("2.0.0")))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("2.0.0"), false))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("2.0.0"), true))
    }

    @Test
    fun testArrayContainsMatcher() {
        val predicate = JsonPredicate.newBuilder()
            .addMatcher(
                matcher = JsonMatcher.newBuilder()
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt("bingo")))
                    .build()
            )
            .build()

        val ignoreCasePredicate = JsonPredicate.newBuilder()
            .addMatcher(
                matcher = JsonMatcher.newBuilder()
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt("bingo")))
                    .setIgnoreCase(true)
                    .build()
            )
            .build()

        var elements = JsonValue.wrapOpt(listOf("that's", "a", "bingo"))

        Assert.assertTrue(ValueMatcher.newArrayContainsMatcher(predicate).apply(elements))
        Assert.assertTrue(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate).apply(elements))

        Assert.assertTrue(ValueMatcher.newArrayContainsMatcher(predicate, 2).apply(elements))
        Assert.assertTrue(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 2).apply(elements))

        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 1).apply(elements))
        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 1).apply(elements))

        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 0).apply(elements))
        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 0).apply(elements))

        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, -1).apply(elements))
        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, -1).apply(elements))

        elements = JsonValue.wrapOpt(listOf("that's", "a", "BINGO"))

        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(predicate).apply(elements))
        Assert.assertTrue(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate).apply(elements))

        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 2).apply(elements))
        Assert.assertTrue(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 2).apply(elements))

        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(predicate, 1).apply(elements))
        Assert.assertFalse(ValueMatcher.newArrayContainsMatcher(ignoreCasePredicate, 1).apply(elements))
    }

    @Test
    fun testArrayLengthMatcher() {
        val predicate = JsonPredicate.newBuilder()
            .addMatcher(
                matcher = JsonMatcher.newBuilder()
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt(3)))
                    .build()
            )
            .build()

        val wrongPredicate = JsonPredicate.newBuilder()
            .addMatcher(
                matcher = JsonMatcher.newBuilder()
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt(4)))
                    .build()
            )
            .build()

        val elements = JsonValue.wrapOpt(listOf("toto", "titi", "tite"))
        val arrayString = JsonValue.parseString("[\"android\", \"ios\", \"unknown\"]")

        Assert.assertTrue(ValueMatcher.newArrayLengthMatcher(predicate).apply(elements))
        Assert.assertFalse(ValueMatcher.newArrayLengthMatcher(wrongPredicate).apply(elements))
        Assert.assertTrue(ValueMatcher.newArrayLengthMatcher(predicate).apply(arrayString))
        Assert.assertFalse(ValueMatcher.newArrayLengthMatcher(wrongPredicate).apply(arrayString))
    }

    @Test
    fun testParse() {
        val min = 5.0
        val max = 7.0
        var json = jsonMapOf(
            "at_least" to min,
            "at_most" to max
        ).toJsonValue()

        var matcher = ValueMatcher.newNumberRangeMatcher(min, max)
        Assert.assertEquals(matcher, ValueMatcher.parse(json))

        json = jsonMapOf("is_present" to true).toJsonValue()
        matcher = ValueMatcher.newIsPresentMatcher()
        Assert.assertEquals(matcher, ValueMatcher.parse(json))

        json = jsonMapOf("equals" to "string").toJsonValue()
        matcher = ValueMatcher.newValueMatcher(JsonValue.wrap("string"))
        Assert.assertEquals(matcher, ValueMatcher.parse(json))

        json = jsonMapOf(
            "equals" to "string",
            "ignore_case" to true
        ).toJsonValue()
        matcher = ValueMatcher.newValueMatcher(JsonValue.wrap("string"))
        Assert.assertEquals(matcher, ValueMatcher.parse(json))

        json = jsonMapOf("version_matches" to "1.2.4").toJsonValue()
        matcher = ValueMatcher.newVersionMatcher("1.2.4")
        Assert.assertEquals(matcher, ValueMatcher.parse(json))

        json = jsonMapOf("version" to "1.2.4").toJsonValue()
        matcher = ValueMatcher.newVersionMatcher("1.2.4")
        Assert.assertEquals(matcher, ValueMatcher.parse(json))

        json = jsonMapOf(StringBeginsMatcher.STRING_BEGINS to "hello").toJsonValue()
        Assert.assertEquals(StringBeginsMatcher(JsonValue.wrap("hello")), ValueMatcher.parse(json))

        json = jsonMapOf(StringEndsMatcher.STRING_ENDS to "goodbye").toJsonValue()
        Assert.assertEquals(StringEndsMatcher(JsonValue.wrap("goodbye")), ValueMatcher.parse(json))

        json = jsonMapOf(StringContainsMatcher.STRING_CONTAINS to "world").toJsonValue()
        Assert.assertEquals(StringContainsMatcher(JsonValue.wrap("world")), ValueMatcher.parse(json))
    }

    @Test
    fun testParseArrayContainsMatcher() {
        val predicate = JsonPredicate.newBuilder()
            .addMatcher(
                matcher = JsonMatcher.newBuilder()
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrapOpt("bingo")))
                    .build()
            )
            .build()

        var json = jsonMapOf("array_contains" to predicate).toJsonValue()
        var matcher = ValueMatcher.newArrayContainsMatcher(predicate)
        Assert.assertEquals(matcher, ValueMatcher.parse(json))

        json = jsonMapOf(
            "array_contains" to predicate,
            "index" to 50
        ).toJsonValue()
        matcher = ValueMatcher.newArrayContainsMatcher(predicate, 50)
        Assert.assertEquals(matcher, ValueMatcher.parse(json))
    }

    @Test
    fun testStringBeginsMatcher() {
        val matcher = StringBeginsMatcher(JsonValue.wrap("foo"))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("foobar")))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("FOOBAR"), true))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("FOOBAR")))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("barfoo")))
    }

    @Test
    fun testStringEndsMatcher() {
        val matcher = StringEndsMatcher(JsonValue.wrap("bar"))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("foobar")))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("FOOBAR"), true))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("FOOBAR")))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("barfoo")))
    }

    @Test
    fun testStringContainsMatcher() {
        val matcher = StringContainsMatcher(JsonValue.wrap("oob"))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("foobar")))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("FOOBAR"), true))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("FOOBAR")))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("barfoo")))
    }

    @Test
    fun testStringContainsMatcherEdgeCase() {
        // this one fails if the implementation is
        // return containerComparableLowerCase.contains(containeeComparableLowerCase);
        val matcher = StringContainsMatcher(JsonValue.wrap("iẞAR"))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("fooİẞar")))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("FOOİẞAR"), true))
    }

    @Test
    fun testStringEndsMatcherEdgeCase() {
        // this one fails if the implementation is
        // containerComparable.toLowerCase().endsWith(containeeComparable.toLowerCase());
        val matcher = StringEndsMatcher(JsonValue.wrap("i"))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("fooİ")))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("fooİ"), true))
    }

    @Test
    fun testStringBeginsMatcherEdgeCase() {
        // this one fails if the implementation is
        // containerComparable.toLowerCase().startsWidth(containeeComparable.toLowerCase());
        val matcher = StringBeginsMatcher(JsonValue.wrap("i"))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("İfoo")))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("İfoo"), true))
    }

    @Test
    fun testStringEqualsMatcherEdgeCase() {
        val matcher = ExactValueMatcher(JsonValue.wrap("i"))
        Assert.assertFalse(matcher.apply(JsonValue.wrap("İ")))
        Assert.assertTrue(matcher.apply(JsonValue.wrap("İ"), true))
    }
}
