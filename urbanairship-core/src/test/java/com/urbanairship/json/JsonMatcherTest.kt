/* Copyright Airship and Contributors */
package com.urbanairship.json

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue.Companion.wrap
import com.urbanairship.json.ValueMatcher.Companion.newValueMatcher
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class JsonMatcherTest {

    @Test
    public fun testMatcher() {
        val valueMatcher = newValueMatcher(wrap("value"))

        val builder = JsonMatcher.newBuilder()
            .setKey("key")
            .setValueMatcher(valueMatcher)

        var matcher = builder.build()
        var ignoreCaseMatcher = builder.setIgnoreCase(true).build()

        var value = jsonMapOf("key" to "value").toJsonValue()

        Assert.assertTrue(matcher.apply(value))
        Assert.assertTrue(ignoreCaseMatcher.apply(value))

        value = jsonMapOf("key" to "VALUE").toJsonValue()

        Assert.assertFalse(matcher.apply(value))
        Assert.assertTrue(ignoreCaseMatcher.apply(value))

        val builderProperties = JsonMatcher.newBuilder()
            .setKey("key")
            .setScope("properties")
            .setValueMatcher(valueMatcher)

        matcher = builderProperties.build()
        ignoreCaseMatcher = builderProperties.setIgnoreCase(true).build()

        Assert.assertFalse(ignoreCaseMatcher.apply(value))
        Assert.assertFalse(matcher.apply(value))

        value = jsonMapOf("properties" to jsonMapOf("key" to "value")).toJsonValue()

        Assert.assertTrue(matcher.apply(value))

        matcher = JsonMatcher.newBuilder().setValueMatcher(valueMatcher).build()
        value = wrap("value")

        Assert.assertTrue(matcher.apply(value))
    }

    @Test
    public fun testParse() {
        val valueMatcher = newValueMatcher(wrap("string"))

        val valueJson = jsonMapOf("equals" to "string").toJsonValue()
        val matcherJson = jsonMapOf(
            "key" to "key",
            "value" to valueJson,
            "scope" to jsonListOf("properties", "inner")
        ).toJsonValue()

        val matcher = JsonMatcher.newBuilder()
            .setKey("key")
            .setScope(listOf("properties", "inner"))
            .setValueMatcher(valueMatcher)
            .build()

        Assert.assertEquals(matcher, JsonMatcher.parse(matcherJson))

        val ignoreCaseMatcherJson = jsonMapOf(
            "key" to "key",
            "value" to valueJson,
            "scope" to jsonListOf("properties"),
            "ignore_case" to true
        ).toJsonValue()

        val ignoreCaseMatcher = JsonMatcher.newBuilder()
            .setKey("key")
            .setScope("properties")
            .setValueMatcher(valueMatcher)
            .setIgnoreCase(true)
            .build()

        Assert.assertEquals(ignoreCaseMatcher, JsonMatcher.parse(ignoreCaseMatcherJson))
    }

    /**
     * Test parsing an empty JsonMap throws a JsonException.
     */
    @Test(expected = JsonException::class)
    public fun testParseEmptyMap() {
        JsonMatcher.parse(JsonMap.EMPTY_MAP.toJsonValue())
    }

    /**
     * Test parsing an invalid JsonValue throws a JsonException.
     */
    @Test(expected = JsonException::class)
    public fun testParseInvalidJson() {
        JsonMatcher.parse(wrap("not valid"))
    }

    /**
     * Test parsing an invalid JsonValue throws a JsonException.
     */
    @Test(expected = JsonException::class)
    public fun testParseJsonMissingValueMatcher() {
        val json = jsonMapOf("key" to "value").toJsonValue()
        JsonMatcher.parse(json)
    }
}
