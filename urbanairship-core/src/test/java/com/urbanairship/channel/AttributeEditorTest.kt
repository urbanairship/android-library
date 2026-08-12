/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import java.time.Instant
import java.util.Date
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Attributes editor tests.
 */
@RunWith(AndroidJUnit4::class)
public class AttributeEditorTest {

    private val clock = TestClock()
    private val editor: TestAttributeEditor = TestAttributeEditor(clock)

    @Test(expected = NumberFormatException::class)
    @Throws(NumberFormatException::class)
    public fun testDoubleNaN() {
        editor.setAttribute("key", Double.NaN)
    }

    @Test(expected = NumberFormatException::class)
    @Throws(NumberFormatException::class)
    public fun testDoublePositiveInfinity() {
        editor.setAttribute("key", Double.POSITIVE_INFINITY)
    }

    @Test(expected = NumberFormatException::class)
    @Throws(NumberFormatException::class)
    public fun testDoubleNegativeInfinity() {
        editor.setAttribute("key", Double.NEGATIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    @Throws(NumberFormatException::class)
    public fun testInvalidAttribute() {
        editor.removeAttribute(attribute = "Cool#")
    }

    @Test(expected = IllegalArgumentException::class)
    @Throws(NumberFormatException::class)
    public fun testInvalidInstanceId() {
        editor.removeAttribute(attribute = "Cool", instanceId = "foo#")
    }

    @Test(expected = NumberFormatException::class)
    @Throws(NumberFormatException::class)
    public fun testFloatNaN() {
        editor.setAttribute("key", Float.NaN)
    }

    @Test(expected = NumberFormatException::class)
    @Throws(NumberFormatException::class)
    public fun testFloatPositiveInfinity() {
        editor.setAttribute("key", Float.POSITIVE_INFINITY)
    }

    @Test(expected = NumberFormatException::class)
    @Throws(NumberFormatException::class)
    public fun testFloatNegativeInfinity() {
        editor.setAttribute("key", Float.NEGATIVE_INFINITY)
    }

    @Test
    public fun testAttributes() {
        clock.currentTime = Instant.ofEpochMilli(10000)
        editor.setAttribute("string", "expected_value").setAttribute("long", 100L)
            .setAttribute("double", 30.13).setAttribute("float", 131.2003f)
            .setAttribute("date", Date(1561803000000L)).removeAttribute("remove").apply()

        val expected: MutableList<AttributeMutation> = ArrayList()
        expected.add(
            AttributeMutation.newSetAttributeMutation(
                "string", JsonValue.wrapOpt("expected_value"), Instant.ofEpochMilli(10000)
            )
        )
        expected.add(
            AttributeMutation.newSetAttributeMutation(
                "long", JsonValue.wrapOpt(100L), Instant.ofEpochMilli(10000)
            )
        )
        expected.add(
            AttributeMutation.newSetAttributeMutation(
                "double", JsonValue.wrapOpt(30.13), Instant.ofEpochMilli(10000)
            )
        )
        expected.add(
            AttributeMutation.newSetAttributeMutation(
                "float", JsonValue.wrapOpt(131.2003f), Instant.ofEpochMilli(10000)
            )
        )
        expected.add(
            AttributeMutation.newSetAttributeMutation(
                "date", JsonValue.wrapOpt(DateUtils.createIso8601TimeStamp(Instant.ofEpochMilli(1561803000000))), Instant.ofEpochMilli(10000)
            )
        )
        expected.add(AttributeMutation.newRemoveAttributeMutation("remove", Instant.ofEpochMilli(10000)))

        Assert.assertEquals(expected, editor.appliedMutations)
    }

    @Test(expected = IllegalArgumentException::class)
    @Throws(IllegalArgumentException::class)
    public fun testJsonAttributeReservedKey() {
        editor.setAttribute(
            attribute = "cool", instanceId = "story", json = jsonMapOf(
                "key" to "value", "exp" to "123"
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    @Throws(IllegalArgumentException::class)
    public fun testJsonAttributeEmptyMap() {
        editor.setAttribute(
            attribute = "cool", instanceId = "story", json = jsonMapOf()
        )
    }

    @Test
    public fun testJsonAttribute() {
        clock.currentTime = Instant.ofEpochMilli(10000)

        editor.setAttribute(
            attribute = "foo", instanceId = "bar", json = jsonMapOf("key" to "value")
        )
        editor.removeAttribute(attribute = "foo", instanceId = "baz")

        editor.setAttribute(
            attribute = "foo", instanceId = "qux", json = jsonMapOf("key" to "value"), expiration = Date(20000)
        )
        editor.apply()

        val expected = listOf(
            AttributeMutation.newSetAttributeMutation("foo#bar", jsonMapOf(
                "key" to "value"
            ).toJsonValue(), Instant.ofEpochMilli(10000)),
            AttributeMutation.newRemoveAttributeMutation("foo#baz", Instant.ofEpochMilli(10000)),
            AttributeMutation.newSetAttributeMutation("foo#qux", jsonMapOf(
                "key" to "value",
                "exp" to 20
            ).toJsonValue(), Instant.ofEpochMilli(10000)),
        )

        Assert.assertEquals(expected, editor.appliedMutations)

    }

    private class TestAttributeEditor(clock: Clock) : AttributeEditor(clock) {
        var appliedMutations: MutableList<AttributeMutation> = mutableListOf()

        override fun onApply(collapsedMutations: List<AttributeMutation>) {
            this.appliedMutations.addAll(collapsedMutations)
        }
    }
}
