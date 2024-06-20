package com.urbanairship.featureflag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.jsonMapOf
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class EvaluationOptionsTest {

    @Test
    public fun testParse() {
        val json = jsonMapOf(
            "disallow_stale_value" to true,
            "ttl" to 1800000
        )

        val fromJson = EvaluationOptions.fromJson(json)
        assert(fromJson.disallowStaleValues == true)
        assert(fromJson.ttl == 1800000.toULong())
    }
}
