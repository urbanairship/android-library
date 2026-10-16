/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [LedgerConfig]'s canonicalization guarantee directly, independent of
 * [AutomationSchedule.fromJson]'s own coverage of it: [LedgerConfig.sharedIds]
 * must come out canonical (sorted, de-duplicated, or null) no matter which
 * public entry point built the instance, since two configs naming the same
 * set of groups have to compare equal for
 * [com.urbanairship.automation.AutomationSchedule] equality to be stable
 * across a re-fetch that merely reorders this array.
 */
@RunWith(AndroidJUnit4::class)
public class LedgerConfigTest {

    // `LedgerConfig(...)` below is the public companion factory
    // (`operator fun invoke`), not the class's private primary constructor —
    // there is no way to reach that constructor from outside `LedgerConfig`
    // itself, which is exactly the point: the factory is the only external
    // construction path, and it canonicalizes.

    @Test
    public fun testConstructionThroughTheFactoryCanonicalizes() {
        val config = LedgerConfig(sharedIds = listOf("group-2", "group-1", "group-1"))
        assertEquals(listOf("group-1", "group-2"), config.sharedIds)
    }

    @Test
    public fun testConstructionWithEmptyListNormalizesToNull() {
        assertNull(LedgerConfig(sharedIds = emptyList()).sharedIds)
    }

    @Test
    public fun testConstructionWithNoArgsIsNull() {
        assertNull(LedgerConfig().sharedIds)
    }

    /**
     * The whole point: two configs naming the same set of groups compare
     * equal regardless of which order — or which construction path — each one
     * arrived through.
     */
    @Test
    public fun testConfigsWithSameGroupsInDifferentOrderAreEqual() {
        val fromBackendOrderA = LedgerConfig(sharedIds = listOf("group-a", "shared"))
        val fromBackendOrderB = LedgerConfig(sharedIds = listOf("shared", "group-a"))

        assertEquals(fromBackendOrderA, fromBackendOrderB)
    }

    @Test
    public fun testFromJsonAgreesWithFactoryConstruction() {
        val decoded = LedgerConfig.fromJson(
            JsonValue.parseString(
                """{ "shared_ids": ["group-2", "group-1", "group-1"] }"""
            )
        )

        assertEquals(LedgerConfig(sharedIds = listOf("group-1", "group-2")), decoded)
    }

    /**
     * An explicit empty array and an absent key are both "no pooling" and
     * must normalize to the same (null) representation, or a schedule
     * re-served in the other shape would spuriously compare as changed.
     */
    @Test
    public fun testExplicitEmptyArrayAndAbsentKeyAreEquivalent() {
        val explicitlyEmpty = LedgerConfig.fromJson(
            JsonValue.parseString("""{ "shared_ids": [] }""")
        )
        val absent = LedgerConfig.fromJson(JsonValue.parseString("{}"))

        assertEquals(absent, explicitlyEmpty)
        assertNull(explicitlyEmpty.sharedIds)
    }
}
