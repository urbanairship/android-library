/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.info

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class LinearLayoutInfoSpacingTest {

    private fun parse(spacing: String): LinearLayoutInfo {
        val json = """{ "type": "linear_layout", "direction": "vertical", $spacing "items": [] }"""
        return ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as LinearLayoutInfo
    }

    @Test
    public fun spacingDefaultsToZero() {
        assertEquals(0.0, parse("").spacing, 0.0)
    }

    @Test
    public fun spacingReadsWholeAndFractionalNumbers() {
        assertEquals(12.0, parse("\"spacing\": 12,").spacing, 0.0)
        assertEquals(2.5, parse("\"spacing\": 2.5,").spacing, 0.0)
    }

    @Test
    public fun negativeSpacingIsZero() {
        assertEquals(0.0, parse("\"spacing\": -4,").spacing, 0.0)
    }
}
