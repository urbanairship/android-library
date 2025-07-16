/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ScreenTrackingEventTest {

    /**
     * Test screen tracking event data formatting directly
     */
    @Test
    public fun testScreenTrackingEventData() {
        val event = ScreenTrackingEvent("test_screen", "previous_screen", 0, 1)

        val expected = JsonValue.parseString("""
            {
              "duration": "0.001",
              "entered_time": "0.000",
              "exited_time": "0.001",
              "previous_screen": "previous_screen",
              "screen": "test_screen"
            }
        """.trimIndent()
        )

        // Test isValid returns true for valid region event with expected data
        assertEquals(expected.map, event.getEventData(ConversionData(null, null, null)))
    }

    /**
     * Test setting screen tracking event screen
     */
    @Test
    public fun testSetScreen() {
        var screen = createFixedSizeString('a', 256)
        var event = ScreenTrackingEvent(screen, null, 0, 1)

        // Check that 256 character screen is invalid
        assertFalse(event.isValid())

        screen = createFixedSizeString('a', 255)
        event = ScreenTrackingEvent(screen, null, 0, 1)

        // Check that 255 character screen is valid
        assertTrue(event.isValid())

        screen = ""
        event = ScreenTrackingEvent(screen, null, 0, 1)

        // Check that 0 character screen is invalid
        assertFalse(event.isValid())
    }

    /**
     * Test setting screen tracking event duration
     */
    @Test
    public fun testSetDuration() {
        var event = ScreenTrackingEvent("test_screen", null, 0, 1)

        // Check that duration of 1 is valid
        assertTrue(event.isValid())

        event = ScreenTrackingEvent("test_screen", null, 0, 0)

        // Check that duration of 0 is valid
        assertTrue(event.isValid())

        event = ScreenTrackingEvent("test_screen", null, 0, -1)

        // Check that duration of -1 is invalid
        assertFalse(event.isValid())
    }

    /**
     * Helper method to create a fixed size string with a repeating character
     *
     * @param repeat The character to repeat.
     * @param length Length of the String.
     * @return A fixed size string.
     */
    private fun createFixedSizeString(repeat: Char, length: Int): String {
        val builder = StringBuilder(length)
        for (i in 0..<length) {
            builder.append(repeat)
        }

        return builder.toString()
    }
}
