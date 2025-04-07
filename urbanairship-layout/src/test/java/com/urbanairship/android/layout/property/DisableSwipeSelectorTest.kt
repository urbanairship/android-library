/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DisableSwipeSelectorTest {

    @Test
    public fun testParsing() {
        val json = """
            {
              "when_state_matches": {
                "scope": [
                  "test"
                ],
                "value": {
                  "equals": [
                    "is-complete"
                  ]
                }
              },
              "directions": {
                "type": "horizontal"
              }
            }
        """.trimIndent()

        val selector = DisableSwipeSelector.fromJson(JsonValue.parseString(json))
        assertNotNull(selector.predicate)
        assertEquals(selector.direction, DisableSwipeSelector.Direction.HORIZONTAL)
    }
}
