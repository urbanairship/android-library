/* Copyright Airship and Contributors */

package com.urbanairship.automation.deferred

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.AutomationAudience
import com.urbanairship.json.JsonException
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class DeferredScheduleResultTest {

    @Test
    public fun testParsesMissBehavior() {
        AutomationAudience.MissBehavior.entries.forEach { behavior ->
            val json = jsonMapOf(
                "audience_match" to false,
                "miss_behavior" to behavior.json
            ).toJsonValue()

            assertEquals(behavior, DeferredScheduleResult.fromJson(json).missBehavior)
        }
    }

    @Test
    public fun testMissBehaviorIsOptional() {
        val json = jsonMapOf("audience_match" to false).toJsonValue()

        assertNull(DeferredScheduleResult.fromJson(json).missBehavior)
    }

    @Test
    public fun testUnknownMissBehaviorThrows() {
        val json = jsonMapOf(
            "audience_match" to false,
            "miss_behavior" to "not a behavior"
        ).toJsonValue()

        assertThrows(JsonException::class.java) { DeferredScheduleResult.fromJson(json) }
    }
}
