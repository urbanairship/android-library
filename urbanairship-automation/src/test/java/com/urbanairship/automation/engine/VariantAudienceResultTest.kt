package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.VariantAudience
import com.urbanairship.json.jsonMapOf
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class VariantAudienceResultTest {

    @Test
    public fun testJsonRoundTrip() {
        val result = VariantAudienceResult(
            outcome = VariantAudience.Outcome.VARIANT_MISS,
            reportingContext = jsonMapOf("foo" to "bar")
        )
        assertEquals(result, VariantAudienceResult.fromJson(result.toJsonValue()))
    }

    @Test
    public fun testJsonRoundTripWithoutReportingContext() {
        val result = VariantAudienceResult(outcome = VariantAudience.Outcome.HOLDOUT)
        val restored = VariantAudienceResult.fromJson(result.toJsonValue())
        assertEquals(result, restored)
        assertNull(restored.reportingContext)
    }
}
