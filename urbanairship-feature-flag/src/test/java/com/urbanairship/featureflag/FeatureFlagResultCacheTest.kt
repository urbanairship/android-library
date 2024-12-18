package com.urbanairship.featureflag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.cache.AirshipCache
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class FeatureFlagResultCacheTest {

    private val airshipCache: AirshipCache = mockk(relaxed = true)
    private val resultCache = FeatureFlagResultCache(cache = airshipCache)

    @Test
    public fun testCacheFlag(): TestResult = runTest {
        val flag = FeatureFlag.createFlag(UUID.randomUUID().toString(), true, FeatureFlag.ReportingInfo(JsonMap.EMPTY_MAP))
        resultCache.cache(flag, 10.seconds)

        coVerify {
            airshipCache.store(flag, "FeatureFlagResultCache:${flag.name}", 10.seconds)
        }
    }

    @Test
    public fun testFlag(): TestResult = runTest {
        val flag = FeatureFlag.createFlag(UUID.randomUUID().toString(), true, FeatureFlag.ReportingInfo(JsonMap.EMPTY_MAP))
        val callbackSlot = slot<(JsonValue) -> FeatureFlag>()

        coEvery { airshipCache.getCached(key = "FeatureFlagResultCache:${flag.name}", converter = capture(callbackSlot)) } returns flag
        resultCache.flag(flag.name)

        val fromJSON = callbackSlot.captured.invoke(flag.toJsonValue())
        assertEquals(fromJSON, flag)
    }

    @Test
    public fun testDelete(): TestResult = runTest {
        resultCache.removeCachedFlag("some-flag")

        coVerify {
            airshipCache.delete("FeatureFlagResultCache:some-flag")
        }
    }
}
