package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.VariantAudience
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PreparedScheduleInfoTest {

    @Test
    public fun testUnrecognizedVariantOutcomeKeepsTheSchedule() {
        // AutomationStore discards the entire stored schedule when this throws, so an
        // outcome stamped by a newer SDK has to parse rather than reject.
        val info = PreparedScheduleInfo.fromJson(
            jsonMapOf(
                "schedule_id" to "test-schedule",
                "trigger_session_id" to "some-session",
                "variant_audience_result" to jsonMapOf("outcome" to "some_future_arm")
            ).toJsonValue()
        )

        assertEquals("test-schedule", info.scheduleId)
        assertEquals("some-session", info.triggerSessionId)
        assertEquals(
            VariantAudienceResult(VariantAudience.Outcome.Unknown("some_future_arm")),
            info.variantAudienceResult
        )
    }
}
