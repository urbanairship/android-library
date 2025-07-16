/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.TestClock
import com.urbanairship.util.Clock
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class SubscriptionListEditorTest {

    private val clock = TestClock().also { it.currentTimeMillis = 0 }
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
            SubscriptionListMutation.newUnsubscribeMutation("foo", 0L),
            SubscriptionListMutation.newUnsubscribeMutation("bar", 0L),
            SubscriptionListMutation.newSubscribeMutation("baz", 0L)
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
            SubscriptionListMutation.newSubscribeMutation("one", 0L),
            SubscriptionListMutation.newSubscribeMutation("two", 0L),
            SubscriptionListMutation.newSubscribeMutation("three", 0L),
            SubscriptionListMutation.newUnsubscribeMutation("a", 0L),
            SubscriptionListMutation.newUnsubscribeMutation("b", 0L),
            SubscriptionListMutation.newUnsubscribeMutation("c", 0L)
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
            SubscriptionListMutation.newUnsubscribeMutation("foo", 0L),
            SubscriptionListMutation.newUnsubscribeMutation("bar", 0L),
            SubscriptionListMutation.newSubscribeMutation("baz", 0L)
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
