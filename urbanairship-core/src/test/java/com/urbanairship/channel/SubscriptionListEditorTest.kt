/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.TestClock
import com.urbanairship.util.Clock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SubscriptionListEditorTest {

    private val clock = TestClock().also { it.currentTime = Instant.EPOCH }
    private var editor = TestSubscriptionListEditor(clock)

    @Test
    public fun testSubscribeUnsubscribeEmptyListIds() {
        editor
            .subscribe("")
            .subscribe("   ")
            .unsubscribe("")
            .unsubscribe("   ")
            .apply()

        assert(editor.collapsedMutations?.isEmpty() == true)
    }

    @Test
    public fun testCollapseMutations() {
        editor
            .subscribe("foo")
            .subscribe("bar")
            .unsubscribe("foo")
            .unsubscribe("bar")
            .subscribe("baz")
            .apply()

        val expected = listOf(
            SubscriptionListMutation.newUnsubscribeMutation("foo", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newUnsubscribeMutation("bar", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newSubscribeMutation("baz", Instant.ofEpochMilli(0))
        )

        assertEquals(expected, editor.collapsedMutations)
    }

    @Test
    public fun testSubscribeUnsubscribeLists() {
        val subscribes = setOf("one", "two", "three")
        val unsubscribes = setOf("a", "b", "c")

        editor
            .subscribe(subscribes)
            .unsubscribe(unsubscribes)
            .apply()

        val expected = listOf(
            SubscriptionListMutation.newSubscribeMutation("one", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newSubscribeMutation("two", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newSubscribeMutation("three", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newUnsubscribeMutation("a", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newUnsubscribeMutation("b", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newUnsubscribeMutation("c", Instant.ofEpochMilli(0))
        )

        assertEquals(expected, editor.collapsedMutations)
    }

    @Test
    public fun testMutate() {
        editor
            .mutate("foo", true)
            .mutate("bar", true)
            .mutate("foo", false)
            .mutate("bar", false)
            .mutate("baz", true)
            .apply()

        val expected = listOf(
            SubscriptionListMutation.newUnsubscribeMutation("foo", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newUnsubscribeMutation("bar", Instant.ofEpochMilli(0)),
            SubscriptionListMutation.newSubscribeMutation("baz", Instant.ofEpochMilli(0))
        )

        assertEquals(expected, editor.collapsedMutations)
    }

    private class TestSubscriptionListEditor(clock: Clock) : SubscriptionListEditor(clock) {

        var collapsedMutations: List<SubscriptionListMutation>? = null
            private set

        override fun onApply(collapsedMutations: List<SubscriptionListMutation>) {
            this.collapsedMutations = collapsedMutations
        }
    }
}
