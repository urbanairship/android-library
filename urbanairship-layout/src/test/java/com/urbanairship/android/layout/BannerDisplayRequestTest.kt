/* Copyright Airship and Contributors */

package com.urbanairship.android.layout

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerDisplayRequestTest {

    @Test
    public fun testEqualityIgnoresPriority() {
        // Deliberate: the banner view manager feeds display requests through
        // distinctUntilChanged, so a priority change alone must not re-emit (and re-display)
        // an otherwise identical request.
        val request = request(priority = 0)
        val reprioritized = request(priority = 100)

        assertEquals(request, reprioritized)
        assertEquals(request.hashCode(), reprioritized.hashCode())
    }

    @Test
    public fun testEqualityUsesViewInstanceIdAndExtras() {
        assertFalse(request() == request(viewInstanceId = "other"))
        assertFalse(request() == request(extras = jsonMapOf("foo" to "bar")))
    }

    private fun request(
        viewInstanceId: String = "instance",
        priority: Int = 0,
        extras: JsonMap = JsonMap.EMPTY_MAP
    ): BannerDisplayRequest = BannerDisplayRequest(
        viewInstanceId = viewInstanceId,
        priority = priority,
        extras = extras,
        layoutInfoProvider = { mockk() },
        displayArgsProvider = { mockk() }
    )
}
