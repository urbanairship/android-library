/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ScopedSubscriptionListEditorTest {

    private var result: List<ScopedSubscriptionListMutation>? = null
    private val clock = TestClock()
    private val editor = object : ScopedSubscriptionListEditor(clock) {
        override fun onApply(mutations: List<ScopedSubscriptionListMutation>) {
            result = mutations
        }
    }

    @Test
    public fun testSubscribe() {
        editor.subscribe("some list", Scope.SMS).apply()
        val expected = listOf(
            ScopedSubscriptionListMutation.newSubscribeMutation(
                "some list", Scope.SMS, clock.currentTimeMillis
            )
        )
        Assert.assertEquals(expected, result)
    }

    @Test
    public fun testUnsubscribe() {
        editor.unsubscribe("some list", Scope.APP).apply()
        val expected = listOf(
            ScopedSubscriptionListMutation.newUnsubscribeMutation(
                "some list", Scope.APP, clock.currentTimeMillis
            )
        )
        Assert.assertEquals(expected, result)
    }

    @Test
    public fun testMutateSingle() {
        editor
            .mutate("some list", setOf(Scope.APP), false)
            .mutate("some other list", setOf(Scope.WEB), true)
            .apply()

        val expected = listOf(
            ScopedSubscriptionListMutation.newUnsubscribeMutation(
                "some list", Scope.APP, clock.currentTimeMillis
            ), ScopedSubscriptionListMutation.newSubscribeMutation(
                "some other list", Scope.WEB, clock.currentTimeMillis
            )
        )

        Assert.assertEquals(expected, result)
    }

    @Test
    public fun testMutateMultiple() {
        editor
            .mutate("some list", setOf(Scope.APP, Scope.SMS), false)
            .mutate("some other list", setOf(Scope.WEB, Scope.EMAIL), true)
            .apply()

        val expected = listOf(
            ScopedSubscriptionListMutation.newUnsubscribeMutation(
                "some list", Scope.APP, clock.currentTimeMillis
            ), ScopedSubscriptionListMutation.newUnsubscribeMutation(
                "some list", Scope.SMS, clock.currentTimeMillis
            ), ScopedSubscriptionListMutation.newSubscribeMutation(
                "some other list", Scope.WEB, clock.currentTimeMillis
            ), ScopedSubscriptionListMutation.newSubscribeMutation(
                "some other list", Scope.EMAIL, clock.currentTimeMillis
            )
        )

        Assert.assertEquals(expected, result)
    }

    @Test
    public fun testCollapse() {
        editor
            .mutate("some list", setOf(Scope.APP), false)
            .subscribe("some list", Scope.APP)
            .apply()

        val expected = listOf(
            ScopedSubscriptionListMutation.newSubscribeMutation(
                "some list", Scope.APP, clock.currentTimeMillis
            )
        )
        Assert.assertEquals(expected, result)
    }
}
